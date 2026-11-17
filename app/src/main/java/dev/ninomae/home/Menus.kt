package dev.ninomae.home

import androidx.compose.foundation.clickable
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

/** Held app: Open, then split / window / pair as big buttons, then the rest as joined rows. */
@Composable
fun AppMenu(st: HomeState, ui: Ui) {
    val app = ui.menuApp ?: return
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val big = Rad.normal
    val j = Rad.joined
    // Joined onto the dock when it came from there, so the dock side stays small.
    val lastBottom = if (ui.menuFromDock && ui.above) j else big
    val topCorner = if (ui.menuFromDock && !ui.above) j else big
    val pinned = st.isPinned(app.key)

    Column(Modifier.width(260.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Piece(Modifier.fillMaxWidth().height(58.dp), corners(topCorner, topCorner, j, j), pal.l4, edge = true) {
            Row(Modifier.fillMaxSize().padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app, 36.dp)
                Spacer(Modifier.width(10.dp))
                Title(app.label, Modifier.weight(1f), size = 16.sp)
                AccentButton("Open", height = 32.dp) { Actions.open(ctx, st, app); ui.close() }
            }
        }
        Piece(Modifier.fillMaxWidth().height(72.dp), RoundedCornerShape(j), pal.l4, edge = true) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BigButton("splitscreen_left", "Split", Modifier.weight(1f)) { ui.close(); Actions.split(ctx, st, app, null) }
                BigButton("pip", "Window", Modifier.weight(1f)) { ui.close(); Actions.window(ctx, st, app) }
                BigButton("join", "Pair", Modifier.weight(1f)) { ui.newPair(app.key) }
            }
        }
        MenuRow(if (pinned) "keep_off" else "keep", if (pinned) "Unpin from the dock" else "Pin to the dock", RoundedCornerShape(j)) {
            st.togglePin(app.key); ui.close()
        }
        MenuRow("info", "App info", RoundedCornerShape(j)) { Actions.info(ctx, app); ui.close() }
        MenuRow("delete", "Uninstall", corners(j, j, lastBottom, lastBottom)) { Actions.uninstall(ctx, app); ui.close() }
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
