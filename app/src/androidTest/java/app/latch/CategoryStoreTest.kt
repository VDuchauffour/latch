package app.latch

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.latch.data.CategoryStore
import app.latch.domain.AppKey
import app.latch.domain.CategoryOps
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryStoreTest {
    private lateinit var file: File
    private val scopes = mutableListOf<CoroutineScope>()

    private fun newStore(): CategoryStore {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }
        return CategoryStore(PreferenceDataStoreFactory.create(scope = scope) { file })
    }

    @Before fun setUp() {
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        file = File(dir, "test-${System.nanoTime()}.preferences_pb")
    }

    // DataStore releases its file only once the scope's job has *completed*; a bare cancel()
    // returns before that, so a new DataStore on the same file would race the old one.
    private suspend fun closeStores() {
        scopes.forEach { it.coroutineContext.job.cancelAndJoin() }
        scopes.clear()
    }

    @After fun tearDown() = runBlocking {
        closeStores()
        file.delete()
        Unit
    }

    @Test fun seedsOnceAndPersistsAcrossInstances() = runBlocking {
        val store = newStore()
        store.seedIfEmpty()
        val seeded = store.categories.first()
        assertEquals(CategoryOps.DEFAULT_NAMES, seeded.map { it.name })

        val app = AppKey("com.example", "com.example.Main", 0)
        store.update { CategoryOps.addApp(it, seeded[0].id, app) }
        store.update { CategoryOps.rename(it, seeded[1].id, "Music") }

        // A fresh DataStore on the same file (as after a process restart) reads the same data.
        closeStores()
        val reopened = newStore()
        reopened.seedIfEmpty() // must not overwrite
        val restored = reopened.categories.first()
        assertEquals(listOf(app), restored[0].apps)
        assertEquals("Music", restored[1].name)
    }
}
