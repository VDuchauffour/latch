package app.latch

import android.graphics.Bitmap
import android.os.Process
import android.os.UserManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.latch.data.LatchSettings
import app.latch.data.TileBackground
import app.latch.domain.Category
import app.latch.ui.LatchViewModel
import java.io.File
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Captures the README screenshots. Skipped unless run with `-e screenshots true` (see `just screenshots`).
 * Drawers are filled with the Google apps of an emulator image with Google APIs; missing ones are skipped.
 * PNGs land in the app's external files dir under `screenshots/`.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {
    @get:Rule val rule = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as LatchApp
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before fun setUp() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("screenshots") == "true")
        val serial = app.getSystemService(UserManager::class.java).getSerialNumberForUser(Process.myUserHandle())
        val installed = runBlocking { app.apps.apps.filterNotNull().first() }.map { it.key }.filter { it.userSerial == serial }
        fun keys(vararg packages: String) = packages.mapNotNull { pkg -> installed.firstOrNull { it.packageName == pkg } }
        runBlocking {
            app.categories.update {
                listOf(
                    Category("productivity", "Productivity", 0, keys(GMAIL, CALENDAR, DRIVE, FILES, CLOCK, MAPS)),
                    Category("communication", "Communication", 1, keys(MESSAGES, PHONE, CONTACTS, CHROME)),
                    Category("media", "Media", 2, keys(PHOTOS, YOUTUBE, YT_MUSIC, CAMERA))
                )
            }
            app.settings.setAppsPerDrawer(LatchSettings.DEFAULT_APPS_PER_DRAWER)
            app.settings.setGrayIcons(true)
            app.settings.setTileBackground(TileBackground.DEFAULT)
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("category:Media")
    }

    @After fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
    }

    @Test fun captureAll() {
        shot("home")

        drawer("Productivity")
        drawer("Communication")
        drawer("Media")
        rule.onNodeWithTag("category:Media").performClick()

        rule.onNodeWithTag("searchIcon").performSemanticsAction(SemanticsActions.OnLongClick)
        waitForTag("quickMenu")
        shot("quick-menu")
        rule.onNodeWithText("Latch settings").performClick()
        waitForTag("settings")
        rule.onNodeWithTag("settingsSection:Appearance").performClick()
        waitForTag("accentColor")
        shot("settings")

        relaunch()
        rule.onNodeWithTag("category:Productivity").performClick()
        waitForTag("categoryApps:Productivity")
        rule.onAllNodes(hasTestTagPrefix("tile:"))[1].performSemanticsAction(SemanticsActions.OnLongClick)
        waitForTag("contextMenu")
        shot("app-menu")

        relaunch()
        rule.onNodeWithTag("home").performTouchInput { swipeUp() }
        waitForTag("drawerList")
        shot("app-drawer")

        relaunch()
        // Set the query without focusing the field: the keyboard's suggestion strip can show the
        // (host-synced) clipboard, so keep it out of the shot.
        scenario.onActivity { ViewModelProvider(it, LatchViewModel.Factory)[LatchViewModel::class.java].setQuery("c") }
        waitForTag("searchResults")
        shot("search")
    }

    /** Opens [name] (closing the previously open drawer) and captures it. */
    private fun drawer(name: String) {
        rule.onNodeWithTag("category:$name").performClick()
        waitForTag("categoryApps:$name")
        shot("drawer-${name.lowercase()}")
    }

    private fun hasTestTagPrefix(prefix: String) = SemanticsMatcher("testTag starts with $prefix") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }

    private fun waitForTag(tag: String) = rule.waitUntil(10_000) {
        rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }

    /** Starts again from a collapsed Home, with no menu, sheet, or search open. */
    private fun relaunch() {
        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("category:Media")
    }

    /** Lets animations and async icon loads settle, then saves the whole screen (status bar included). */
    private fun shot(name: String) {
        rule.waitForIdle()
        Thread.sleep(SETTLE_MS)
        val full = instrumentation.uiAutomation.takeScreenshot()
        // Half resolution keeps each PNG small enough for the repo (the README shows them 200px wide).
        val bitmap = Bitmap.createScaledBitmap(full, full.width / 2, full.height / 2, true)
        val dir = File(app.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val GMAIL = "com.google.android.gm"
        const val CALENDAR = "com.google.android.calendar"
        const val DRIVE = "com.google.android.apps.docs"
        const val FILES = "com.google.android.documentsui"
        const val CLOCK = "com.google.android.deskclock"
        const val MAPS = "com.google.android.apps.maps"
        const val MESSAGES = "com.google.android.apps.messaging"
        const val PHONE = "com.google.android.dialer"
        const val CONTACTS = "com.google.android.contacts"
        const val CHROME = "com.android.chrome"
        const val PHOTOS = "com.google.android.apps.photos"
        const val YOUTUBE = "com.google.android.youtube"
        const val YT_MUSIC = "com.google.android.apps.youtube.music"
        const val CAMERA = "com.android.camera2"
        const val SETTLE_MS = 1_200L
    }
}
