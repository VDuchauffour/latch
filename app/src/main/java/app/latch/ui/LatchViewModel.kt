package app.latch.ui

import android.graphics.Rect
import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.latch.LatchApp
import app.latch.data.AppLauncher
import app.latch.data.AppRepository
import app.latch.data.CategoryStore
import app.latch.data.CategoryStore.Companion.newId
import app.latch.domain.AlphaSections
import app.latch.domain.AppEntry
import app.latch.domain.AppKey
import app.latch.domain.AppSearch
import app.latch.domain.Category
import app.latch.domain.CategoryOps
import app.latch.domain.Section
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoryUi(
    val id: String,
    val name: String,
    /** Installed apps only; uninstalled ones are filtered out. */
    val apps: List<AppEntry>,
)

data class UiState(
    val categories: List<CategoryUi> = emptyList(),
    val drawerSections: List<Section> = emptyList(),
    val expandedCategoryId: String? = null,
    val drawerOpen: Boolean = false,
    val loaded: Boolean = false,
    val query: String = "",
    val searchResults: List<AppEntry> = emptyList(),
)

class LatchViewModel(
    private val repository: AppRepository,
    private val store: CategoryStore,
    private val launcher: AppLauncher,
    private val saved: SavedStateHandle,
) : ViewModel() {

    private val expanded = saved.getStateFlow<String?>(KEY_EXPANDED, null)
    private val drawerOpen = saved.getStateFlow(KEY_DRAWER, false)

    private val sections = repository.apps
        .map { apps -> apps?.let { AlphaSections.build(it, collator()) } }
        .flowOn(Dispatchers.Default)

    private val query = MutableStateFlow("")

    private val searchResults = combine(sections, query) { sections, q ->
        AppSearch.match(sections.orEmpty().flatMap { it.apps }, q)
    }.flowOn(Dispatchers.Default)

    private val content = combine(repository.apps, store.categories, sections) { apps, categories, sections ->
        val installed = apps.orEmpty().associateBy { it.key }
        Triple(
            CategoryOps.resolve(categories, installed).map { (c, entries) -> CategoryUi(c.id, c.name, entries) },
            sections.orEmpty(),
            apps != null,
        )
    }.flowOn(Dispatchers.Default)

    val uiState: StateFlow<UiState> = combine(
        content, expanded, drawerOpen, query, searchResults,
    ) { (cats, sections, loaded), exp, drawer, q, results ->
        UiState(
            categories = cats,
            drawerSections = sections,
            expandedCategoryId = exp?.takeIf { id -> cats.any { it.id == id } },
            drawerOpen = drawer,
            loaded = loaded,
            query = q,
            searchResults = if (q.isBlank()) emptyList() else results,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    init {
        viewModelScope.launch {
            store.seedIfEmpty()
            // Start-up prune: drop apps uninstalled while we weren't listening, but only when certain.
            repository.apps.filterNotNull().first()
            val installed = repository.apps.value.orEmpty().mapTo(HashSet()) { it.key }
            store.update { cats ->
                CategoryOps.prune(cats) { it !in installed && repository.isDefinitelyGone(it) }
            }
        }
        viewModelScope.launch {
            // Live prune: after a package changes, drop its keys that no longer exist.
            repository.packageChanges.collect { change ->
                val installed = repository.apps.value.orEmpty().mapTo(HashSet()) { it.key }
                store.update { cats ->
                    CategoryOps.prune(cats) {
                        it.packageName == change.packageName && it.userSerial == change.userSerial && it !in installed
                    }
                }
            }
        }
    }

    // --- Navigation state ---

    fun toggleCategory(id: String) {
        saved[KEY_EXPANDED] = if (expanded.value == id) null else id
    }

    fun collapseAll() {
        saved[KEY_EXPANDED] = null
    }

    fun setDrawerOpen(open: Boolean) {
        saved[KEY_DRAWER] = open
    }

    fun setQuery(text: String) {
        query.value = text
    }

    fun clearSearch() = setQuery("")

    /** Home pressed while already on Home. */
    fun resetHome() {
        clearSearch()
        collapseAll()
        setDrawerOpen(false)
    }

    // --- Apps ---

    fun launch(key: AppKey, bounds: Rect?, options: Bundle?) = launcher.launch(key, bounds, options)

    fun openAppInfo(key: AppKey, bounds: Rect?) = launcher.openAppInfo(key, bounds)

    fun expandNotifications() = launcher.expandNotifications()

    // --- Categories ---

    fun createCategory(name: String) = edit { CategoryOps.create(it, newId(), name) }

    fun renameCategory(id: String, name: String) = edit { CategoryOps.rename(it, id, name) }

    fun deleteCategory(id: String) {
        if (expanded.value == id) collapseAll()
        edit { CategoryOps.delete(it, id) }
    }

    fun moveCategory(id: String, toIndex: Int) = edit { CategoryOps.reorder(it, id, toIndex) }

    fun addToCategory(id: String, app: AppKey) = edit { CategoryOps.addApp(it, id, app) }

    fun removeFromCategory(id: String, app: AppKey) = edit { CategoryOps.removeApp(it, id, app) }

    /** Drop handler: from the drawer = add, from another category = move. */
    fun drop(app: AppKey, sourceCategoryId: String?, targetCategoryId: String) = edit {
        if (sourceCategoryId == null) CategoryOps.addApp(it, targetCategoryId, app)
        else CategoryOps.moveApp(it, sourceCategoryId, targetCategoryId, app)
    }

    private fun edit(transform: (List<Category>) -> List<Category>) {
        viewModelScope.launch { store.update(transform) }
    }

    companion object {
        private const val KEY_EXPANDED = "expanded"
        private const val KEY_DRAWER = "drawer"

        private fun collator(): Collator =
            Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LatchApp
                LatchViewModel(app.apps, app.categories, app.launcher, createSavedStateHandle())
            }
        }
    }
}
