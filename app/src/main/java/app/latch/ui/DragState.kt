package app.latch.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import app.latch.domain.AppEntry
import app.latch.domain.AppKey

data class DraggedApp(
    val entry: AppEntry,
    /** Category the drag started from, or null when it started in the drawer. */
    val sourceCategoryId: String?
)

/**
 * A drop. [beforeIndex] is set when it landed inside an open category's row of apps: the app goes
 * just before the app at that index (`apps.size` = at the end). Null means "onto the category name".
 */
data class Drop(val app: DraggedApp, val targetCategoryId: String, val beforeIndex: Int? = null)

/**
 * Where a drop inside an open category's row would land, with the tiles on either side (root
 * coordinates) so the insertion line can be drawn between them. At least one neighbor is non-null.
 */
data class Insertion(val categoryId: String, val beforeIndex: Int, val left: Rect?, val right: Rect?)

private class TileSlot(val index: Int, val bounds: Rect)

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
    var insertion by mutableStateOf<Insertion?>(null)
        private set

    /** Category that last received a drop, and a counter to retrigger its "bump" animation. */
    var lastDropCategoryId by mutableStateOf<String?>(null)
        private set
    var dropCount by mutableIntStateOf(0)
        private set

    // Not snapshot state: written on every layout pass, read only during hit-testing.
    private val targets = HashMap<String, Rect>()
    private val rows = HashMap<String, Rect>()
    private val tiles = HashMap<String, HashMap<AppKey, TileSlot>>()

    val isDragging: Boolean get() = dragged != null

    fun registerTarget(categoryId: String, boundsInRoot: Rect) {
        targets[categoryId] = boundsInRoot
    }

    fun unregisterTarget(categoryId: String) {
        targets.remove(categoryId)
    }

    fun targetBounds(categoryId: String): Rect? = targets[categoryId]

    /** An open category's apps area (all its lines of tiles), in root coordinates. */
    fun registerRow(categoryId: String, boundsInRoot: Rect) {
        rows[categoryId] = boundsInRoot
    }

    fun unregisterRow(categoryId: String) {
        rows.remove(categoryId)
        tiles.remove(categoryId)
    }

    /** A tile at [index] in an open category's apps. */
    fun registerTile(categoryId: String, key: AppKey, index: Int, boundsInRoot: Rect) {
        tiles.getOrPut(categoryId) { HashMap() }[key] = TileSlot(index, boundsInRoot)
    }

    fun unregisterTile(categoryId: String, key: AppKey) {
        tiles[categoryId]?.remove(key)
    }

    /** Category whose bounds contain [point], if any. */
    fun hitTest(point: Offset): String? = targets.entries.firstOrNull { it.value.contains(point) }?.key

    fun start(entry: AppEntry, sourceCategoryId: String?, at: Offset) {
        dragged = DraggedApp(entry, sourceCategoryId)
        moveTo(at)
    }

    fun moveTo(at: Offset) {
        position = at
        hoveredCategoryId = hitTest(at)
        insertion = if (hoveredCategoryId == null) insertionAt(at) else null
    }

    /**
     * Insertion point under [point] inside an open category's apps: on the line of tiles nearest the
     * pointer vertically, before the first tile whose center is right of the pointer, else after the
     * line's last tile. Null when it would leave the dragged app where it already is.
     */
    private fun insertionAt(point: Offset): Insertion? {
        val app = dragged ?: return null
        val categoryId = rows.entries.firstOrNull { it.value.contains(point) }?.key ?: return null
        val slots = tiles[categoryId]?.values.orEmpty()
        val nearest = slots.minByOrNull { verticalDistance(point.y, it.bounds) } ?: return null
        val line = slots.filter { it.bounds.top == nearest.bounds.top }.sortedBy { it.index }
        val right = line.firstOrNull { point.x < it.bounds.center.x }
        val left = line.lastOrNull { it.index < (right?.index ?: Int.MAX_VALUE) }
        val beforeIndex = right?.index ?: (line.last().index + 1)
        if (app.sourceCategoryId == categoryId) {
            val from = tiles[categoryId]?.get(app.entry.key)?.index
            if (from != null && (beforeIndex == from || beforeIndex == from + 1)) return null
        }
        return Insertion(categoryId, beforeIndex, left?.bounds, right?.bounds)
    }

    private fun verticalDistance(y: Float, r: Rect): Float = when {
        y < r.top -> r.top - y
        y > r.bottom -> y - r.bottom
        else -> 0f
    }

    /**
     * Ends the drag; returns the drop if it landed on a category other than its source, or at a new
     * position inside an open category's row.
     */
    fun end(): Drop? {
        val app = dragged
        val target = hoveredCategoryId
        val at = insertion
        reset()
        if (app == null) return null
        if (at != null) {
            if (at.categoryId != app.sourceCategoryId) {
                lastDropCategoryId = at.categoryId
                dropCount++
            }
            return Drop(app, at.categoryId, at.beforeIndex)
        }
        if (target == null || target == app.sourceCategoryId) return null
        lastDropCategoryId = target
        dropCount++
        return Drop(app, target)
    }

    fun cancel() = reset()

    private fun reset() {
        dragged = null
        hoveredCategoryId = null
        insertion = null
    }
}
