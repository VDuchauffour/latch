package app.latch.ui

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.os.Bundle
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.latch.data.IconLoader
import app.latch.domain.AppEntry
import app.latch.domain.AppKey
import app.latch.ui.theme.LatchColors
import app.latch.ui.theme.LatchType

val LocalIconLoader = staticCompositionLocalOf<IconLoader> { error("No IconLoader provided") }

/** Where a tile lives, which decides its menu and its drag semantics. */
@Immutable
data class TileContext(val categoryId: String?)

/** What tiles can do; implemented once by the root screen. */
interface TileActions {
    fun launch(entry: AppEntry, bounds: android.graphics.Rect?, options: Bundle?)
    fun menu(entry: AppEntry, context: TileContext): List<MenuAction>
    fun dragStart(entry: AppEntry, context: TileContext, rootPosition: Offset)
    fun dragMove(rootPosition: Offset)
    fun dragEnd()
    fun dragCancel()
}

@Composable
fun rememberAppIcon(key: AppKey): ImageBitmap? {
    val loader = LocalIconLoader.current
    val version by loader.version.collectAsState()

    // False positive: `value` is assigned, but the check misses it with `by` delegation.
    @SuppressLint("ProduceStateDoesNotAssignValue")
    val icon by produceState(loader.cached(key), key, version) { value = loader.load(key) }
    return icon
}

/** Icon + label (max 2 lines). Tap launches, long-press opens the menu, long-press + move drags. */
@Composable
fun AppTile(
    entry: AppEntry,
    context: TileContext,
    actions: TileActions,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    highlighted: Boolean = false,
    tagPrefix: String = "tile"
) {
    val view = LocalView.current
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val currentEntry by rememberUpdatedState(entry)
    val currentContext by rememberUpdatedState(context)
    val currentActions by rememberUpdatedState(actions)

    val handler = remember {
        object : TapLongPressDragHandler {
            fun toRoot(local: Offset) = coords?.takeIf { it.isAttached }?.localToRoot(local) ?: local

            override fun onTap() {
                val c = coords?.takeIf { it.isAttached }
                val bounds = c?.boundsInWindow()?.let {
                    android.graphics.Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt())
                }
                currentActions.launch(currentEntry, bounds, bounds?.let { launchOptions(view, it) })
            }

            override fun onLongPress() {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                menuOpen = true
            }

            override fun onDragStart(local: Offset) {
                menuOpen = false
                currentActions.dragStart(currentEntry, currentContext, toRoot(local))
            }

            override fun onDrag(local: Offset) = currentActions.dragMove(toRoot(local))
            override fun onDragEnd() = currentActions.dragEnd()
            override fun onDragCancel() = currentActions.dragCancel()
        }
    }

    Box(modifier) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (highlighted) LatchColors.TileHover else LatchColors.Tile)
                .onGloballyPositioned { coords = it }
                .tapLongPressDrag(entry.key, handler)
                .alpha(if (dimmed) 0.35f else 1f)
                .padding(horizontal = 4.dp, vertical = 12.dp)
                .semantics {
                    contentDescription = entry.label
                    onClick {
                        handler.onTap()
                        true
                    }
                    onLongClick {
                        handler.onLongPress()
                        true
                    }
                }
                .testTag("$tagPrefix:${entry.label}"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            AppIcon(entry.key, Modifier.size(42.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                text = entry.label,
                style = LatchType.TileLabel,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
        ContextMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            actions = actions.menu(entry, context)
        )
    }
}

@Composable
fun AppIcon(key: AppKey, modifier: Modifier = Modifier) {
    val icon = rememberAppIcon(key)
    if (icon != null) {
        Image(bitmap = icon, contentDescription = null, modifier = modifier)
    } else {
        Box(modifier.clip(CircleShape).background(LatchColors.Placeholder))
    }
}

private fun launchOptions(view: View, bounds: android.graphics.Rect): Bundle? {
    val location = IntArray(2)
    view.getLocationInWindow(location)
    return runCatching {
        ActivityOptions.makeScaleUpAnimation(
            view,
            bounds.left - location[0],
            bounds.top - location[1],
            bounds.width(),
            bounds.height()
        ).toBundle()
    }.getOrNull()
}
