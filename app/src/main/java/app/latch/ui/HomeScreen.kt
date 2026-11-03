package app.latch.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.latch.ui.theme.LatchType

/** Callbacks from Home to the root screen. */
interface HomeActions {
    fun toggle(categoryId: String)
    fun collapseAll()
    fun requestRename(category: CategoryUi)
    fun requestDelete(category: CategoryUi)
    fun moveCategory(categoryId: String, toIndex: Int)
}

/**
 * Centered list of category names. Tapping a name expands its apps (accordion); names are also the
 * drop targets for app drag-and-drop, and can be reordered by long-press + vertical drag.
 */
@Composable
fun HomeScreen(
    categories: List<CategoryUi>,
    expandedCategoryId: String?,
    dragState: DragState,
    tileActions: TileActions,
    homeActions: HomeActions,
    appsPerDrawer: Int,
    modifier: Modifier = Modifier
) {
    // --- Category reordering (local order while dragging, committed on release) ---
    var reorderingId by remember { mutableStateOf<String?>(null) }
    var reorderPointerY by remember { mutableFloatStateOf(0f) }
    var localOrder by remember { mutableStateOf<List<String>?>(null) }
    val ids = categories.map { it.id }
    LaunchedEffect(ids) {
        // Drop the local order once the store reflects it (or the set of categories changed).
        val local = localOrder
        if (reorderingId == null && local != null && (local == ids || local.toSet() != ids.toSet())) localOrder = null
    }
    val shown = localOrder?.let { order ->
        val byId = categories.associateBy { it.id }
        order.mapNotNull { byId[it] }.takeIf { it.size == categories.size }
    } ?: categories

    val currentCategories by rememberUpdatedState(categories)
    val currentHomeActions by rememberUpdatedState(homeActions)
    val reorder = remember {
        object : ReorderCallbacks {
            override fun start(id: String, rootY: Float) {
                currentHomeActions.collapseAll()
                reorderingId = id
                reorderPointerY = rootY
                localOrder = (localOrder ?: currentCategories.map { it.id })
            }

            override fun move(rootY: Float) {
                val id = reorderingId ?: return
                reorderPointerY = rootY
                val order = localOrder ?: return
                val target = order.firstOrNull { other ->
                    other != id && dragState.targetBounds(other)?.let { rootY in it.top..it.bottom } == true
                } ?: return
                localOrder = order.toMutableList().apply {
                    remove(id)
                    add(order.indexOf(target), id)
                }
            }

            override fun end() {
                val id = reorderingId ?: return
                val index = localOrder?.indexOf(id) ?: -1
                reorderingId = null
                if (index >= 0) currentHomeActions.moveCategory(id, index)
            }

            override fun cancel() {
                reorderingId = null
                localOrder = null
            }
        }
    }

    // Stable instance: read inside graphicsLayer only, so dragging doesn't recompose the list.
    val reorderOffset: (String) -> Float = remember(dragState) {
        { id -> dragState.targetBounds(id)?.let { reorderPointerY - it.center.y } ?: 0f }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewport = maxHeight
        val insets = WindowInsets.systemBars.asPaddingValues()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .testTag("home")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = viewport)
                    .padding(insets)
                    .padding(vertical = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                shown.forEach { category ->
                    // Keyed so a reordered item keeps its state and its in-flight gesture.
                    key(category.id) {
                        CategoryItem(
                            category = category,
                            expanded = category.id == expandedCategoryId,
                            dragState = dragState,
                            tileActions = tileActions,
                            homeActions = homeActions,
                            appsPerDrawer = appsPerDrawer,
                            reorder = reorder,
                            reordering = category.id == reorderingId,
                            reorderOffsetY = reorderOffset
                        )
                    }
                }
            }
        }
    }
}

private interface ReorderCallbacks {
    fun start(id: String, rootY: Float)
    fun move(rootY: Float)
    fun end()
    fun cancel()
}

@Composable
private fun CategoryItem(
    category: CategoryUi,
    expanded: Boolean,
    dragState: DragState,
    tileActions: TileActions,
    homeActions: HomeActions,
    appsPerDrawer: Int,
    reorder: ReorderCallbacks,
    reordering: Boolean,
    reorderOffsetY: (String) -> Float
) {
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    val current by rememberUpdatedState(category)
    val actions by rememberUpdatedState(homeActions)
    var coords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }

    val hovered = dragState.hoveredCategoryId == category.id
    val hoverScale by animateFloatAsState(if (hovered) 1.18f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "hover")
    val bump = remember { Animatable(1f) }
    LaunchedEffect(dragState.dropCount) {
        if (dragState.dropCount > 0 && dragState.lastDropCategoryId == category.id) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            bump.animateTo(1.25f, tween(90))
            bump.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
        }
    }
    DisposableEffect(category.id) {
        onDispose { dragState.unregisterTarget(category.id) }
    }

    val handler = remember {
        object : TapLongPressDragHandler {
            fun rootY(local: Offset) = coords?.takeIf { it.isAttached }?.localToRoot(local)?.y ?: local.y
            override fun onTap() = actions.toggle(current.id)
            override fun onLongPress() {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                menuOpen = true
            }
            override fun onDragStart(local: Offset) {
                menuOpen = false
                reorder.start(current.id, rootY(local))
            }
            override fun onDrag(local: Offset) = reorder.move(rootY(local))
            override fun onDragEnd() = reorder.end()
            override fun onDragCancel() = reorder.cancel()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // Drop-target bounds, outside the reorder translation.
                    .onGloballyPositioned {
                        coords = it
                        dragState.registerTarget(category.id, it.boundsInRoot())
                    }
                    .tapLongPressDrag(category.id, handler)
                    .semantics {
                        onClick {
                            actions.toggle(current.id)
                            true
                        }
                        onLongClick {
                            menuOpen = true
                            true
                        }
                    }
                    .testTag("category:${category.name}")
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = category.name,
                    style = LatchType.CategoryName,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .graphicsLayer {
                            // Visual only: the parent's bounds (drop/reorder target) are not affected.
                            val s = hoverScale * bump.value * (if (reordering) 1.08f else 1f)
                            scaleX = s
                            scaleY = s
                            translationY = if (reordering) reorderOffsetY(category.id) else 0f
                            alpha = when {
                                reordering -> 0.9f
                                dragState.isDragging && !hovered -> 0.75f
                                else -> 1f
                            }
                        }
                )
            }
            // Sibling of the gesture node (not a child), so dismissing the popup when a reorder
            // drag starts doesn't reset the in-flight gesture.
            Box(Modifier.align(Alignment.Center)) {
                ContextMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    actions = listOf(
                        MenuAction("Rename") { actions.requestRename(current) },
                        MenuAction("Delete") { actions.requestDelete(current) }
                    )
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut()
        ) {
            CategoryApps(category, tileActions, appsPerDrawer)
        }
    }
}

@Composable
private fun CategoryApps(category: CategoryUi, tileActions: TileActions, appsPerDrawer: Int) {
    if (category.apps.isEmpty()) {
        Text(
            text = "Long-press an app in the drawer (swipe up)\nand drag it here",
            style = LatchType.Hint,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )
        return
    }
    val context = remember(category.id) { TileContext(category.id) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Fit [appsPerDrawer] tiles across; more than that scroll horizontally.
        val tileWidth = ((maxWidth - 10.dp * 2 - 8.dp * (appsPerDrawer - 1)) / appsPerDrawer).coerceAtLeast(48.dp)
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("categoryApps:${category.name}"),
            contentPadding = PaddingValues(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            items(category.apps, key = { it.key.toString() }) { entry ->
                AppTile(entry, context, tileActions, Modifier.width(tileWidth))
            }
        }
    }
}
