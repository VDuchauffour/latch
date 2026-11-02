package app.latch

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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

    private fun firstCategoryTag(): String {
        rule.waitUntil(5_000) {
            rule.onAllNodes(SemanticsMatcher("category") { node ->
                node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("category:") == true
            }).fetchSemanticsNodes().isNotEmpty()
        }
        val node = rule.onAllNodes(SemanticsMatcher("category") { n ->
            n.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("category:") == true
        }).fetchSemanticsNodes().first()
        return node.config[SemanticsProperties.TestTag]
    }

    @Test fun categoryExpandsAndCollapses() {
        val tag = firstCategoryTag()
        rule.onNodeWithTag(tag).performClick()
        rule.waitUntil(3_000) {
            rule.onAllNodesWithTag("categoryApps:${tag.removePrefix("category:")}").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodes(SemanticsMatcher("hint") { n ->
                    n.config.getOrNull(SemanticsProperties.Text)
                        ?.any { it.text.startsWith("Long-press an app") } == true
                }).fetchSemanticsNodes().isNotEmpty()
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
            rule.onAllNodes(SemanticsMatcher("tile") { n ->
                n.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("tile:") == true
            }).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodes(SemanticsMatcher("tile") { n ->
            n.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("tile:") == true
        }).onFirst().performSemanticsAction(SemanticsActions.OnLongClick)
        rule.onNodeWithText("App info").assertIsDisplayed()
    }

    @Test fun addCategoryEntryOpensDialog() {
        firstCategoryTag()
        rule.onNodeWithTag("addCategory").performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("New category").assertIsDisplayed()
        rule.onNode(hasTestTag("nameField")).assertIsDisplayed()
    }
}
