package app.latch

import android.graphics.Bitmap
import android.os.Process
import android.os.UserManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.latch.data.LatchSettings
import app.latch.data.TileBackground
import app.latch.domain.AppKey
import app.latch.domain.Category
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Captures the README screenshots. Skipped unless run with `-e screenshots true` (see `just screenshots`),
 * and needs the placeholder apps from `screenshots/fakeapps` installed. PNGs land in the app's external
 * files dir under `screenshots/`.
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
        fun keys(vararg names: String) = names.map { AppKey(FAKE_PACKAGE, "$FAKE_PACKAGE.$it", serial) }
        runBlocking {
            app.categories.update {
                listOf(
                    Category("productivity", "Productivity", 0, keys("Inbox", "Agenda", "Notes", "Tasks", "Docs", "Meet")),
                    Category("social", "Social", 1, keys("Chatter", "Forum", "Snapshot", "Friends", "Tunes", "Clips"))
                )
            }
            app.settings.setAppsPerDrawer(LatchSettings.DEFAULT_APPS_PER_DRAWER)
            app.settings.setGrayIcons(true)
            app.settings.setTileBackground(TileBackground.DEFAULT)
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("category:Social")
    }

    @After fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
    }

    @Test fun captureAll() {
        shot("home")

        rule.onNodeWithTag("category:Productivity").performClick()
        waitForTag("tile:Meet")
        shot("drawer-productivity")

        rule.onNodeWithTag("category:Social").performClick()
        waitForTag("tile:Clips")
        shot("drawer-social")
        rule.onNodeWithTag("category:Social").performClick()

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
        waitForTag("tile:Notes")
        rule.onNodeWithTag("tile:Notes").performSemanticsAction(SemanticsActions.OnLongClick)
        waitForTag("contextMenu")
        shot("app-menu")

        relaunch()
        rule.onNodeWithTag("home").performTouchInput { swipeUp() }
        waitForTag("drawerList")
        shot("app-drawer")

        relaunch()
        rule.onNodeWithTag("searchField").performTextInput("f")
        waitForTag("searchResults")
        shot("search")
    }

    private fun waitForTag(tag: String) = rule.waitUntil(10_000) {
        rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }

    /** Starts again from a collapsed Home, with no menu, sheet, or search open. */
    private fun relaunch() {
        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForTag("category:Social")
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
        const val FAKE_PACKAGE = "app.latch.fakeapps"
        const val SETTLE_MS = 1_200L
    }
}
