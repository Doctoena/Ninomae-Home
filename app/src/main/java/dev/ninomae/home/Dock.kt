package dev.ninomae.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Joined dock: pinned apps | recent apps | all apps. Holding an app joins its menu onto the dock; it also takes drops. */
@Composable
fun Dock(st: HomeState, ui: Ui, compact: Boolean, modifier: Modifier) {
    val pal = LocalPal.current
    val density = LocalDensity.current
    val notes by NotifService.notes.collectAsState()
    val badged = remember(notes) { notes.map { it.pkg }.toSet() }
    val pinned = st.pinned.mapNotNull { st.app(it) }.let { if (compact) it.take(4) else it.take(9) }
    val recents = if (st.dockRecentsOn) st.dockRecents(if (compact) 0 else 3) else emptyList()
    val menuOnDock = ui.layer == Layer.Menu && ui.menuFromDock
    val dropping = ui.drag != null && ui.dockRect.contains(ui.dragPos)
    val big = Rad.dock
    val j = Rad.joined
    SideEffect {
        ui.dockSlotPx = with(density) { 54.dp.toPx() }
        ui.dockPadPx = with(density) { 11.dp.toPx() }
    }

    Row(
        modifier.height(66.dp).onGloballyPositioned { ui.dockRect = it.boundsInRoot() },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Piece(
            Modifier.fillMaxHeight().onGloballyPositioned { ui.pinRect = it.boundsInRoot() },
            corners(if (menuOnDock && ui.anchor == ui.pinRect) j else big, j, j, big),
            color = if (dropping) pal.l5 else pal.l3,
            edge = true,
        ) {
            Row(
                Modifier.fillMaxHeight().padding(horizontal = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pinned.isEmpty()) {
                    Lbl("Drag apps here to pin them", Modifier.padding(horizontal = 6.dp), size = 13.sp, color = pal.sub)
                }
                pinned.forEach { app -> DockIcon(st, ui, app, fromDock = true, badge = st.dots && app.pkg in badged) { ui.pinRect } }
            }
        }
        if (recents.isNotEmpty()) {
            var recRect = Rect.Zero
            Piece(
                Modifier.fillMaxHeight().onGloballyPositioned { recRect = it.boundsInRoot() },
                RoundedCornerShape(j),
                edge = true,
            ) {
                Row(
                    Modifier.fillMaxHeight().padding(horizontal = 11.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    recents.forEach { app -> DockIcon(st, ui, app, fromDock = false, badge = st.dots && app.pkg in badged) { recRect } }
                }
            }
        }
        Piece(
            Modifier.size(62.dp, 66.dp),
            corners(j, big, big, j),
            color = if (ui.layer == Layer.Drawer) pal.l5 else pal.l3,
            edge = true,
            onClick = { ui.toggle(Layer.Drawer) },
        ) {
            Glyph("apps", Modifier.align(Alignment.Center), size = 24.dp, color = if (ui.layer == Layer.Drawer) pal.acc else pal.fg)
        }
    }
}

@Composable
private fun DockIcon(st: HomeState, ui: Ui, app: AppEntry, fromDock: Boolean, badge: Boolean, pieceRect: () -> Rect) {
    val ctx = LocalContext.current
    val dragged = ui.drag?.src?.let { it.fromDock && it.appKey == app.key } == true
    Box(Modifier.alpha(if (dragged) 0.25f else 1f)) {
        AppIcon(
            app,
            44.dp,
            Modifier
                .pressScale()
                .clip(RoundedCornerShape(13.dp))
                .holdable(
                    onTap = { Actions.open(ctx, st, app) },
                    onHold = { pos ->
                        ui.hold = Hold(DragSrc(appKey = app.key, fromDock = fromDock), pos)
                        ui.showMenu(app, pieceRect(), above = true, fromDock = true)
                    },
                ),
        )
        if (badge) Badge(Modifier.align(Alignment.TopEnd).offset(3.dp, (-3).dp))
    }
}

@Composable
fun Badge(modifier: Modifier = Modifier, size: Dp = 12.dp) {
    val pal = LocalPal.current
    Box(modifier.size(size).clip(CircleShape).background(pal.l3).padding(2.dp).clip(CircleShape).background(pal.acc))
}
