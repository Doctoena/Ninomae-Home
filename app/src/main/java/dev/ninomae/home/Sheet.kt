package dev.ninomae.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private sealed interface Row2 {
    data class Header(val text: String) : Row2
    data class App(val app: AppEntry) : Row2
}

/**
 * All apps as a sheet from the bottom: suggested, pairs, A–Z with a letter scrubber,
 * and the search field at the bottom under the thumb. [p] is how far it's raised.
 */
@Composable
fun Sheet(st: HomeState, ui: Ui, p: Float, compact: Boolean, sheetHeightPx: Float, modifier: Modifier) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    var games by remember { mutableStateOf(false) }
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val notes by NotifService.notes.collectAsState()
    val badged = remember(notes) { notes.map { it.pkg }.toSet() }
    val cols = if (compact) 4 else 6

    // Opening for a search (the home search pill) puts the cursor in the field.
    LaunchedEffect(ui.layer, ui.searchFocus) {
        if (ui.layer == Layer.Drawer && ui.searchFocus) {
            runCatching { focus.requestFocus() }
            keyboard?.show()
            ui.searchFocus = false
        }
        if (ui.layer != Layer.Drawer) {
            q = ""
            keyboard?.hide()
        }
    }

    val visible = remember(st.apps, st.hidden) { st.apps.filter { it.key !in st.hidden } }
    val shown = remember(visible, q, games) {
        val base = if (games) visible.filter { it.isGame } else visible
        if (q.isBlank()) base else {
            val t = q.trim()
            base.filter { it.label.contains(t, ignoreCase = true) || initialsOf(it.label).startsWith(t, ignoreCase = true) }
                .sortedByDescending { it.label.startsWith(t, ignoreCase = true) }
        }
    }
    val suggested = remember(visible, st.launches) {
        visible.filter { (st.launches[it.key] ?: 0) > 0 }.sortedByDescending { st.launches[it.key] ?: 0 }.take(cols)
    }
    // A–Z sections, and where each letter starts in the grid for the scrubber.
    val (rows, letterIndex) = remember(shown, q) {
        val out = mutableListOf<Row2>()
        val index = LinkedHashMap<Char, Int>()
        if (q.isBlank()) {
            shown.groupBy { letterOf(it.label) }.toSortedMap().forEach { (c, list) ->
                index[c] = out.size
                out += Row2.Header(c.toString())
                list.forEach { out += Row2.App(it) }
            }
        } else shown.forEach { out += Row2.App(it) }
        out to index
    }
    val searching = q.isNotBlank()

    Piece(
        modifier.graphicsLayer { translationY = (1f - p) * sheetHeightPx },
        corners(28.dp, 28.dp, 0.dp, 0.dp),
        pal.l1,
        edge = true,
    ) {
        Column(Modifier.fillMaxSize()) {
            // handle: drag it down to close
            Box(
                Modifier.fillMaxWidth().height(26.dp).sheetDrag(ui, sheetHeightPx),
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(44.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(pal.fg.copy(alpha = 0.45f))) }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyVerticalGrid(
                    GridCells.Fixed(cols),
                    state = grid,
                    contentPadding = PaddingValues(start = 10.dp, end = if (searching) 10.dp else 26.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (!searching) {
                        if (suggested.isNotEmpty() && !games) {
                            header("Suggested", pal.sub, false)
                            items(suggested, key = { "s" + it.key }) { app -> SheetTile(st, ui, app, badged, rowOf(0, p)) }
                        }
                        if (!games) item(span = { GridItemSpan(maxLineSpan) }, key = "pairs") { PairChips(st, ui) }
                    }
                    rows.forEach { r ->
                        when (r) {
                            is Row2.Header -> header(r.text, pal.acc, true)
                            is Row2.App -> item(key = r.app.key) { SheetTile(st, ui, r.app, badged, rowOf(2, p)) }
                        }
                    }
                    if (searching) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "web") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (shown.isEmpty()) Lbl("No apps match “${q.trim()}”", Modifier.fillMaxWidth().padding(12.dp), color = pal.sub, align = TextAlign.Center)
                                SearchOut("travel_explore", "Search the web for “${q.trim()}”", corners(18.dp, 18.dp, 5.dp, 5.dp)) {
                                    Actions.webSearch(ctx, q.trim()); ui.close()
                                }
                                SearchOut("shop", "Look in the Play Store", corners(5.dp, 5.dp, 18.dp, 18.dp)) {
                                    Actions.playStore(ctx, q.trim()); ui.close()
                                }
                            }
                        }
                    }
                }
                if (!searching && letterIndex.size > 3) {
                    Scrubber(
                        letterIndex.keys.toList(),
                        Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
                    ) { c ->
                        val headerPos = letterIndex[c] ?: return@Scrubber
                        // Offset by the suggested + pairs items above the A–Z list.
                        val before = (if (suggested.isNotEmpty() && !games) 1 + suggested.size else 0) + (if (!games) 1 else 0)
                        scope.launch { grid.scrollToItem(before + headerPos) }
                    }
                }
            }

            // search under the thumb, lifted above the keyboard
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)).padding(start = 10.dp, end = 10.dp, bottom = 12.dp)
                    .height(54.dp).clip(RoundedCornerShape(27.dp)).background(pal.l4).padding(start = 18.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph("search", size = 21.dp, color = pal.sub)
                Spacer(Modifier.width(12.dp))
                BasicTextField(
                    q, { q = it }, Modifier.weight(1f).focusRequester(focus),
                    singleLine = true,
                    textStyle = TextStyle(color = pal.fg, fontSize = 16.sp, fontFamily = Sans),
                    cursorBrush = SolidColor(pal.acc),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { shown.firstOrNull()?.let { Actions.open(ctx, st, it); ui.close() } }),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (q.isEmpty()) Lbl("Search apps", size = 16.sp, color = pal.dim)
                            inner()
                        }
                    },
                )
                if (searching) {
                    IconButton("close", 36.dp) { q = "" }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Chip("A–Z", !games) { games = false }
                        if (visible.any { it.isGame }) Chip("Games", games) { games = true }
                    }
                }
            }
        }
    }
}

private fun LazyGridScope.header(text: String, color: androidx.compose.ui.graphics.Color, big: Boolean) {
    item(span = { GridItemSpan(maxLineSpan) }, key = "h$text") {
        if (big) Title(text, Modifier.padding(start = 8.dp, top = 4.dp), size = 15.sp, color = color)
        else Lbl(text, Modifier.padding(start = 8.dp, top = 4.dp), size = 12.sp, color = color)
    }
}

/** Rows rise into place as the sheet comes up, later rows a touch behind. */
private fun rowOf(row: Int, p: Float): Modifier = Modifier.graphicsLayer {
    val local = ((p - row * 0.06f) / (1f - row * 0.06f)).coerceIn(0f, 1f)
    alpha = local
    translationY = (1f - local) * 60f * (1 + row)
}

@Composable
private fun SheetTile(st: HomeState, ui: Ui, app: AppEntry, badged: Set<String>, enter: Modifier) {
    val ctx = LocalContext.current
    var rect by remember { mutableStateOf(Rect.Zero) }
    Column(
        enter
            .onGloballyPositioned { rect = it.boundsInRoot() }
            .clip(RoundedCornerShape(Rad.container))
            .pressScale()
            .holdable(
                onTap = { Actions.open(ctx, st, app); ui.close() },
                onHold = { pos ->
                    ui.hold = Hold(DragSrc(appKey = app.key), pos)
                    ui.showMenu(app, rect, above = rect.center.y > ui.screenHpx / 2, fromDock = false)
                },
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            AppIcon(app, 54.dp)
            if (st.dots && app.pkg in badged) Badge(Modifier.align(Alignment.TopEnd).offset(3.dp, (-3).dp), 13.dp)
        }
        Spacer(Modifier.height(6.dp))
        Lbl(app.label, Modifier.padding(horizontal = 2.dp), size = 12.sp, align = TextAlign.Center)
    }
}

@Composable
private fun PairChips(st: HomeState, ui: Ui) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(start = 4.dp, top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        st.pairs.forEach { pr ->
            var rect by remember { mutableStateOf(Rect.Zero) }
            Row(
                Modifier.height(42.dp).onGloballyPositioned { rect = it.boundsInRoot() }
                    .clip(RoundedCornerShape(15.dp)).background(pal.l4)
                    .holdable(onTap = { ui.close(); Actions.openPair(ctx, st, pr) }, onHold = { ui.showPairMenu(pr, rect, above = true) })
                    .padding(start = 8.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PairIcon(st.app(pr.left), st.app(pr.right), 28.dp)
                Spacer(Modifier.width(8.dp))
                Lbl(pr.name, size = 13.sp, weight = FontWeight.SemiBold)
            }
        }
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(15.dp)).border(1.5.dp, pal.line, RoundedCornerShape(15.dp)).clickable { ui.newPair() },
            contentAlignment = Alignment.Center,
        ) { Glyph("add", size = 20.dp, color = pal.sub) }
    }
}

/** Letters down the edge: tap or slide to jump. */
@Composable
private fun Scrubber(letters: List<Char>, modifier: Modifier, onLetter: (Char) -> Unit) {
    val pal = LocalPal.current
    var current by remember { mutableIntStateOf(-1) }
    var h by remember { mutableStateOf(1f) }
    fun pick(y: Float) {
        val i = (y / h * letters.size).toInt().coerceIn(0, letters.lastIndex)
        if (i != current) {
            current = i
            onLetter(letters[i])
        }
    }
    Column(
        modifier
            .width(20.dp)
            .onGloballyPositioned { h = it.size.height.toFloat() }
            .pointerInput(letters) { detectTapGestures { pick(it.y) } }
            .pointerInput(letters) {
                detectVerticalDragGestures(onDragEnd = { current = -1 }) { c, _ -> c.consume(); pick(c.position.y) }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEachIndexed { i, c ->
            Lbl(
                c.toString(),
                Modifier.padding(vertical = 1.5.dp),
                size = if (i == current) 13.sp else 10.sp,
                color = if (i == current) pal.acc else pal.dim,
                family = NumFont,
                align = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Chip(text: String, chosen: Boolean, onClick: () -> Unit) {
    val pal = LocalPal.current
    Box(
        Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(if (chosen) pal.acc else pal.l5)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Lbl(text, size = 13.sp, color = if (chosen) pal.ink else pal.fg, weight = if (chosen) FontWeight.SemiBold else FontWeight.Normal) }
}

@Composable
private fun SearchOut(glyph: String, text: String, shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit) {
    val pal = LocalPal.current
    Piece(Modifier.fillMaxWidth().height(50.dp), shape, pal.l3, edge = true, onClick = onClick) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Glyph(glyph, size = 19.dp, color = pal.acc)
            Spacer(Modifier.width(12.dp))
            Lbl(text, Modifier.weight(1f))
            Glyph("north_east", size = 16.dp, color = pal.sub)
        }
    }
}

/** Dragging the sheet's handle moves it; let go and it settles open or shut. */
fun Modifier.sheetDrag(ui: Ui, heightPx: Float): Modifier = pointerInput(Unit) {
    val vt = androidx.compose.ui.input.pointer.util.VelocityTracker()
    detectVerticalDragGestures(
        onDragStart = { vt.resetTracking(); ui.gesturing = true },
        onDragEnd = { ui.settleSheet(vt.calculateVelocity().y) },
        onDragCancel = { ui.settleSheet(0f) },
    ) { c, dy ->
        c.consume()
        vt.addPosition(c.uptimeMillis, c.position)
        ui.sheet = (ui.sheet - dy / heightPx).coerceIn(0f, 1f)
    }
}

private fun letterOf(label: String): Char = label.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.let { if (it.isDigit()) '#' else it } ?: '#'
private fun initialsOf(s: String) = s.split(' ', '-', '_').filter { it.isNotEmpty() }.joinToString("") { it.take(1) }
