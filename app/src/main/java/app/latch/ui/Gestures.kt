package app.latch.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CancellationException

/** Callbacks for [tapLongPressDrag]. Positions are local to the modified element. */
interface TapLongPressDragHandler {
    fun onTap()
    fun onLongPress()
    fun onDragStart(local: Offset)
    fun onDrag(local: Offset)
    fun onDragEnd()
    fun onDragCancel()
}

/**
 * One gesture detector for tap / long-press / long-press-then-drag:
 * - released before the long-press timeout without moving → [TapLongPressDragHandler.onTap];
 * - moved beyond touch slop (or a parent started scrolling) before the timeout → nothing, so lists scroll;
 * - held until the timeout → `onLongPress` (show the context menu), then moving beyond touch slop
 *   starts a drag that reports every move until the pointer is released.
 */
@Composable
fun Modifier.tapLongPressDrag(key: Any?, handler: TapLongPressDragHandler): Modifier {
    // The block must be a stable instance: in Compose 1.7 a new lambda resets the pointer input and
    // cancels an in-flight gesture whenever the caller recomposes (e.g. while reordering).
    val block: suspend PointerInputScope.() -> Unit = remember(handler) { { detectTapLongPressDrag(handler) } }
    return pointerInput(key, block)
}

private suspend fun PointerInputScope.detectTapLongPressDrag(handler: TapLongPressDragHandler) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        var tapped = false
        val longPressed = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (change.changedToUp()) {
                    tapped = !change.isConsumed
                    if (tapped) change.consume()
                    break
                }
                if (change.isConsumed || (change.position - down.position).getDistance() > slop) break
            }
            false
        } == null
        if (!longPressed) {
            if (tapped) handler.onTap()
            return@awaitEachGesture
        }

        handler.onLongPress()
        var dragging = false
        try {
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) {
                    if (dragging) handler.onDragEnd()
                    dragging = false
                    return@awaitEachGesture
                }
                change.consume()
                if (!dragging && (change.position - down.position).getDistance() > slop) {
                    dragging = true
                    handler.onDragStart(change.position)
                }
                if (dragging) handler.onDrag(change.position)
            }
        } catch (e: CancellationException) {
            if (dragging) handler.onDragCancel()
            throw e
        }
    }
}
