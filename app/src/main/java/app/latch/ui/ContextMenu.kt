package app.latch.ui

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

data class MenuAction(val label: String, val onClick: () -> Unit)

/** A long-press popup menu. Clicking an item dismisses the menu then runs the action. */
@Composable
fun ContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    actions: List<MenuAction>,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = offset,
        modifier = Modifier.testTag("contextMenu"),
    ) {
        actions.forEach { action ->
            DropdownMenuItem(
                text = { Text(action.label) },
                onClick = {
                    onDismiss()
                    action.onClick()
                },
            )
        }
    }
}
