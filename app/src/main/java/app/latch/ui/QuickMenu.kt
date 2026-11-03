package app.latch.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.latch.ui.theme.TextShadow
import kotlin.math.cos
import kotlin.math.sin

enum class QuickIcon { AddDrawer, Settings }

data class QuickAction(val label: String, val icon: QuickIcon, val onClick: () -> Unit)

/**
 * The quick menu opened by long-pressing the search icon. While the finger is still down, sliding
 * over a button highlights it; releasing runs it and always closes the menu.
 */
@Stable
class QuickMenuState {
    var isOpen by mutableStateOf(false)
        private set

    /** Index of the button under the finger, or -1. */
    var highlighted by mutableIntStateOf(-1)
        private set

    // Not snapshot state: written on layout, read only during hit-testing.
    private val bounds = HashMap<Int, Rect>()

    fun open() {
        highlighted = -1
        isOpen = true
    }

    fun close() {
        isOpen = false
        highlighted = -1
    }

    internal fun register(index: Int, rect: Rect) {
        bounds[index] = rect
    }

    /** Highlights the row under [rootPosition]; returns true when the highlight changed. */
    fun pointerAt(rootPosition: Offset): Boolean {
        if (!isOpen) return false
        val hit = bounds.entries.firstOrNull { (_, r) -> rootPosition.y in r.top..r.bottom }?.key ?: -1
        val changed = hit != highlighted
        highlighted = hit
        return changed
    }

    /** Finger lifted: closes the menu and returns the button to run, or -1 for none. */
    fun release(): Int {
        val index = highlighted
        close()
        return index
    }
}

private val ButtonSize = 56.dp
private val RowGap = 16.dp

@Composable
fun QuickMenu(state: QuickMenuState, actions: List<QuickAction>, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = state.isOpen,
        enter = fadeIn(tween(60)),
        exit = fadeOut(tween(120)),
        modifier = modifier
    ) {
        val halfGap = with(LocalDensity.current) { RowGap.toPx() } / 2f
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { state.close() }
                .testTag("quickMenu")
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    // Sits just above the search bar (12dp margin + 56dp bar).
                    .padding(end = 16.dp, bottom = 12.dp + 56.dp + 20.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(RowGap)
            ) {
                actions.forEachIndexed { index, action ->
                    QuickButton(
                        action = action,
                        highlighted = state.highlighted == index,
                        onClick = {
                            state.close()
                            action.onClick()
                        },
                        modifier = Modifier
                            .onGloballyPositioned {
                                // Grow each band by half the gap so sliding between rows never drops the highlight.
                                val r = it.boundsInRoot()
                                state.register(index, Rect(r.left, r.top - halfGap, r.right, r.bottom + halfGap))
                            }
                            .testTag("quickAction:$index")
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickButton(action: QuickAction, highlighted: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val color by animateColorAsState(if (highlighted) LocalAccentColor.current else Color.White, label = "quickColor")
    val shift by animateDpAsState(
        if (highlighted) (-56).dp else 0.dp,
        spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "quickShift"
    )
    Row(
        modifier = modifier
            .offset(x = shift)
            .clip(RoundedCornerShape(ButtonSize / 2))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = action.label,
            style = TextStyle(color = color, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, shadow = TextShadow)
        )
        Spacer(Modifier.width(16.dp))
        Box(
            Modifier.size(ButtonSize).clip(CircleShape).background(color),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(24.dp)) { drawQuickIcon(action.icon, Color.Black) }
        }
    }
}

private fun DrawScope.drawQuickIcon(icon: QuickIcon, color: Color) {
    val w = size.width
    val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
    when (icon) {
        QuickIcon.AddDrawer -> {
            drawLine(color, Offset(w * 0.5f, w * 0.15f), Offset(w * 0.5f, w * 0.85f), stroke.width, StrokeCap.Round)
            drawLine(color, Offset(w * 0.15f, w * 0.5f), Offset(w * 0.85f, w * 0.5f), stroke.width, StrokeCap.Round)
        }
        QuickIcon.Settings -> {
            // Gear: eight teeth around a ring with a hole.
            val c = Offset(w / 2f, w / 2f)
            repeat(8) { i ->
                val a = Math.toRadians(i * 45.0)
                val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
                drawLine(color, c + dir * (w * 0.28f), c + dir * (w * 0.46f), w * 0.15f, StrokeCap.Butt)
            }
            drawCircle(color, w * 0.3f, c, style = Stroke(width = w * 0.12f))
            drawCircle(color, w * 0.1f, c, style = Stroke(width = w * 0.09f))
        }
    }
}
