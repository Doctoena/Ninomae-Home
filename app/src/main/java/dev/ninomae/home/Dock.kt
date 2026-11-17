package dev.ninomae.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Joined dock: pinned apps | recent apps | all apps. Holding an app joins its menu onto the dock. */
@Composable
fun Dock(st: HomeState, ui: Ui, compact: Boolean, modifier: Modifier) {
    val pal = LocalPal.current
    val pinned = st.pinned.mapNotNull { st.app(it) }.let { if (compact) it.take(4) else it.take(8) }
    val recents = st.dockRecents(if (compact) 0 else 3)
    val menuOnDock = ui.layer == Layer.Menu && ui.menuFromDock
    var pinRect by remember { mutableStateOf(Rect.Zero) }
    var recRect by remember { mutableStateOf(Rect.Zero) }
    val big = Rad.dock
    val j = Rad.joined

    Row(modifier.height(66.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Piece(
            Modifier.fillMaxHeight().onGloballyPositioned { pinRect = it.boundsInRoot() },
            corners(if (menuOnDock && ui.anchor == pinRect) j else big, j, j, big),
            edge = true,
        ) {
            Row(
                Modifier.fillMaxHeight().padding(horizontal = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pinned.isEmpty()) {
                    Lbl("Hold an app to pin it here", Modifier.padding(horizontal = 6.dp), size = 13.sp, color = pal.sub)
                }
                pinned.forEach { app -> DockIcon(st, ui, app) { pinRect } }
            }
        }
        if (recents.isNotEmpty()) {
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
                    recents.forEach { app -> DockIcon(st, ui, app) { recRect } }
                }
            }
        }
        Piece(
            Modifier.size(62.dp, 66.dp).clickable { ui.toggle(Layer.Drawer) },
            corners(j, big, big, j),
            color = if (ui.layer == Layer.Drawer) pal.l5 else pal.l3,
            edge = true,
        ) {
            Glyph("apps", Modifier.align(Alignment.Center), size = 24.dp, color = if (ui.layer == Layer.Drawer) pal.acc else pal.fg)
        }
    }
}

@Composable
private fun DockIcon(st: HomeState, ui: Ui, app: AppEntry, pieceRect: () -> Rect) {
    val ctx = LocalContext.current
    AppIcon(
        app,
        44.dp,
        Modifier
            .clip(RoundedCornerShape(13.dp))
            .press(onLong = { ui.showMenu(app, pieceRect(), above = true, fromDock = true) }) { Actions.open(ctx, st, app) },
    )
}
