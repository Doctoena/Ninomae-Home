package dev.ninomae.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Held app: who it is, its own shortcuts, then Split / Window / Pair / Rename,
 * then where it lives (dock, home, folder), Hide and Uninstall.
 */
@Composable
fun AppMenu(st: HomeState, ui: Ui, compact: Boolean) {
    val app = ui.menuApp ?: return
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val big = 22.dp
    val j = Rad.joined
    // Joined onto the dock when it came from there, so the dock side stays small.
    val bottom = if (ui.menuFromDock && ui.above) j else big
    val top = if (ui.menuFromDock && !ui.above) j else big
    val pinned = st.isPinned(app.key)
    val shortcuts = remember(app.key) { Shortcuts.forApp(ctx, app) }
    val onHome = st.home.any { it.screen == ui.screen && it.type == "app" && it.app == app.key }
    val opened = st.launches[app.key] ?: 0
    val width = if (compact) (ui.screenWdp - 32f).dp else 300.dp

    Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Piece(Modifier.fillMaxWidth().height(64.dp), corners(top, top, j, j), pal.l4, edge = true) {
            Row(Modifier.fillMaxSize().padding(start = 12.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Title(app.label, size = 17.sp)
                    Lbl(
                        when { opened == 0 -> "Hold and drag to move it"; opened == 1 -> "Opened once"; else -> "Opened $opened times" },
                        size = 12.sp, color = pal.sub,
                    )
                }
                Box(
                    Modifier.size(40.dp, 36.dp).clip(RoundedCornerShape(12.dp)).background(pal.l5).clickable { Actions.info(ctx, app); ui.close() },
                    contentAlignment = Alignment.Center,
                ) { Glyph("info", size = 19.dp) }
            }
        }
        shortcuts.forEach { s ->
            val icon = remember(s.id) { Shortcuts.icon(ctx, s) }
            Piece(Modifier.fillMaxWidth().height(48.dp), RoundedCornerShape(j), pal.l4, edge = true, onClick = { ui.close(); Shortcuts.start(ctx, s) }) {
                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) Image(icon, null, Modifier.size(22.dp)) else Glyph("arrow_outward", size = 19.dp)
                    Spacer(Modifier.width(14.dp))
                    Lbl((s.shortLabel ?: s.longLabel ?: "").toString(), Modifier.weight(1f))
                }
            }
        }
        Piece(Modifier.fillMaxWidth().height(80.dp), RoundedCornerShape(j), pal.l4, edge = true) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BigButton("splitscreen_left", "Split", Modifier.weight(1f)) { ui.close(); Actions.split(ctx, st, app, null) }
                BigButton("pip", "Window", Modifier.weight(1f)) { ui.close(); Actions.window(ctx, st, app) }
                BigButton("join", "Pair", Modifier.weight(1f)) { ui.newPair(app.key) }
                BigButton("edit", "Rename", Modifier.weight(1f)) { ui.rename(app.key) }
            }
        }
        Piece(Modifier.fillMaxWidth().height(52.dp), corners(j, j, bottom, bottom), pal.l4, edge = true) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                StripAction(if (pinned) "keep_off" else "keep", if (pinned) "Unpin" else "Pin", false) { st.togglePin(app.key); ui.close() }
                Hair()
                when {
                    ui.menuFolderId != null -> StripAction("remove_circle", "Take out", false) {
                        st.removeFromFolder(ui.menuFolderId!!, app.key); st.addAppToHome(app.key, ui.screen, ui.page); ui.close()
                    }
                    ui.menuHomeId != null -> StripAction("hide_source", "Remove", false) { st.removeItem(ui.menuHomeId!!); ui.close() }
                    !onHome -> StripAction("add_to_home_screen", "Add", false) { st.addAppToHome(app.key, ui.screen, ui.page); ui.close() }
                    else -> StripAction("home", "On home", false) { ui.close() }
                }
                Hair()
                StripAction("visibility_off", "Hide", false) { st.setHidden(app.key, true); ui.close() }
                Hair()
                StripAction("delete", "Uninstall", true) { Actions.uninstall(ctx, app); ui.close() }
            }
        }
    }
}

@Composable
private fun RowScope.StripAction(glyph: String, text: String, danger: Boolean, onClick: () -> Unit) {
    val c = if (danger) Color(0xFFFFB4AB) else LocalPal.current.fg
    Row(
        Modifier.weight(1f).fillMaxHeight().clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(glyph, size = 17.dp, color = c)
        Spacer(Modifier.width(5.dp))
        Lbl(text, size = 12.sp, color = c)
    }
}

/** Held pair (bar chip or drawer): open, edit, delete. */
@Composable
fun PairMenu(st: HomeState, ui: Ui) {
    val p = ui.pair ?: return
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val big = Rad.normal
    val j = Rad.joined
    Column(Modifier.width(240.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Piece(Modifier.fillMaxWidth().height(54.dp), corners(big, big, j, j), pal.l4, edge = true) {
            Row(Modifier.fillMaxSize().padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                PairIcon(st.app(p.left), st.app(p.right), 34.dp)
                Spacer(Modifier.width(10.dp))
                Title(p.name, Modifier.weight(1f), size = 16.sp)
                AccentButton("Open", height = 32.dp) { ui.close(); Actions.openPair(ctx, st, p) }
            }
        }
        MenuRow("edit", "Edit", RoundedCornerShape(j)) { ui.editPair(p) }
        MenuRow("delete", "Delete", corners(j, j, big, big)) { st.deletePair(p.id); ui.close() }
    }
}

@Composable
fun BigButton(glyph: String, text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val pal = LocalPal.current
    Piece(modifier.height(54.dp), RoundedCornerShape(Rad.segment), pal.l5, onClick = onClick) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Glyph(glyph, size = 20.dp)
            Spacer(Modifier.height(3.dp))
            Lbl(text, size = 12.sp)
        }
    }
}

@Composable
fun MenuRow(glyph: String, text: String, shape: Shape, onClick: () -> Unit) {
    val pal = LocalPal.current
    Piece(Modifier.fillMaxWidth().height(42.dp), shape, pal.l4, edge = true, onClick = onClick) {
        Row(Modifier.fillMaxSize().padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Glyph(glyph, size = 19.dp)
            Spacer(Modifier.width(12.dp))
            Lbl(text)
        }
    }
}
