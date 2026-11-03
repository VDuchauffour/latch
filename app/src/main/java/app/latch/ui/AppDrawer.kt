package app.latch.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.latch.domain.Section
import app.latch.ui.theme.LatchType
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Flattened drawer layout: which list item starts each section, and which section each item is in. */
@Immutable
private class DrawerLayout(sections: List<Section>, columns: Int) {
    val sectionStart: IntArray = IntArray(sections.size)
    val sectionOfItem: IntArray

    init {
        var index = 0
        val owners = ArrayList<Int>()
        sections.forEachIndexed { s, section ->
            sectionStart[s] = index
            val items = 1 + (section.apps.size + columns - 1) / columns
            repeat(items) { owners += s }
            index += items
        }
        sectionOfItem = owners.toIntArray()
    }
}

private val DrawerTileContext = TileContext(categoryId = null)

/**
 * All apps, A–Z: sticky letter headers, rows of [AppTile]s, and a fast-scroller on the right edge.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawer(sections: List<Section>, actions: TileActions, listState: LazyListState, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.testTag("drawer")) {
        val columns = if (maxWidth >= 360.dp) 5 else 4
        val layout = remember(sections, columns) { DrawerLayout(sections, columns) }
        val insets = WindowInsets.systemBars.asPaddingValues()

        LazyColumn(
            state = listState,
            // Status-bar inset on the list itself (not contentPadding) so sticky headers pin below it.
            modifier = Modifier
                .fillMaxSize()
                .padding(top = insets.calculateTopPadding())
                .clipToBounds()
                .testTag("drawerList"),
            contentPadding = PaddingValues(
                start = 10.dp,
                end = 22.dp, // room for the fast-scroller
                top = 24.dp,
                bottom = insets.calculateBottomPadding() + 24.dp
            )
        ) {
            sections.forEach { section ->
                stickyHeader(key = "h:${section.header}", contentType = "header") {
                    Text(
                        text = section.header,
                        style = LatchType.SectionHeader,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    )
                }
                val rows = section.apps.chunked(columns)
                rows.forEachIndexed { r, row ->
                    item(key = "r:${section.header}:$r", contentType = "row") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { entry ->
                                AppTile(entry, DrawerTileContext, actions, Modifier.weight(1f))
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }

        if (sections.size > 1) {
            FastScroller(
                sections = sections,
                layout = layout,
                listState = listState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(top = insets.calculateTopPadding() + 24.dp, bottom = insets.calculateBottomPadding() + 24.dp)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun FastScroller(sections: List<Section>, layout: DrawerLayout, listState: LazyListState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var trackHeight by remember { mutableIntStateOf(1) }
    var dragging by remember { mutableStateOf(false) }
    var dragSection by remember { mutableIntStateOf(0) }

    val currentSection by remember(layout) {
        derivedStateOf {
            layout.sectionOfItem.getOrElse(listState.firstVisibleItemIndex) { 0 }
        }
    }
    val shownSection = if (dragging) dragSection else currentSection
    val thumbHeight = 44.dp
    val thumbHeightPx = with(density) { thumbHeight.toPx() }
    val fraction = if (sections.size <= 1) 0f else shownSection / (sections.size - 1f)

    fun jumpTo(y: Float) {
        val s = ((y / trackHeight) * sections.size).toInt().coerceIn(0, sections.lastIndex)
        if (s != dragSection || !dragging) {
            if (s != dragSection) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            dragSection = s
            scope.launch { listState.scrollToItem(layout.sectionStart[s]) }
        }
    }

    val currentJump by rememberUpdatedState<(Float) -> Unit>(::jumpTo)
    // Stable block: a new lambda per recomposition would reset (and cancel) the gesture.
    val scrollerInput: suspend PointerInputScope.() -> Unit = remember {
        {
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                dragging = true
                dragSection = -1
                currentJump(down.position.y)
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    change.consume()
                    currentJump(change.position.y)
                }
                dragging = false
            }
        }
    }

    Box(
        modifier
            .width(28.dp)
            .testTag("fastScroller")
            .onSizeChanged { trackHeight = it.height.coerceAtLeast(1) }
            .pointerInput(sections, layout, scrollerInput)
    ) {
        // Thumb
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, ((trackHeight - thumbHeightPx) * fraction).roundToInt()) }
                .size(width = 5.dp, height = thumbHeight)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = if (dragging) 1f else 0.7f))
        )
        // Letter bubble
        AnimatedVisibility(
            visible = dragging && dragSection in sections.indices,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset {
                    val bubble = 64.dp.toPx()
                    IntOffset(
                        x = -48.dp.roundToPx(),
                        y = ((trackHeight - thumbHeightPx) * fraction + thumbHeightPx / 2 - bubble / 2).roundToInt()
                    )
                }
        ) {
            Box(
                // requiredSize: the 28dp-wide scroller track must not squeeze the bubble.
                Modifier.requiredSize(64.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = sections.getOrNull(dragSection)?.header.orEmpty(),
                    color = Color.Black,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}
