package dev.ninomae.home

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Held app (C3): three joined pieces of frosted glass. Who it is (with App info), its own shortcuts,
 * then every action in a tidy two-column grid of rows. The side that meets the dock stays small.
 */
@Composable
fun AppMenu(st: HomeState, ui: Ui, compact: Boolean) {
    val app = ui.menuApp ?: return
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val big = 24.dp
    val j = 6.dp
    // Joined onto the dock when it came from there, so the dock side stays small.
    val bottom = if (ui.menuFromDock && ui.above) j else big
    val top = if (ui.menuFromDock && !ui.above) j else big
    val pinned = st.isPinned(app.key)
    val shortcuts = remember(app.key) { Shortcuts.forApp(ctx, app) }
    val onHome = st.home.any { it.screen == ui.screen && it.type == "app" && it.app == app.key }
    val opened = st.launches[app.key] ?: 0
    // Over the drawer or a folder it sits on more glass, so it takes a denser wash to stay readable.
    val wash = if (ui.menuParent != Layer.None) 0.72f else 0.55f
    val density = androidx.compose.ui.platform.LocalDensity.current
    // From the dock it takes the dock's width, so the two meet edge to edge.
    val width = if (ui.menuFromDock && ui.dockRect.width > 0f) with(density) { ui.dockRect.width.toDp() }
        else if (compact) (ui.screenWdp - 32f).dp else 340.dp

    // Where it lives, if there's something to do about it: out of a folder, off the home screen, or onto it.
    val place: MenuAction? = when {
        ui.menuFolderId != null -> MenuAction("remove_circle", "Take out") {
            st.removeFromFolder(ui.menuFolderId!!, app.key); st.addAppToHome(app.key, ui.screen, maxOf(ui.page, 0)); ui.close()
        }
        ui.menuHomeId != null -> MenuAction("hide_source", "Remove from home") { st.removeItem(ui.menuHomeId!!); ui.back() }
        !onHome -> MenuAction("add_to_home_screen", "Add to home") { st.addAppToHome(app.key, ui.screen, maxOf(ui.page, 0)); ui.back() }
        else -> null
    }
    val left = listOfNotNull(
        MenuAction("splitscreen_left", "Split screen") { ui.close(); Actions.split(ctx, st, app, null) },
        MenuAction("pip", "Pop-up window") { ui.close(); Actions.window(ctx, st, app) },
        MenuAction("join", "Make a pair") { ui.newPair(app.key) },
        if (st.edgeOn) MenuAction(if (st.isOnEdge(app.key)) "playlist_remove" else "playlist_add", if (st.isOnEdge(app.key)) "Off the edge" else "Add to edge") {
            st.toggleEdge(app.key); ui.back()
        } else null,
        place ?: MenuAction("info", "App info") { Actions.info(ctx, app); ui.close() },
    )
    val right = listOf(
        MenuAction(if (pinned) "keep_off" else "keep", if (pinned) "Unpin" else "Pin to dock") {
            if (!pinned && st.dockFull(ui.screen)) ui.note(DOCK_FULL) else st.togglePin(app.key)
            ui.back()
        },
        MenuAction("edit", "Rename") { ui.rename(app.key) },
        MenuAction("sell", "Tags") { ui.pickTags(app.key) },
        MenuAction("visibility_off", "Hide") { st.setHidden(app.key, true); ui.back() },
        MenuAction("delete", "Uninstall", danger = true) { Actions.uninstall(ctx, app); ui.close() },
    )

    Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // who it is
        Row(
            Modifier.fillMaxWidth().height(64.dp).frosted(st, corners(top, top, j, j), wash = wash, edge = 0.14f)
                .padding(start = 12.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIcon(app, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Title(app.label, size = 17.sp)
                Lbl(
                    when { opened == 0 -> "Hold and drag to move it"; opened == 1 -> "Opened once"; else -> "Opened $opened times" },
                    size = 11.sp, color = pal.sub,
                )
            }
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).clickable { Actions.info(ctx, app); ui.close() },
                contentAlignment = Alignment.Center,
            ) { Glyph("info", size = 18.dp) }
        }
        // its own shortcuts
        if (shortcuts.isNotEmpty()) Column(Modifier.fillMaxWidth().frosted(st, RoundedCornerShape(j), wash = wash, edge = 0.14f)) {
            shortcuts.forEachIndexed { i, s ->
                val icon = remember(s.id) { Shortcuts.icon(ctx, s) }
                if (i > 0) Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
                Row(
                    Modifier.fillMaxWidth().height(46.dp).clickable { ui.close(); Shortcuts.start(ctx, s) }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (icon != null) Image(icon, null, Modifier.size(20.dp)) else Glyph("arrow_outward", size = 18.dp, color = pal.acc)
                    Spacer(Modifier.width(14.dp))
                    Lbl((s.shortLabel ?: s.longLabel ?: "").toString(), Modifier.weight(1f), size = 14.sp)
                    Glyph("north_east", size = 15.dp, color = pal.dim)
                }
            }
        }
        // everything else, two columns
        Row(
            Modifier.fillMaxWidth().frosted(st, corners(j, j, bottom, bottom), wash = wash, edge = 0.14f).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(left, right).forEach { col ->
                Column(Modifier.weight(1f)) { col.forEach { ActionRow(it) } }
            }
        }
    }
}

private class MenuAction(val glyph: String, val text: String, val danger: Boolean = false, val go: () -> Unit)

private val Danger = Color(0xFFE8A39B)

@Composable
private fun ActionRow(a: MenuAction) {
    val pal = LocalPal.current
    val c = if (a.danger) Danger else pal.fg
    Row(
        Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = a.go).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(a.glyph, size = 18.dp, color = if (a.danger) Danger else pal.acc)
        Spacer(Modifier.width(12.dp))
        Lbl(a.text, size = 13.sp, color = c)
    }
}

/** Held pair (bar chip or drawer): the same glass as the app menu. Open, Edit, Delete. */
@Composable
fun PairMenu(st: HomeState, ui: Ui) {
    val p = ui.pair ?: return
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val big = 24.dp
    val j = 6.dp
    Column(Modifier.width(250.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth().height(58.dp).frosted(st, corners(big, big, j, j), wash = 0.55f, edge = 0.14f).padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PairIcon(st.app(p.left), st.app(p.right), 34.dp)
            Spacer(Modifier.width(10.dp))
            Title(p.name, Modifier.weight(1f), size = 16.sp)
            Box(
                Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(pal.acc).clickable { ui.close(); Actions.openPair(ctx, st, p) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Lbl("Open", size = 13.sp, color = pal.ink, weight = FontWeight.SemiBold) }
        }
        Column(Modifier.fillMaxWidth().frosted(st, corners(j, j, big, big), wash = 0.55f, edge = 0.14f).padding(6.dp)) {
            ActionRow(MenuAction("edit", "Edit") { ui.editPair(p) })
            ActionRow(MenuAction("delete", "Delete", danger = true) { st.deletePair(p.id); ui.close() })
        }
    }
}
