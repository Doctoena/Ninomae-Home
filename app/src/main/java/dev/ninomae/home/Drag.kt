package dev.ninomae.home

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.roundToInt

/** What's being held: an app (from the drawer, dock or a folder) or something on the home screen. */
data class DragSrc(
    val appKey: String? = null,
    val item: HomeItem? = null,
    val fromDock: Boolean = false,
    val folderId: String? = null,
)

data class Hold(val src: DragSrc, val start: Offset)

/** Tap, or hold (with a buzz) reporting where in root coordinates the finger is. */
fun Modifier.holdable(onTap: () -> Unit, onHold: (Offset) -> Unit): Modifier = composed {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val tap by rememberUpdatedState(onTap)
    val hold by rememberUpdatedState(onHold)
    val haptic = LocalHapticFeedback.current
    this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { tap() },
                onLongPress = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    hold(origin + it)
                },
            )
        }
}

/**
 * Watches every touch from the root. Once something is held, moving the finger turns the hold
 * into a drag (closing any menu), and lifting it drops wherever it is.
 */
fun Modifier.dragTracker(st: HomeState, ui: Ui): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            val h = ui.hold ?: continue
            val c = ev.changes.firstOrNull() ?: continue
            if (c.pressed) {
                if (ui.drag == null && !st.locked && (c.position - h.start).getDistance() > viewConfiguration.touchSlop * 0.5f) {
                    ui.beginDrag(c.position)
                }
                if (ui.drag != null) {
                    ui.dragPos = c.position
                    ev.changes.forEach { it.consume() }
                }
            } else {
                if (ui.drag != null) {
                    ev.changes.forEach { it.consume() }
                    performDrop(st, ui)
                }
                ui.hold = null
                ui.drag = null
            }
        }
    }
}

/** Where the dragged thing would land on the grid: (x, y) of its top-left cell, or null if off the grid. */
fun dropCell(ui: Ui, w: Int, h: Int): Pair<Int, Int>? {
    val p = ui.dragPos
    val r = ui.gridRect
    if (!r.contains(p) || ui.cellW <= 0f) return null
    val col = ((p.x - r.left) / ui.cellW).toInt()
    val row = ((p.y - r.top) / ui.cellH).toInt()
    val x = (col - (w - 1) / 2).coerceIn(0, (gridCols(ui.screen) - w).coerceAtLeast(0))
    val y = (row - (h - 1) / 2).coerceIn(0, (gridRows(ui.screen) - h).coerceAtLeast(0))
    return x to y
}

fun cellUnderFinger(ui: Ui): Pair<Int, Int>? {
    val p = ui.dragPos
    val r = ui.gridRect
    if (!r.contains(p) || ui.cellW <= 0f) return null
    return ((p.x - r.left) / ui.cellW).toInt() to ((p.y - r.top) / ui.cellH).toInt()
}

fun overRemoveZone(ui: Ui): Boolean = ui.dragPos.y < ui.gridRect.top && ui.gridRect != androidx.compose.ui.geometry.Rect.Zero

private fun performDrop(st: HomeState, ui: Ui) {
    val src = ui.drag?.src ?: return
    val p = ui.dragPos
    val screen = ui.screen
    val page = ui.page
    // Dropped while the widgets page is showing: nothing to drop onto there.
    if (page < 0 && !overRemoveZone(ui) && !ui.dockRect.contains(ui.dragPos)) return
    val key = src.appKey ?: src.item?.takeIf { it.type == "app" }?.app

    // Above the grid: take it off the home screen, or out of its folder.
    if (overRemoveZone(ui)) {
        when {
            src.item != null -> st.removeItem(src.item.id)
            src.folderId != null && key != null -> st.removeFromFolder(src.folderId, key)
            src.fromDock && key != null -> st.togglePin(key)
        }
        return
    }

    // On the dock: pin it where it was dropped.
    if (ui.dockRect.contains(p) && key != null) {
        val slot = ((p.x - ui.pinRect.left - ui.dockPadPx) / ui.dockSlotPx).roundToInt()
        st.pinAt(key, slot)
        src.item?.let { st.removeItem(it.id) }
        src.folderId?.let { st.removeFromFolder(it, key) }
        return
    }

    val w = src.item?.w ?: 1
    val h = src.item?.h ?: 1
    val under = cellUnderFinger(ui) ?: return
    val (x, y) = dropCell(ui, w, h) ?: return

    val target = st.itemAt(screen, page, under.first, under.second, ignore = src.item?.id)

    // A widget onto a widget: stack them.
    if (src.item != null && src.item.isWidget && target != null && target.isWidget) {
        st.stackInto(target, src.item)
        return
    }

    // Onto an app or folder: make or grow a folder.
    if (target != null && key != null && (src.item == null || src.item.type == "app") &&
        (target.type == "folder" || (target.type == "app" && target.app != key))
    ) {
        if (src.folderId == target.id) return
        st.mergeInto(target, key, removeId = src.item?.id)
        if (src.fromDock) st.togglePin(key)
        src.folderId?.let { st.removeFromFolder(it, key) }
        return
    }

    if (st.fits(screen, page, x, y, w, h, ignore = src.item?.id)) {
        val item = src.item ?: HomeItem(st.newId(), screen, page, x, y, type = "app", app = key ?: return)
        st.placeAt(screen, page, x, y, item)
        if (src.fromDock && key != null) st.togglePin(key)
        src.folderId?.let { if (key != null) st.removeFromFolder(it, key) }
    }
}
