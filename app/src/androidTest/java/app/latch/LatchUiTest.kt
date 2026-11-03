package app.latch

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LatchUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun waitForTag(tag: String) = rule.waitUntil(5_000) {
        rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }

    private val isCategory = SemanticsMatcher("category") { n ->
        n.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("category:") == true
    }

    /** Home has no add entry; new categories come from the quick menu's "Add drawer". */
    private fun openAddDialog() {
        rule.onNodeWithTag("searchIcon").performSemanticsAction(SemanticsActions.OnLongClick)
        rule.onNodeWithText("Add drawer").performClick()
    }

    /** Home starts with no categories, so create one through the UI when needed. */
    private fun firstCategoryTag(): String {
        waitForTag("searchIcon")
        rule.waitForIdle()
        if (rule.onAllNodes(isCategory).fetchSemanticsNodes().isEmpty()) {
            openAddDialog()
            rule.onNode(hasTestTag("nameField")).performTextInput("Test")
            rule.onNodeWithText("Add").performClick()
        }
        rule.waitUntil(5_000) { rule.onAllNodes(isCategory).fetchSemanticsNodes().isNotEmpty() }
        return rule.onAllNodes(isCategory).fetchSemanticsNodes().first().config[SemanticsProperties.TestTag]
    }

    @Test fun categoryExpandsAndCollapses() {
        val tag = firstCategoryTag()
        rule.onNodeWithTag(tag).performClick()
        rule.waitUntil(3_000) {
            rule.onAllNodesWithTag("categoryApps:${tag.removePrefix("category:")}").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodes(
                    SemanticsMatcher("hint") { n ->
                        n.config.getOrNull(SemanticsProperties.Text)
                            ?.any { it.text.startsWith("Long-press an app") } == true
                    }
                ).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithTag("categoryApps:${tag.removePrefix("category:")}").fetchSemanticsNodes().isEmpty())
    }

    @Test fun drawerOpensAndCloses() {
        firstCategoryTag()
        rule.onNodeWithTag("home").performTouchInput { swipeUp() }
        rule.waitForIdle()
        rule.onNodeWithTag("drawerList").assertIsDisplayed()
        rule.waitUntil(3_000) { rule.activity.window.decorView.isShown }
        rule.onNodeWithTag("drawerList").performTouchInput { swipeDown() }
        rule.waitForIdle()
        firstCategoryTag()
        rule.onNodeWithTag(firstCategoryTag()).assertIsDisplayed()
    }

    @Test fun drawerTileContextMenuHasAppInfo() {
        firstCategoryTag()
        rule.onNodeWithTag("home").performTouchInput { swipeUp() }
        rule.waitForIdle()
        rule.waitUntil(5_000) {
            rule.onAllNodes(
                SemanticsMatcher("tile") { n ->
                    n.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("tile:") == true
                }
            ).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodes(
            SemanticsMatcher("tile") { n ->
                n.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("tile:") == true
            }
        ).onFirst().performSemanticsAction(SemanticsActions.OnLongClick)
        rule.onNodeWithText("App info").assertIsDisplayed()
    }

    @Test fun longPressSearchIconOpensQuickMenu() {
        waitForTag("searchIcon")
        rule.onNodeWithTag("searchIcon").performSemanticsAction(SemanticsActions.OnLongClick)
        rule.onNodeWithTag("quickMenu").assertIsDisplayed()
        listOf("Add drawer", "Latch settings").forEach {
            rule.onNodeWithText(it).assertIsDisplayed()
        }
        rule.onNodeWithText("Add drawer").performClick()
        rule.onNodeWithText("New category").assertIsDisplayed()
    }

    @Test fun latchSettingsOpensWithAppearanceSection() {
        waitForTag("searchIcon")
        rule.onNodeWithTag("searchIcon").performSemanticsAction(SemanticsActions.OnLongClick)
        rule.onNodeWithText("Latch settings").performClick()
        rule.onNodeWithTag("settings").assertIsDisplayed()
        rule.onNodeWithTag("settingsSection:Appearance").performClick()
        rule.onNodeWithText("Apps per drawer").assertIsDisplayed()
        val before = rule.onNodeWithTag("appsPerDrawer:value").fetchSemanticsNode()
            .config[SemanticsProperties.Text].first().text.toInt()
        val button = if (before < 7) "appsPerDrawer:plus" else "appsPerDrawer:minus"
        rule.onNodeWithTag(button).performClick()
        rule.waitUntil(3_000) {
            rule.onNodeWithTag("appsPerDrawer:value").fetchSemanticsNode()
                .config[SemanticsProperties.Text].first().text.toInt() != before
        }
        // Restore the setting for other tests.
        rule.onNodeWithTag(if (before < 7) "appsPerDrawer:minus" else "appsPerDrawer:plus").performClick()
        rule.activity.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitUntil(3_000) { rule.onAllNodesWithTag("settings").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun slidingFromSearchIconRunsHighlightedButton() {
        waitForTag("searchIcon")
        val icon = rule.onNodeWithTag("searchIcon")
        icon.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitUntil(3_000) { rule.onAllNodesWithTag("quickAction:0").fetchSemanticsNodes().isNotEmpty() }
        val targetY = rule.onNodeWithTag("quickAction:0").fetchSemanticsNode().boundsInRoot.center.y
        val iconTop = icon.fetchSemanticsNode().boundsInRoot.top
        icon.performTouchInput {
            moveTo(Offset(center.x, targetY - iconTop))
            up()
        }
        rule.onNodeWithText("New category").assertIsDisplayed()
    }
}
