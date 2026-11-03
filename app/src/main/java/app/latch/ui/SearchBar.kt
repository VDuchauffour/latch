package app.latch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.latch.domain.AppEntry
import app.latch.ui.theme.LatchColors
import app.latch.ui.theme.LatchType

private val PanelShape = RoundedCornerShape(28.dp)
private val SearchTileContext = TileContext(categoryId = null)

private const val MAX_RESULTS = 5

/** Hold time on the search icon before the quick menu opens; shorter than the system long-press so it feels instant. */
private const val QUICK_MENU_HOLD_MS = 180L

/** Long-press on the search icon, then slide and release over a quick-menu button. */
interface QuickMenuGesture {
    fun open()

    /** Pointer moved, in root coordinates; returns true when the highlighted button changed. */
    fun move(rootPosition: Offset): Boolean

    fun release()
}

@Composable
fun HomeSearch(
    query: String,
    results: List<AppEntry>,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    quickMenu: QuickMenuGesture,
    tileActions: TileActions,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (query.isNotBlank()) {
            SearchResults(results, tileActions)
            Box(Modifier.height(10.dp))
        }
        SearchField(query, onQueryChange, onSubmit, onFocusChange, onDismiss, quickMenu)
    }
}

@Composable
private fun SearchResults(results: List<AppEntry>, tileActions: TileActions) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(LatchColors.SearchPanel)
            .padding(8.dp)
            .testTag("searchResults")
    ) {
        if (results.isEmpty()) {
            Text(
                text = "No matching app",
                style = LatchType.Hint,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)
            )
            return@BoxWithConstraints
        }
        val tileWidth = (maxWidth - 6.dp * (MAX_RESULTS - 1)) / MAX_RESULTS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)
        ) {
            val shown = results.take(MAX_RESULTS)
            shown.asReversed().forEach { entry ->
                AppTile(
                    entry = entry,
                    context = SearchTileContext,
                    actions = tileActions,
                    modifier = Modifier.width(tileWidth),
                    highlighted = entry == shown.first(),
                    tagPrefix = "searchTile"
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    quickMenu: QuickMenuGesture
) {
    var focused by remember { mutableStateOf(false) }
    var iconCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val focusRequester = remember { FocusRequester() }
    val haptics = LocalHapticFeedback.current
    // A popup taking window focus (long-press menu) also hides the keyboard; that must not end the search.
    val imeVisible = WindowInsets.imeAnimationTarget.getBottom(LocalDensity.current) > 0
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    var imeSeen by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible, focused) {
        if (!focused) {
            imeSeen = false
        } else if (imeVisible) {
            imeSeen = true
        } else if (imeSeen && windowFocused) {
            onDismiss()
        }
    }
    Box(Modifier.fillMaxWidth()) {
        BasicTextField(
            value = query,
            // The keyboard may re-commit its pending word after focus is cleared.
            onValueChange = { if (focused) onQueryChange(it) },
            singleLine = true,
            textStyle = LatchType.Hint.copy(color = LatchColors.Text),
            cursorBrush = SolidColor(LatchColors.Text),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Go
            ),
            keyboardActions = KeyboardActions(onGo = { onSubmit() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged {
                    if (it.isFocused != focused) onFocusChange(it.isFocused)
                    focused = it.isFocused
                }
                .testTag("searchField"),
            decorationBox = { inner ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(PanelShape)
                        .background(LatchColors.SearchPanel)
                        .padding(start = 24.dp, end = 48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text("Tap to search", style = LatchType.Hint)
                        }
                        inner()
                    }
                }
            }
        )
        // Outside the text field so its own long-press (text selection) never sees these touches.
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 1.dp)
                .size(56.dp)
                .onGloballyPositioned { iconCoords = it }
                .pointerInput(quickMenu) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var up: PointerInputChange? = null
                        val timedOut = withTimeoutOrNull(QUICK_MENU_HOLD_MS) {
                            up = waitForUpOrCancellation()
                        } == null
                        if (!timedOut) {
                            up?.let {
                                it.consume()
                                focusRequester.requestFocus()
                            }
                            return@awaitEachGesture
                        }
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        quickMenu.open()
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) break
                            val root = iconCoords?.takeIf { it.isAttached }?.localToRoot(change.position) ?: continue
                            if (quickMenu.move(root)) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        quickMenu.release()
                    }
                }
                .semantics {
                    onClick("Search") {
                        focusRequester.requestFocus()
                        true
                    }
                    onLongClick("Quick menu") {
                        quickMenu.open()
                        true
                    }
                }
                .testTag("searchIcon"),
            contentAlignment = Alignment.Center
        ) {
            SearchIcon(Modifier.size(22.dp))
        }
    }
}

@Composable
private fun SearchIcon(modifier: Modifier = Modifier, color: Color = LatchColors.Text) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.12f
        val radius = size.minDimension * 0.34f
        val center = Offset(radius + stroke, radius + stroke)
        drawCircle(color, radius, center, style = Stroke(stroke))
        val d = radius * 0.7071f
        drawLine(
            color,
            start = Offset(center.x + d, center.y + d),
            end = Offset(size.width - stroke / 2, size.height - stroke / 2),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}
