package app.latch.ui

import android.graphics.Rect
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.latch.domain.AppEntry
import app.latch.ui.theme.LatchColors
import app.latch.ui.theme.LatchType
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

enum class DrawerValue { Closed, Open }

/** Pending dialog on Home. */
private sealed interface HomeDialog {
    data object Add : HomeDialog
    data class Rename(val id: String, val name: String) : HomeDialog
    data class Delete(val id: String, val name: String) : HomeDialog
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LatchScreen(vm: LatchViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    StatelessLatchScreen(state, vm)
}

/** Root: Home, the swipe-up drawer, the drag shadow and dialogs. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StatelessLatchScreen(state: UiState, vm: LatchViewModel) {
    val density = LocalDensity.current
    val dragState = remember { DragState() }
    val drawerList = rememberLazyListState()
    var dialog by remember { mutableStateOf<HomeDialog?>(null) }
    val quickMenu = remember { QuickMenuState() }
    val focusManager = LocalFocusManager.current

    val sheet = remember {
        AnchoredDraggableState(
            initialValue = if (state.drawerOpen) DrawerValue.Open else DrawerValue.Closed,
            positionalThreshold = { distance -> distance * 0.2f },
            velocityThreshold = { with(density) { 500.dp.toPx() } },
            snapAnimationSpec = spring(dampingRatio = 0.9f, stiffness = 500f),
            decayAnimationSpec = exponentialDecay()
        )
    }

    // ViewModel → sheet (Home button, Back, drag start).
    LaunchedEffect(state.drawerOpen) {
        val target = if (state.drawerOpen) DrawerValue.Open else DrawerValue.Closed
        if (sheet.targetValue != target || sheet.currentValue != target) sheet.animateTo(target)
    }
    // Sheet → ViewModel (user swipes).
    LaunchedEffect(sheet) {
        snapshotFlow { sheet.settledValue }.drop(1).distinctUntilChanged().collect { value ->
            vm.setDrawerOpen(value == DrawerValue.Open)
            if (value == DrawerValue.Closed) drawerList.scrollToItem(0)
        }
    }

    val drawerVisible = state.drawerOpen || sheet.currentValue == DrawerValue.Open || sheet.targetValue == DrawerValue.Open
    val searching = state.query.isNotEmpty()
    var searchFocused by remember { mutableStateOf(false) }
    fun endSearch() {
        focusManager.clearFocus()
        vm.clearSearch()
    }
    LaunchedEffect(drawerVisible) { if (drawerVisible) endSearch() }
    LifecycleResumeEffect(Unit) {
        focusManager.clearFocus()
        onPauseOrDispose {}
    }
    // Lowest priority: never let Back finish the home activity.
    BackHandler(enabled = true) {}
    BackHandler(enabled = drawerVisible || searching || state.expandedCategoryId != null) {
        when {
            drawerVisible -> vm.setDrawerOpen(false)
            searching -> endSearch()
            else -> vm.collapseAll()
        }
    }

    val currentState by rememberUpdatedState(state)
    val tileActions = remember(vm, dragState) {
        object : TileActions {
            override fun launch(entry: AppEntry, bounds: Rect?, options: Bundle?) {
                vm.launch(entry.key, bounds, options)
                if (currentState.query.isNotEmpty()) {
                    focusManager.clearFocus()
                    vm.clearSearch()
                }
            }

            override fun menu(entry: AppEntry, context: TileContext): List<MenuAction> = buildList {
                val categoryId = context.categoryId
                if (categoryId != null) add(MenuAction("Remove from category") { vm.removeFromCategory(categoryId, entry.key) })
                add(MenuAction("App info") { vm.openAppInfo(entry.key, null) })
            }

            override fun dragStart(entry: AppEntry, context: TileContext, rootPosition: Offset) {
                dragState.start(entry, context.categoryId, rootPosition)
                // Reveal Home so a category can be targeted.
                if (currentState.drawerOpen || sheet.currentValue == DrawerValue.Open) vm.setDrawerOpen(false)
            }

            override fun dragMove(rootPosition: Offset) = dragState.moveTo(rootPosition)

            override fun dragEnd() {
                val drop = dragState.end() ?: return
                vm.drop(drop.app.entry.key, drop.app.sourceCategoryId, drop.targetCategoryId, drop.beforeIndex)
            }

            override fun dragCancel() = dragState.cancel()
        }
    }
    val quickActions = remember(vm) {
        listOf(
            QuickAction("Add drawer", QuickIcon.AddDrawer) { dialog = HomeDialog.Add },
            QuickAction("Latch settings", QuickIcon.Settings) { vm.setSettingsOpen(true) }
        )
    }
    val quickGesture = remember(vm, quickMenu, quickActions) {
        object : QuickMenuGesture {
            override fun open() {
                focusManager.clearFocus()
                vm.clearSearch()
                vm.collapseAll()
                quickMenu.open()
            }
            override fun move(rootPosition: Offset) = quickMenu.pointerAt(rootPosition)
            override fun release() {
                quickActions.getOrNull(quickMenu.release())?.onClick?.invoke()
            }
        }
    }
    LaunchedEffect(drawerVisible) { if (drawerVisible) quickMenu.close() }
    BackHandler(enabled = quickMenu.isOpen) { quickMenu.close() }
    BackHandler(enabled = state.settingsOpen) { vm.setSettingsOpen(false) }
    val settingsActions = remember(vm) {
        object : SettingsActions {
            override fun setAppsPerDrawer(count: Int) = vm.setAppsPerDrawer(count)
            override fun setGrayIcons(enabled: Boolean) = vm.setGrayIcons(enabled)
            override fun close() = vm.setSettingsOpen(false)
        }
    }
    val homeActions = remember(vm) {
        object : HomeActions {
            override fun toggle(categoryId: String) {
                focusManager.clearFocus()
                vm.toggleCategory(categoryId)
            }
            override fun collapseAll() {
                focusManager.clearFocus()
                vm.collapseAll()
            }
            override fun requestRename(category: CategoryUi) {
                dialog = HomeDialog.Rename(category.id, category.name)
            }
            override fun requestDelete(category: CategoryUi) {
                dialog = HomeDialog.Delete(category.id, category.name)
            }
            override fun moveCategory(categoryId: String, toIndex: Int) = vm.moveCategory(categoryId, toIndex)
        }
    }

    CompositionLocalProvider(LocalGrayIcons provides state.settings.grayIcons) {
        BoxWithConstraints(Modifier.fillMaxSize().testTag("root")) {
            val heightPx = constraints.maxHeight.toFloat()
            LaunchedEffect(heightPx) {
                sheet.updateAnchors(
                    DraggableAnchors {
                        DrawerValue.Closed at heightPx
                        DrawerValue.Open at 0f
                    }
                )
            }
            /** 0 = drawer closed, 1 = fully open. */
            fun progress(): Float {
                val offset = sheet.offset
                if (offset.isNaN() || heightPx <= 0f) return if (sheet.currentValue == DrawerValue.Open) 1f else 0f
                return (1f - offset / heightPx).coerceIn(0f, 1f)
            }

            val pullDownThreshold = with(density) { 40.dp.toPx() }
            val homeScroll = remember(sheet, pullDownThreshold) {
                homeNestedScroll(sheet, pullDownThreshold) {
                    if (!dragState.isDragging) vm.expandNotifications()
                }
            }
            val drawerScroll = remember(sheet) { drawerNestedScroll(sheet) }
            // Home's drawers and search bar fade out while Latch settings is open.
            val homeAlpha by animateFloatAsState(
                targetValue = if (state.settingsOpen) 0f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "homeAlpha"
            )

            // Readability scrim over bright wallpapers.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.35f))))
            )

            // Home layer: swipe up anywhere to open the drawer.
            Box(
                Modifier
                    .fillMaxSize()
                    .anchoredDraggable(sheet, Orientation.Vertical, enabled = !dragState.isDragging)
                    .nestedScroll(homeScroll)
                    .graphicsLayer {
                        val p = progress()
                        alpha = (1f - p) * homeAlpha
                        translationY = -p * 80.dp.toPx()
                    }
            ) {
                AnimatedVisibility(
                    visible = !(searchFocused || searching) || dragState.isDragging,
                    enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)),
                    exit = fadeOut(spring(stiffness = Spring.StiffnessMedium))
                ) {
                    HomeScreen(
                        categories = state.categories,
                        expandedCategoryId = state.expandedCategoryId,
                        dragState = dragState,
                        tileActions = tileActions,
                        homeActions = homeActions,
                        appsPerDrawer = state.settings.appsPerDrawer,
                        modifier = Modifier.padding(bottom = 72.dp)
                    )
                }
                HomeSearch(
                    query = state.query,
                    results = state.searchResults,
                    onQueryChange = vm::setQuery,
                    onSubmit = {
                        state.searchResults.firstOrNull()?.let { tileActions.launch(it, null, null) }
                    },
                    onFocusChange = { focused ->
                        searchFocused = focused
                        if (focused) vm.collapseAll()
                    },
                    onDismiss = ::endSearch,
                    quickMenu = quickGesture,
                    tileActions = tileActions,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(bottom = 12.dp)
                )
            }

            // Drawer layer.
            Box(
                Modifier
                    .fillMaxSize()
                    .offset {
                        val offset = sheet.offset.takeUnless { it.isNaN() }
                            ?: if (state.drawerOpen) 0f else heightPx
                        IntOffset(0, offset.roundToInt())
                    }
                    .graphicsLayer { alpha = (progress() * 1.4f).coerceAtMost(1f) }
                    .background(LatchColors.OverlayScrim)
                    .anchoredDraggable(sheet, Orientation.Vertical, enabled = !dragState.isDragging)
                    .nestedScroll(drawerScroll)
            ) {
                AppDrawer(
                    sections = state.drawerSections,
                    actions = tileActions,
                    listState = drawerList
                )
            }

            QuickMenu(quickMenu, quickActions)

            AnimatedVisibility(
                visible = state.settingsOpen,
                enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)),
                exit = fadeOut(spring(stiffness = Spring.StiffnessMedium))
            ) {
                SettingsScreen(state.settings, settingsActions)
            }

            InsertionLine(dragState)
            DragShadow(dragState)
        }
    }

    when (val d = dialog) {
        null -> Unit
        HomeDialog.Add -> NameDialog(
            title = "New category",
            initial = "",
            confirm = "Add",
            onConfirm = {
                vm.createCategory(it)
                dialog = null
            },
            onDismiss = { dialog = null }
        )
        is HomeDialog.Rename -> NameDialog(
            title = "Rename category",
            initial = d.name,
            confirm = "Rename",
            onConfirm = {
                vm.renameCategory(d.id, it)
                dialog = null
            },
            onDismiss = { dialog = null }
        )
        is HomeDialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete “${d.name}”?") },
            text = { Text("The apps stay installed; only this category is removed.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteCategory(d.id)
                    dialog = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } }
        )
    }
}

/** Vertical white line between the two tiles where a dragged app would be dropped in an open category. */
@Composable
private fun InsertionLine(dragState: DragState) {
    val at = dragState.insertion ?: return
    val tile = at.left ?: at.right ?: return
    Canvas(Modifier.fillMaxSize().testTag("insertionLine")) {
        val gap = 8.dp.toPx() // Matches the row's tile spacing.
        val x = when {
            at.left != null && at.right != null -> (at.left.right + at.right.left) / 2
            at.left != null -> at.left.right + gap / 2
            else -> at.right!!.left - gap / 2
        }
        val width = 3.dp.toPx()
        drawLine(
            color = Color.White,
            start = Offset(x, tile.top + 4.dp.toPx()),
            end = Offset(x, tile.bottom - 4.dp.toPx()),
            strokeWidth = width,
            cap = StrokeCap.Round
        )
    }
}

/** Icon following the finger while an app is dragged. */
@Composable
private fun DragShadow(dragState: DragState) {
    val dragged = dragState.dragged ?: return
    val density = LocalDensity.current
    val sizePx = with(density) { 84.dp.toPx() }
    Column(
        modifier = Modifier
            .offset {
                IntOffset(
                    (dragState.position.x - sizePx / 2).roundToInt(),
                    (dragState.position.y - sizePx * 0.9f).roundToInt()
                )
            }
            .width(84.dp)
            .graphicsLayer {
                scaleX = 1.1f
                scaleY = 1.1f
                alpha = 0.92f
            }
            .testTag("dragShadow"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIcon(dragged.entry.key, Modifier.size(52.dp))
        Text(
            text = dragged.entry.label,
            style = LatchType.TileLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.focusRequester(focus).testTag("nameField")
            )
            // Inside the dialog's own composition so the field is attached; never crash over focus.
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Home's own vertical scroll hands leftover drags to the sheet: swiping up past the end of the
 * category list opens the drawer.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun homeNestedScroll(sheet: AnchoredDraggableState<DrawerValue>, pullDownThreshold: Float, onPullDown: () -> Unit) =
    object : NestedScrollConnection {
        private var pulled = 0f
        private var fired = false

        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // While the sheet is moving, keep feeding it instead of scrolling Home.
            if (source == NestedScrollSource.UserInput && sheet.currentValue == DrawerValue.Closed && isMoving(sheet)) {
                return Offset(0f, sheet.dispatchRawDelta(available.y))
            }
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput) return Offset.Zero
            val used = sheet.dispatchRawDelta(available.y)
            val leftover = available.y - used
            if (leftover > 0f && sheet.currentValue == DrawerValue.Closed && !isMoving(sheet)) {
                pulled += leftover
                if (!fired && pulled >= pullDownThreshold) {
                    fired = true
                    onPullDown()
                }
            } else if (available.y < 0f) {
                pulled = 0f
            }
            return Offset(0f, used)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            pulled = 0f
            fired = false
            if (isMoving(sheet)) {
                sheet.settle(available.y)
                return available
            }
            return Velocity.Zero
        }
    }

/**
 * Standard bottom-sheet nested scrolling for the drawer list: dragging down while the list is at
 * the top closes the sheet instead of overscrolling.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun drawerNestedScroll(sheet: AnchoredDraggableState<DrawerValue>) = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val delta = available.y
        return if (delta < 0 && source == NestedScrollSource.UserInput && isMoving(sheet)) {
            Offset(0f, sheet.dispatchRawDelta(delta))
        } else {
            Offset.Zero
        }
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (source == NestedScrollSource.UserInput) Offset(0f, sheet.dispatchRawDelta(available.y)) else Offset.Zero

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (isMoving(sheet)) {
            sheet.settle(available.y)
            return available
        }
        return Velocity.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        sheet.settle(available.y)
        return available
    }
}

/** True when the sheet sits between its anchors. */
@OptIn(ExperimentalFoundationApi::class)
private fun isMoving(sheet: AnchoredDraggableState<DrawerValue>): Boolean {
    val offset = sheet.offset
    if (offset.isNaN()) return false
    val min = sheet.anchors.minAnchor()
    val max = sheet.anchors.maxAnchor()
    return offset > min + 0.5f && offset < max - 0.5f
}
