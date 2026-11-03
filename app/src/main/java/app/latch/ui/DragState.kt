package app.latch.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import app.latch.domain.AppEntry

data class DraggedApp(
    val entry: AppEntry,
    /** Category the drag started from, or null when it started in the drawer. */
    val sourceCategoryId: String?
)

data class Drop(val app: DraggedApp, val targetCategoryId: String)

/**
 * Shared state of an app drag-and-drop: the dragged app, the pointer in root coordinates and the
 * drop targets (category names) with their bounds in root coordinates.
 */
@Stable
class DragState {
    var dragged by mutableStateOf<DraggedApp?>(null)
        private set
    var position by mutableStateOf(Offset.Zero)
        private set
    var hoveredCategoryId by mutableStateOf<String?>(null)
        private set

    /** Category that last received a drop, and a counter to retrigger its "bump" animation. */
    var lastDropCategoryId by mutableStateOf<String?>(null)
        private set
    var dropCount by mutableIntStateOf(0)
        private set

    // Not snapshot state: written on every layout pass, read only during hit-testing.
    private val targets = HashMap<String, Rect>()

    val isDragging: Boolean get() = dragged != null

    fun registerTarget(categoryId: String, boundsInRoot: Rect) {
        targets[categoryId] = boundsInRoot
    }

    fun unregisterTarget(categoryId: String) {
        targets.remove(categoryId)
    }

    fun targetBounds(categoryId: String): Rect? = targets[categoryId]

    /** Category whose bounds contain [point], if any. */
    fun hitTest(point: Offset): String? = targets.entries.firstOrNull { it.value.contains(point) }?.key

    fun start(entry: AppEntry, sourceCategoryId: String?, at: Offset) {
        dragged = DraggedApp(entry, sourceCategoryId)
        moveTo(at)
    }

    fun moveTo(at: Offset) {
        position = at
        hoveredCategoryId = hitTest(at)
    }

    /** Ends the drag; returns the drop if it landed on a category other than its source. */
    fun end(): Drop? {
        val app = dragged
        val target = hoveredCategoryId
        reset()
        if (app == null || target == null || target == app.sourceCategoryId) return null
        lastDropCategoryId = target
        dropCount++
        return Drop(app, target)
    }

    fun cancel() = reset()

    private fun reset() {
        dragged = null
        hoveredCategoryId = null
    }
}
