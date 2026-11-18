package dev.ninomae.home

import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The dock (C3): one capsule of frosted glass. Pinned apps, then (inner screen) recent apps after a
 * hairline, then a rose all-apps circle (no sheen line on top: the user didn't like it). An app with notifications gets a small rose bar under it.
 * Holding an app joins its menu onto the dock; it also takes drops.
 */
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
    val r = 35.dp
    val top = if (menuOnDock) Rad.joined else r
    val shape = RoundedCornerShape(top, top, r, r)
    SideEffect {
        ui.dockSlotPx = with(density) { 58.dp.toPx() }
        ui.dockPadPx = with(density) { 14.dp.toPx() }
    }
    Row(
        modifier
            .height(70.dp)
            .onGloballyPositioned { ui.dockRect = it.boundsInRoot(); ui.pinRect = it.boundsInRoot() }
            .frosted(st, shape, wash = if (dropping) 0.12f else 0.26f, edge = 0.2f)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pinned.isEmpty()) {
            Lbl("Drag apps here to pin them", Modifier.padding(horizontal = 6.dp), size = 13.sp, color = pal.sub)
        }
        pinned.forEach { app -> DockIcon(st, ui, app, fromDock = true, badge = st.dots && app.pkg in badged) { ui.pinRect } }
        if (recents.isNotEmpty()) {
            Hairline()
            recents.forEach { app -> DockIcon(st, ui, app, fromDock = false, badge = st.dots && app.pkg in badged) { ui.pinRect } }
        }
        Hairline()
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(pal.acc).pressScale().clickable { ui.toggle(Layer.Apps) },
            contentAlignment = Alignment.Center,
        ) { Glyph("apps", size = 24.dp, color = pal.ink) }
    }
}

@Composable
private fun Hairline() {
    Box(Modifier.size(1.dp, 34.dp).background(Color.White.copy(alpha = 0.2f)))
}

@Composable
private fun DockIcon(st: HomeState, ui: Ui, app: AppEntry, fromDock: Boolean, badge: Boolean, pieceRect: () -> Rect) {
    val ctx = LocalContext.current
    val dragged = ui.drag?.src?.let { it.fromDock && it.appKey == app.key } == true
    Box(Modifier.alpha(if (dragged) 0.25f else 1f)) {
        Box(
            Modifier
                .pressScale()
                .clip(RoundedCornerShape(16.dp))
                .holdable(
                    onTap = { Actions.open(ctx, st, app) },
                    onHold = { pos ->
                        ui.hold = Hold(DragSrc(appKey = app.key, fromDock = fromDock), pos)
                        ui.showMenu(app, pieceRect(), above = true, fromDock = true)
                    },
                ),
        ) { GlassIcon(app, 48.dp) }
        if (badge) NoteBar()
    }
}
