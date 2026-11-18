package dev.ninomae.home

import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A folder opened: its name (editable) over its apps. Drag an app out to take it out. */
/**
 * An open folder (G1): a pane of frosted glass that grows out of the folder's icon, with the
 * wallpaper frosted inside it and the home screen left sharp around it. Apps sit four to a page
 * and swipe sideways. The joined pill top right adds apps (＋) or shows them all at once (↗).
 */
@Composable
fun FolderPopup(st: HomeState, ui: Ui, progress: () -> Float, modifier: Modifier) {
    val pal = LocalPal.current
    val density = LocalDensity.current
    val folder = st.home.firstOrNull { it.id == ui.folderId && it.type == "folder" } ?: return
    val notes by NotifService.notes.collectAsState()
    val badged = remember(notes) { if (st.dots) notes.map { it.pkg }.toSet() else emptySet() }
    var adding by remember(folder.id) { mutableStateOf(false) }
    var full by remember(folder.id) { mutableStateOf(false) }
    LaunchedEffect(ui.inFolder) { if (!ui.inFolder) { adding = false; full = false } }
    // The folder's icon on screen, to grow from (and to light up, in the grid).
    val icon = Rect(
        ui.gridRect.left + folder.x * ui.cellW, ui.gridRect.top + folder.y * ui.cellH,
        ui.gridRect.left + (folder.x + folder.w) * ui.cellW, ui.gridRect.top + (folder.y + folder.h) * ui.cellH,
    )
    BoxWithConstraints(modifier) {
        val sw = constraints.maxWidth.toFloat()
        val sh = constraints.maxHeight.toFloat()
        val cardW = minOf(maxWidth - 32.dp, 400.dp)
        val cardWpx = with(density) { cardW.toPx() }
        val margin = with(density) { 16.dp.toPx() }
        val gap = with(density) { 10.dp.toPx() }
        val topLimit = with(density) { 64.dp.toPx() }
        var cardH by remember { mutableFloatStateOf(0f) }
        val above = icon.top - gap - cardH > topLimit
        val x = (icon.center.x - cardWpx / 2).coerceIn(margin, sw - cardWpx - margin)
        val y = if (above) icon.top - gap - cardH else minOf(icon.bottom + gap, sh - cardH - margin)
        val shape = RoundedCornerShape(32.dp)
        val frosted = st.glass
        Column(
            Modifier
                .offset { IntOffset(x.toInt(), y.toInt()) }
                .width(cardW)
                .onSizeChanged { cardH = it.height.toFloat() }
                .graphicsLayer {
                    val e = progress()
                    alpha = e.coerceIn(0f, 1f)
                    val k = 0.82f + 0.18f * e
                    scaleX = k; scaleY = k
                    transformOrigin = TransformOrigin(((icon.center.x - x) / cardWpx).coerceIn(0f, 1f), if (above) 1f else 0f)
                }
                .clip(shape)
                .drawBehind {
                    // The wallpaper, frosted, lined up with where it really is behind the card.
                    if (frosted != null) {
                        val k = maxOf(sw / frosted.width, sh / frosted.height)
                        val dw = frosted.width * k
                        val dh = frosted.height * k
                        drawImage(
                            frosted,
                            dstOffset = IntOffset(((sw - dw) / 2 - x).toInt(), ((sh - dh) / 2 - y).toInt()),
                            dstSize = IntSize(dw.toInt(), dh.toInt()),
                            filterQuality = FilterQuality.High,
                        )
                    }
                }
                .background(if (frosted != null) pal.l0.copy(alpha = 0.30f) else pal.l1.copy(alpha = 0.92f))
                .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(bottom = 14.dp),
        ) {
            FolderHeader(st, folder, adding, full, onAdd = { adding = !adding; full = false }, onFull = { full = !full; adding = false })
            when {
                adding -> FolderPicker(st, ui, folder)
                full -> LazyVerticalGrid(
                    GridCells.Fixed(4),
                    Modifier.heightIn(max = 380.dp).padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) { items(folder.apps, key = { it }) { FolderTile(st, ui, folder, it, badged) } }
                else -> {
                    val pages = folder.apps.chunked(4)
                    val pager = rememberPagerState(pageCount = { pages.size })
                    HorizontalPager(pager, Modifier.fillMaxWidth().padding(horizontal = 4.dp)) { i ->
                        Row(Modifier.fillMaxWidth()) {
                            pages[i].forEach { key -> Box(Modifier.weight(1f)) { FolderTile(st, ui, folder, key, badged) } }
                            repeat(4 - pages[i].size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    if (pages.size > 1) Row(
                        Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        repeat(pages.size) { i ->
                            val on = i == pager.currentPage
                            Box(Modifier.size(if (on) 18.dp else 6.dp, 6.dp).clip(RoundedCornerShape(3.dp)).background(if (on) pal.acc else pal.sub.copy(alpha = 0.6f)))
                        }
                    }
                }
            }
        }
    }
}

/** The name (tap to rename), the count, and the joined ＋ ↗ pill. */
@Composable
private fun FolderHeader(st: HomeState, folder: HomeItem, adding: Boolean, full: Boolean, onAdd: () -> Unit, onFull: () -> Unit) {
    val pal = LocalPal.current
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    val focus = remember { FocusRequester() }
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 16.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            name, { name = it.take(24); st.renameFolder(folder.id, name) },
            Modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Min).widthIn(min = 40.dp, max = 200.dp).focusRequester(focus),
            singleLine = true,
            textStyle = TextStyle(color = pal.fg, fontSize = 24.sp, fontFamily = TitleFont, fontWeight = FontWeight.Black),
            cursorBrush = SolidColor(pal.acc),
            decorationBox = { inner -> Box { if (name.isEmpty()) Title("Folder", size = 24.sp, color = pal.dim); inner() } },
        )
        Spacer(Modifier.width(8.dp))
        Lbl("${folder.apps.size}", size = 13.sp, color = pal.acc)
        Spacer(Modifier.width(6.dp))
        Glyph("edit", Modifier.clip(CircleShape).clickable { runCatching { focus.requestFocus() } }.padding(4.dp), size = 15.dp, color = pal.sub)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            PillPart(if (adding) "check" else "add", corners(14.dp, 5.dp, 5.dp, 14.dp), pal.acc, onAdd)
            PillPart(if (full) "close_fullscreen" else "open_in_full", corners(5.dp, 14.dp, 14.dp, 5.dp), pal.fg, onFull)
        }
    }
}

@Composable
private fun PillPart(glyph: String, shape: androidx.compose.ui.graphics.Shape, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp, 28.dp).clip(shape).background(Color.White.copy(alpha = 0.12f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Glyph(glyph, size = 16.dp, color = tint) }
}

/** One app in a folder: tap to open, hold for its menu or to drag it out. */
@Composable
private fun FolderTile(st: HomeState, ui: Ui, folder: HomeItem, key: String, badged: Set<String>) {
    val ctx = LocalContext.current
    val app = st.app(key)
    var rect by remember { mutableStateOf(Rect.Zero) }
    Column(
        Modifier.fillMaxWidth()
            .onGloballyPositioned { rect = it.boundsInRoot() }
            .clip(RoundedCornerShape(Rad.container))
            .pressScale()
            .holdable(
                onTap = { if (app != null) { Actions.open(ctx, st, app); ui.close() } },
                onHold = { pos ->
                    ui.hold = Hold(DragSrc(appKey = key, folderId = folder.id), pos)
                    if (app != null) ui.showMenu(app, rect, above = false, fromDock = false, folderId = folder.id)
                },
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            if (app != null) GlassIcon(app, 56.dp) else AppIcon(null, 56.dp)
            if (app != null && app.pkg in badged) NoteBar()
        }
        Spacer(Modifier.height(10.dp))
        Lbl(app?.label ?: "Missing", Modifier.padding(horizontal = 2.dp), size = 12.sp, align = TextAlign.Center)
    }
}

/** Pick what's in the folder: every app, ticked if it's in. */
@Composable
private fun FolderPicker(st: HomeState, ui: Ui, folder: HomeItem) {
    val pal = LocalPal.current
    var q by remember { mutableStateOf("") }
    val apps = remember(st.apps, st.hidden, q) {
        st.apps.filter { it.key !in st.hidden && (q.isBlank() || it.label.contains(q.trim(), ignoreCase = true)) }
            .sortedBy { it.label.lowercase() }
    }
    Column(Modifier.padding(horizontal = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(21.dp)).background(Color.White.copy(alpha = 0.10f)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph("search", size = 18.dp, color = pal.acc)
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                q, { q = it }, Modifier.weight(1f), singleLine = true,
                textStyle = TextStyle(color = pal.fg, fontSize = 14.sp, fontFamily = Sans),
                cursorBrush = SolidColor(pal.acc),
                decorationBox = { inner -> Box { if (q.isEmpty()) Lbl("Find an app to add", size = 14.sp, color = pal.sub); inner() } },
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(apps, key = { it.key }) { app ->
                val inside = app.key in folder.apps
                Row(
                    Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (inside) pal.acc.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable {
                            if (inside) {
                                // A folder needs two apps; taking out the second-last turns it back into an app.
                                if (folder.apps.size <= 2) ui.close()
                                st.removeFromFolder(folder.id, app.key)
                            } else st.mergeInto(folder, app.key, null)
                        }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassIcon(app, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Lbl(app.label, Modifier.weight(1f))
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).background(if (inside) pal.acc else Color.White.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) { if (inside) Glyph("check", size = 15.dp, color = pal.ink) }
                }
            }
        }
    }
}

/** Rename an app: empty puts its own name back. */
@Composable
fun RenamePanel(st: HomeState, ui: Ui, modifier: Modifier) {
    val pal = LocalPal.current
    val app = st.app(ui.renameKey) ?: return
    var text by remember(ui.openCount) { mutableStateOf(app.label) }
    val big = Rad.normal
    val j = Rad.joined
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Piece(Modifier.fillMaxWidth().height(60.dp), corners(big, big, j, j), edge = true) {
            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app, 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Title("Rename", size = 16.sp)
                    Lbl("Its own name is ${app.original}", size = 12.sp, color = pal.sub)
                }
            }
        }
        Piece(Modifier.fillMaxWidth().height(58.dp), RoundedCornerShape(j), edge = true) {
            Box(
                Modifier.fillMaxSize().padding(10.dp).clip(RoundedCornerShape(Rad.segment)).background(pal.l5).padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    text, { text = it.take(30) }, Modifier.fillMaxWidth(), singleLine = true,
                    textStyle = TextStyle(color = pal.fg, fontSize = 15.sp, fontFamily = Sans),
                    cursorBrush = SolidColor(pal.acc),
                )
            }
        }
        Piece(Modifier.fillMaxWidth().height(56.dp), corners(j, j, big, big), edge = true) {
            Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                PlainButton("Reset") { st.rename(app.key, null); ui.close() }
                Spacer(Modifier.weight(1f))
                PlainButton("Cancel") { ui.close() }
                Spacer(Modifier.width(6.dp))
                AccentButton("Save") { st.rename(app.key, text.takeIf { it != app.original }); ui.close() }
            }
        }
    }
}
