package dev.ninomae.home

import android.content.pm.ApplicationInfo
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** A group in the rail, made from Android's own app categories. */
private data class Group(val id: String, val name: String, val glyph: String, val cats: Set<Int>)

private val GROUPS = listOf(
    Group("g-social", "Social", "forum", setOf(ApplicationInfo.CATEGORY_SOCIAL)),
    Group("g-media", "Media", "play_circle", setOf(ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_IMAGE)),
    Group("g-games", "Games", "sports_esports", setOf(ApplicationInfo.CATEGORY_GAME)),
    Group("g-news", "News", "newspaper", setOf(ApplicationInfo.CATEGORY_NEWS)),
    Group("g-maps", "Maps", "map", setOf(ApplicationInfo.CATEGORY_MAPS)),
    Group("g-work", "Work", "work", setOf(ApplicationInfo.CATEGORY_PRODUCTIVITY)),
)

private data class RailItem(val id: String, val glyph: String)

/**
 * All apps (B2 · Glass): search across the top, focused as it opens so you can type straight away;
 * a slim capsule rail of views (most used, recent, A–Z, then groups) on the left; the apps beside it.
 * While you type, the rail picks what kind of result to show. Swipe down (from the top of the list,
 * or anywhere on the search bar or rail) to close it.
 *
 * How far it's raised (ui.sheet) is only read inside graphicsLayer, so opening and closing
 * redraw it without recomposing it.
 */
@Composable
fun Sheet(st: HomeState, ui: Ui, compact: Boolean, modifier: Modifier) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    var view by remember { mutableStateOf("most") }
    var kind by remember { mutableStateOf("s-all") }
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focus = remember { FocusRequester() }
    val notes by NotifService.notes.collectAsState()
    val badged = remember(notes) { notes.map { it.pkg }.toSet() }
    val cols = if (compact) 4 else 6
    val iconSize = if (compact) 52.dp else 56.dp

    // Opening focuses search (keyboard up); closing clears it and goes back to Most used.
    LaunchedEffect(ui.inApps) {
        if (ui.inApps) {
            runCatching { focus.requestFocus() }
            keyboard?.show()
        } else {
            q = ""
            view = "most"
            kind = "s-all"
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
    LaunchedEffect(view, q.isBlank()) { grid.scrollToItem(0) }
    // A deleted (or cancelled) tag: back to Most used.
    LaunchedEffect(st.tags, view) { if (view.startsWith("t-") && st.tags.none { "t-${it.id}" == view }) view = "most" }

    val visible = remember(st.apps, st.hidden) { st.apps.filter { it.key !in st.hidden } }
    val searching = q.isNotBlank()
    val shown = remember(visible, q) {
        if (q.isBlank()) visible else {
            val t = q.trim()
            visible.filter { it.label.contains(t, ignoreCase = true) || initialsOf(it.label).startsWith(t, ignoreCase = true) }
                .sortedByDescending { it.label.startsWith(t, ignoreCase = true) }
        }
    }
    val keys = remember(visible) { visible.associateBy { it.key } }
    // Most used first, topped up with recent apps until there are three full rows.
    val most = remember(visible, st.launches, st.recent, cols) {
        (visible.filter { (st.launches[it.key] ?: 0) > 0 }.sortedByDescending { st.launches[it.key] ?: 0 } +
            st.recent.mapNotNull { keys[it] }).distinctBy { it.key }.take(cols * 3)
    }
    val recent = remember(st.recent, keys) { st.recent.mapNotNull { keys[it] } }
    val groups = remember(visible) { GROUPS.map { g -> g to visible.filter { it.category in g.cats } }.filter { it.second.isNotEmpty() } }
    // Your own tags (T1), each with its apps A–Z.
    val tagged = remember(st.tags, keys) { st.tags.map { t -> t to t.apps.mapNotNull { keys[it] }.sortedBy { it.label.lowercase() } } }
    val calc = remember(q) { Search.calc(q) ?: Search.convert(q) }
    val settingsHits = remember(q) { Search.settings(q) }
    val contacts by androidx.compose.runtime.produceState(emptyList<Answer.Contact>(), q, ui.permissionTick) {
        val t = q.trim()
        value = if (t.length < 2) emptyList() else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Search.contacts(ctx, t) }
    }
    // Calendar events and files (files need All files access), looked up as you type.
    val stamp = Glance.stamp
    val events by androidx.compose.runtime.produceState(emptyList<Answer.Event>(), q, ui.permissionTick, stamp) {
        val t = q.trim()
        value = if (t.length < 2 || !st.searchCalendar || !Search.canReadCalendar(ctx)) emptyList()
        else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Search.events(ctx, t) }
    }
    val files by androidx.compose.runtime.produceState(emptyList<Answer.File>(), q, stamp) {
        val t = q.trim()
        value = if (t.length < 2 || !st.searchFiles || !Search.canReadFiles()) emptyList()
        else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Search.files(ctx, t) }
    }
    val byLetter = remember(visible) { visible.groupBy { letterOf(it.label) }.toSortedMap() }
    var scrubLetter by remember { mutableStateOf<Char?>(null) }
    var scrubY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    val rail = if (searching) listOf(
        RailItem("s-all", "apps"), RailItem("s-people", "person"), RailItem("s-calendar", "event"), RailItem("s-files", "description"),
        RailItem("s-settings", "settings"), RailItem("s-web", "travel_explore"),
    ) else listOf(RailItem("most", "bolt"), RailItem("recent", "history"), RailItem("all", "sort_by_alpha"), RailItem("sep-tags", "")) +
        tagged.map { (t, _) -> RailItem("t-${t.id}", t.glyph) } + RailItem("t-new", "add") + RailItem("sep-groups", "") +
        groups.map { (g, _) -> RailItem(g.id, g.glyph) }
    val chosen = if (searching) kind else view
    val empty = if (!searching) emptySet() else buildSet {
        if (shown.isEmpty() && calc == null) add("s-all")
        if (contacts.isEmpty()) add("s-people")
        if (events.isEmpty()) add("s-calendar")
        if (files.isEmpty()) add("s-files")
        if (settingsHits.isEmpty()) add("s-settings")
    }
    val travel = ui.screenHpx * 0.5f
    val closer = remember(ui) { SheetCloser(ui) }
    closer.travel = travel
    fun open(app: AppEntry) { Actions.open(ctx, st, app); ui.close() }

    Box(modifier) {
        GlassBackdrop(st) { ui.sheet }
        Column(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    // One simple motion, a beat behind the glass: fade in, rise a little, settle to full size.
                    val e = FastOutSlowInEasing.transform(((ui.sheet - 0.12f) / 0.88f).coerceIn(0f, 1f))
                    alpha = e
                    translationY = (1f - e) * 40.dp.toPx()
                    val k = 0.97f + 0.03f * e
                    scaleX = k; scaleY = k
                }
                .nestedScroll(closer)
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
        ) {
            // A faint handle: drag down here to close.
            Box(Modifier.fillMaxWidth().height(22.dp).sheetDrag(ui, travel), contentAlignment = Alignment.Center) {
                Box(Modifier.size(40.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(pal.fg.copy(alpha = 0.28f)))
            }

            // ---------- search, with a soft glow while focused ----------
            Box(
                Modifier.sheetDrag(ui, travel).padding(horizontal = 10.dp).fillMaxWidth().height(66.dp)
                    .clip(RoundedCornerShape(33.dp)).background(pal.acc.copy(alpha = 0.10f)).padding(6.dp),
            ) {
                Row(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(27.dp)).background(glass(0.12f))
                        .border(2.dp, pal.acc, RoundedCornerShape(27.dp)).padding(start = 18.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph("search", size = 23.dp, color = pal.acc)
                    Spacer(Modifier.width(12.dp))
                    BasicTextField(
                        q, { q = it; if (it.isBlank()) kind = "s-all" },
                        Modifier.weight(1f).focusRequester(focus),
                        singleLine = true,
                        textStyle = TextStyle(color = pal.fg, fontSize = 17.sp, fontFamily = Sans),
                        cursorBrush = SolidColor(pal.acc),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { shown.firstOrNull()?.let { open(it) } }),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (q.isEmpty()) Lbl("Search apps, people, settings", size = 16.sp, color = pal.sub)
                                inner()
                            }
                        },
                    )
                    if (searching) GlassButton("close") { q = ""; kind = "s-all" }
                    else GlassButton("tune") { ui.toggle(Layer.Settings) }
                }
            }
            Spacer(Modifier.height(10.dp))

            Row(Modifier.weight(1f).fillMaxWidth().padding(start = 12.dp)) {
                // ---------- slim capsule rail ----------
                Column(
                    Modifier.width(if (compact) 54.dp else 58.dp).verticalScroll(rememberScrollState())
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(29.dp)).background(glass(0.10f))
                        .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(29.dp)).padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    rail.forEach { r ->
                        if (r.id.startsWith("sep")) {
                            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(22.dp, 1.dp).background(Color.White.copy(alpha = 0.18f)))
                            }
                        } else RailButton(r.glyph, r.id == chosen, r.id in empty) {
                            if (r.id == "t-new") { val id = st.newTag(); view = "t-$id"; ui.editTag(id, fresh = true) }
                            else if (!searching) view = r.id
                            else if (r.id == "s-web") { Actions.webSearch(ctx, q.trim()); ui.close() }
                            else kind = r.id
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))

                // ---------- the apps ----------
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    val scrubbing = !searching && view == "all" && byLetter.size > 3
                    LazyVerticalGrid(
                        GridCells.Fixed(cols),
                        state = grid,
                        contentPadding = PaddingValues(end = if (scrubbing) 34.dp else 12.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        if (!searching) when (view) {
                            "most" -> {
                                heading("Most used", count = most.size)
                                tiles("m", most) { GlassTile(st, ui, it, iconSize, badged) }
                                item(span = { GridItemSpan(maxLineSpan) }, key = "pairs-h") {
                                    Heading("Pairs", "open side by side", Modifier.padding(top = 10.dp))
                                }
                                item(span = { GridItemSpan(maxLineSpan) }, key = "pairs") { PairsRow(st, ui, compact) }
                            }
                            "recent" -> {
                                heading("Recent", "last opened first")
                                if (recent.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }, key = "r-none") {
                                    Lbl("Apps you open show up here", Modifier.padding(4.dp), color = pal.sub)
                                }
                                tiles("r", recent) { GlassTile(st, ui, it, iconSize, badged) }
                            }
                            "all" -> {
                                heading("All apps", count = visible.size)
                                byLetter.forEach { (c, apps) ->
                                    item(span = { GridItemSpan(maxLineSpan) }, key = "l$c") {
                                        Title(c.toString(), Modifier.padding(start = 4.dp, top = 4.dp), size = 18.sp, color = pal.acc)
                                    }
                                    tiles("a$c", apps) { GlassTile(st, ui, it, iconSize, badged) }
                                }
                            }
                            else -> tagged.firstOrNull { "t-${it.first.id}" == view }?.let { (t, apps) ->
                                item(span = { GridItemSpan(maxLineSpan) }, key = "h-tag") {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Heading(t.name, if (apps.size == 1) "your tag · 1 app" else "your tag · ${apps.size} apps", Modifier.weight(1f))
                                        PlainButton("Edit tag", glyph = "edit", height = 32.dp) { ui.editTag(t.id) }
                                    }
                                }
                                if (apps.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }, key = "t-none") {
                                    Lbl("Hold an app and choose Tags to put it here", Modifier.padding(4.dp), color = pal.sub)
                                }
                                tiles("t", apps) { GlassTile(st, ui, it, iconSize, badged) }
                            } ?: groups.firstOrNull { it.first.id == view }?.let { (g, apps) ->
                                heading(g.name, count = apps.size)
                                tiles("g", apps.sortedBy { it.label.lowercase() }) { GlassTile(st, ui, it, iconSize, badged) }
                            }
                        } else {
                            val apps = kind == "s-all"
                            val top = shown.firstOrNull()
                            if (apps && calc != null) item(span = { GridItemSpan(maxLineSpan) }, key = "calc") { CalcCard(calc) }
                            if (apps && top != null) {
                                item(span = { GridItemSpan(maxLineSpan) }, key = "top") { TopHit(st, ui, top) { open(top) } }
                                tiles("s", shown.drop(1)) { GlassTile(st, ui, it, iconSize, badged) }
                            }
                            item(span = { GridItemSpan(maxLineSpan) }, key = "rows") {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val people = kind == "s-all" || kind == "s-people"
                                    val cal = kind == "s-all" || kind == "s-calendar"
                                    val fil = kind == "s-all" || kind == "s-files"
                                    val sets = kind == "s-all" || kind == "s-settings"
                                    val nothing = (!apps || shown.isEmpty()) && calc == null &&
                                        (!people || contacts.isEmpty()) && (!cal || events.isEmpty()) && (!fil || files.isEmpty()) &&
                                        (!sets || settingsHits.isEmpty())
                                    if (nothing) Lbl("Nothing matches “${q.trim()}”", Modifier.fillMaxWidth().padding(12.dp), color = pal.sub, align = TextAlign.Center)
                                    if (people) {
                                        if (contacts.isNotEmpty()) {
                                            contacts.forEachIndexed { i, c -> ContactRow(c, i == 0, i == contacts.lastIndex) { ui.close() } }
                                        } else if (!Search.canReadContacts(ctx) && q.trim().length >= 2 && st.searchContacts) {
                                            ResultRow("person_search", "Search your contacts too", null, true, true) {
                                                ui.askPermissions(arrayOf(android.Manifest.permission.READ_CONTACTS))
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                    }
                                    if (cal) {
                                        if (events.isNotEmpty()) {
                                            events.forEachIndexed { i, e ->
                                                ResultRow("event", e.title, e.note, i == 0, i == events.lastIndex) { Search.openEvent(ctx, e); ui.close() }
                                            }
                                            Spacer(Modifier.height(4.dp))
                                        } else if (kind == "s-calendar" && st.searchCalendar && !Search.canReadCalendar(ctx)) {
                                            ResultRow("event", "Search your calendar too", null, true, true) {
                                                ui.askPermissions(arrayOf(android.Manifest.permission.READ_CALENDAR))
                                            }
                                        }
                                    }
                                    if (fil) {
                                        if (files.isNotEmpty()) {
                                            files.forEachIndexed { i, f ->
                                                ResultRow(Search.fileGlyph(f.mime), f.name, f.note, i == 0, i == files.lastIndex) { Search.openFile(ctx, f); ui.close() }
                                            }
                                            Spacer(Modifier.height(4.dp))
                                        } else if (kind == "s-files" && st.searchFiles && !Search.canReadFiles()) {
                                            ResultRow("folder_open", "Search your files too", "Needs All files access", true, true) { Search.allowFiles(ctx) }
                                        }
                                    }
                                    if (sets && settingsHits.isNotEmpty()) {
                                        settingsHits.forEachIndexed { i, h ->
                                            ResultRow(h.glyph, h.name, "Settings", i == 0, i == settingsHits.lastIndex) { Search.openSetting(ctx, h); ui.close() }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                    }
                                    if (kind == "s-all") {
                                        ResultRow("travel_explore", "Search the web for “${q.trim()}”", null, true, false) {
                                            Actions.webSearch(ctx, q.trim()); ui.close()
                                        }
                                        ResultRow("shop", "Look in the Play Store", null, false, true) {
                                            Actions.playStore(ctx, q.trim()); ui.close()
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (scrubbing) {
                        Scrubber(
                            byLetter.keys.toList(),
                            Modifier.align(Alignment.CenterEnd).padding(end = 6.dp),
                            onEnd = { scrubLetter = null },
                        ) { c, y ->
                            scrubLetter = c
                            scrubY = y
                            // heading, then per letter: its header + its tiles
                            var index = 1
                            for ((k, apps) in byLetter) { if (k == c) break; index += 1 + apps.size }
                            scope.launch { grid.scrollToItem(index) }
                        }
                    }
                    // The letter under your thumb, big, beside the scrubber.
                    scrubLetter?.let { c ->
                        Box(
                            Modifier.align(Alignment.TopEnd)
                                .offset { IntOffset(0, (scrubY - with(density) { 32.dp.toPx() }).toInt()) }
                                .padding(end = 38.dp).size(64.dp).clip(RoundedCornerShape(22.dp)).background(pal.acc),
                            contentAlignment = Alignment.Center,
                        ) { Title(c.toString(), size = 30.sp, color = pal.ink) }
                    }
                }
            }
        }
    }
}

private fun LazyGridScope.heading(title: String, note: String = "", count: Int? = null) {
    item(span = { GridItemSpan(maxLineSpan) }, key = "h-$title") { Heading(title, note, count = count) }
}

private fun LazyGridScope.tiles(prefix: String, apps: List<AppEntry>, tile: @Composable (AppEntry) -> Unit) {
    itemsIndexed(apps, key = { _, a -> prefix + a.key }) { _, a -> tile(a) }
}

@Composable
private fun Heading(title: String, note: String, modifier: Modifier = Modifier, count: Int? = null) {
    val pal = LocalPal.current
    Row(modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Title(title, size = 22.sp)
        Spacer(Modifier.width(10.dp))
        // A count in a small chip, or a short note in the brighter secondary colour.
        if (count != null) Box(
            Modifier.height(22.dp).widthIn(min = 28.dp).clip(RoundedCornerShape(11.dp)).background(glass(0.12f)).padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) { Lbl("$count", size = 12.sp, color = pal.acc, weight = FontWeight.SemiBold) }
        else if (note.isNotEmpty()) Lbl(note, Modifier.padding(top = 3.dp), size = 13.5.sp, color = pal.sub)
    }
}

/** A circle in the rail; the chosen one is accent with a soft glow round it. */
@Composable
private fun RailButton(glyph: String, on: Boolean, empty: Boolean, onClick: () -> Unit) {
    val pal = LocalPal.current
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f)
            .drawBehind { if (on) drawCircle(pal.acc.copy(alpha = 0.22f), radius = size.minDimension / 2f + 4.dp.toPx()) }
            .clip(CircleShape).background(if (on) pal.acc else Color.Transparent)
            .pressScale().clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Glyph(glyph, size = 22.dp, color = if (on) pal.ink else pal.fg.copy(alpha = if (empty) 0.3f else 0.9f), fill = on) }
}

/** Icon and name. Hold for the menu, then drag it out to the home screen. */
@Composable
private fun GlassTile(st: HomeState, ui: Ui, app: AppEntry, size: Dp, badged: Set<String>) {
    val ctx = LocalContext.current
    var rect by remember { mutableStateOf(Rect.Zero) }
    Column(
        Modifier
            .fillMaxWidth()
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
            GlassIcon(app, size)
            if (st.dots && app.pkg in badged) NoteBar()
        }
        Spacer(Modifier.height(10.dp))
        Lbl(app.label, Modifier.padding(horizontal = 2.dp), size = 12.5.sp, weight = FontWeight.Medium, align = TextAlign.Center)
    }
}

/** The app Enter would open, big, washed in the accent. */
@Composable
private fun TopHit(st: HomeState, ui: Ui, app: AppEntry, onOpen: () -> Unit) {
    val pal = LocalPal.current
    var rect by remember { mutableStateOf(Rect.Zero) }
    val shape = corners(24.dp, 24.dp, 6.dp, 6.dp)
    val note = (GROUPS.firstOrNull { app.category in it.cats }?.name ?: "App") +
        ((st.launches[app.key] ?: 0).takeIf { it > 0 }?.let { " · $it ${if (it == 1) "open" else "opens"}" } ?: "")
    Box(
        Modifier.fillMaxWidth().height(96.dp)
            .onGloballyPositioned { rect = it.boundsInRoot() }
            .clip(shape)
            .background(glass(0.07f))
            .background(Brush.horizontalGradient(0f to pal.acc.copy(alpha = 0.24f), 0.8f to Color.Transparent))
            .border(1.dp, glassEdge(), shape)
            .holdable(
                onTap = onOpen,
                onHold = { pos ->
                    ui.hold = Hold(DragSrc(appKey = app.key), pos)
                    ui.showMenu(app, rect, above = false, fromDock = false)
                },
            ),
    ) {
        Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIcon(app, 62.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Title(app.label, size = 22.sp)
                Lbl(note, size = 12.sp, color = pal.sub)
            }
            Row(
                Modifier.height(42.dp).clip(RoundedCornerShape(21.dp)).background(pal.acc).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Lbl("Open", size = 14.sp, color = pal.ink, weight = FontWeight.SemiBold)
                Spacer(Modifier.width(6.dp))
                Glyph("keyboard_return", size = 18.dp, color = pal.ink)
            }
        }
    }
}

/** Your pairs as tiny screens split in two, and a card to make a new one. */
@Composable
private fun PairsRow(st: HomeState, ui: Ui, compact: Boolean) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val w = if (compact) 128.dp else 150.dp
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        st.pairs.forEach { pr ->
            var rect by remember { mutableStateOf(Rect.Zero) }
            Column(
                Modifier.width(w).height(104.dp)
                    .onGloballyPositioned { rect = it.boundsInRoot() }
                    .clip(RoundedCornerShape(22.dp)).background(glass(0.08f)).border(1.dp, glassEdge(), RoundedCornerShape(22.dp))
                    .pressScale(0.97f)
                    .holdable(onTap = { ui.close(); Actions.openPair(ctx, st, pr) }, onHold = { ui.showPairMenu(pr, rect, above = true) })
                    .padding(10.dp),
            ) {
                Row(Modifier.fillMaxWidth().height(58.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(pr.left, pr.right).forEachIndexed { i, k ->
                        Box(
                            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(14.dp))
                                .background(pal.acc.copy(alpha = if (i == 0) 0.16f else 0.10f)),
                            contentAlignment = Alignment.Center,
                        ) { st.app(k)?.let { GlassIcon(it, 32.dp) } }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Lbl(pr.name, Modifier.padding(start = 2.dp), size = 13.sp, weight = FontWeight.Medium)
            }
        }
        Column(
            Modifier.width(if (st.pairs.isEmpty()) w else 88.dp).height(104.dp)
                .clip(RoundedCornerShape(22.dp)).border(1.5.dp, glassEdge(), RoundedCornerShape(22.dp))
                .clickable { ui.newPair() },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Glyph("add", size = 24.dp, color = pal.acc)
            Spacer(Modifier.height(4.dp))
            Lbl("New pair", size = 12.sp, color = pal.sub)
        }
    }
}

/** Letters down the edge on a soft track: tap or slide to jump; reports where the finger is for the bubble. */
@Composable
private fun Scrubber(letters: List<Char>, modifier: Modifier, onEnd: () -> Unit, onLetter: (Char, Float) -> Unit) {
    val pal = LocalPal.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    var current by remember { mutableIntStateOf(-1) }
    var h by remember { mutableStateOf(1f) }
    var top by remember { mutableFloatStateOf(0f) }
    fun pick(y: Float) {
        val i = (y / h * letters.size).toInt().coerceIn(0, letters.lastIndex)
        if (i != current) {
            current = i
            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
        }
        onLetter(letters[i], top + y)
    }
    Column(
        modifier
            .width(22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(glass(if (current >= 0) 0.14f else 0.07f))
            .onGloballyPositioned { h = it.size.height.toFloat(); top = it.positionInParent().y }
            .pointerInput(letters) { detectTapGestures(onPress = { pick(it.y); tryAwaitRelease(); current = -1; onEnd() }) }
            .pointerInput(letters) {
                detectVerticalDragGestures(onDragEnd = { current = -1; onEnd() }, onDragCancel = { current = -1; onEnd() }) { c, _ -> c.consume(); pick(c.position.y) }
            }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEachIndexed { i, c ->
            Lbl(
                c.toString(),
                Modifier.padding(vertical = 1.5.dp),
                size = if (i == current) 13.sp else 10.sp,
                color = if (i == current) pal.acc else pal.sub,
                family = NumFont,
                align = TextAlign.Center,
            )
        }
    }
}

/** One joined glass row of results: a round accent badge, the name, a note, and an arrow out. */
@Composable
private fun ResultRow(glyph: String, text: String, note: String?, first: Boolean, last: Boolean, onClick: () -> Unit) {
    val pal = LocalPal.current
    val t = if (first) 22.dp else 6.dp
    val b = if (last) 22.dp else 6.dp
    val shape = corners(t, t, b, b)
    Row(
        Modifier.fillMaxWidth().height(58.dp).clip(shape).background(glass(0.07f)).border(1.dp, glassEdge(), shape)
            .clickable(onClick = onClick).padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(pal.acc.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Glyph(glyph, size = 20.dp, color = pal.acc)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Lbl(text, size = 15.sp)
            note?.let { Lbl(it, size = 12.sp, color = pal.sub) }
        }
        Glyph("north_east", size = 18.dp, color = pal.dim)
    }
}

/** Swipe down on the handle, search bar or rail to close; let go and it settles open or shut. */
fun Modifier.sheetDrag(ui: Ui, travel: Float): Modifier = pointerInput(travel) {
    val vt = androidx.compose.ui.input.pointer.util.VelocityTracker()
    detectVerticalDragGestures(
        onDragStart = { vt.resetTracking(); ui.gesturing = true },
        onDragEnd = { ui.settleSheet(vt.calculateVelocity().y) },
        onDragCancel = { ui.settleSheet(0f) },
    ) { c, dy ->
        c.consume()
        vt.addPosition(c.uptimeMillis, c.position)
        ui.sheet = (ui.sheet - dy / travel).coerceIn(0f, 1f)
    }
}

/**
 * Swipe down on the list once it's at the top (or on the rail) and the sheet follows your finger down;
 * swipe back up and it opens again before the list scrolls. Letting go settles it by speed or distance.
 */
private class SheetCloser(private val ui: Ui) : NestedScrollConnection {
    var travel = 1f

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (ui.gesturing && available.y < 0 && ui.sheet < 1f) {
            ui.sheet = (ui.sheet - available.y / travel).coerceIn(0f, 1f)
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.y > 0f) {
            ui.gesturing = true
            ui.sheet = (ui.sheet - available.y / travel).coerceIn(0f, 1f)
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (!ui.gesturing) return Velocity.Zero
        ui.settleSheet(available.y)
        return available
    }
}

private fun letterOf(label: String): Char = label.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.let { if (it.isDigit()) '#' else it } ?: '#'
private fun initialsOf(s: String) = s.split(' ', '-', '_').filter { it.isNotEmpty() }.joinToString("") { it.take(1) }

/** The answer to a sum or a conversion, big, tap to copy. */
@Composable
private fun CalcCard(a: Answer) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val (top, result) = when (a) {
        is Answer.Calc -> a.expr to a.value
        is Answer.Convert -> a.from to a.to
        else -> return
    }
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(glass(0.07f))
            .background(Brush.horizontalGradient(0f to pal.acc.copy(alpha = 0.20f), 0.8f to Color.Transparent))
            .border(1.dp, glassEdge(), shape)
            .clickable { Search.copy(ctx, result.substringBefore(' ')) }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Lbl(top, size = 13.sp, color = pal.sub)
            Title("= $result", size = 26.sp)
        }
        Glyph("content_copy", size = 18.dp, color = pal.sub)
    }
}

@Composable
private fun ContactRow(c: Answer.Contact, first: Boolean, last: Boolean, onDone: () -> Unit) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val t = if (first) 22.dp else 6.dp
    val b = if (last) 22.dp else 6.dp
    val shape: Shape = corners(t, t, b, b)
    Row(
        Modifier.fillMaxWidth().height(60.dp).clip(shape).background(glass(0.07f)).border(1.dp, glassEdge(), shape)
            .clickable { Search.openContact(ctx, c); onDone() }.padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(pal.acc.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Title(c.name.take(1).uppercase(), size = 16.sp, color = pal.acc)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Lbl(c.name, size = 15.sp, weight = FontWeight.SemiBold)
            c.number?.let { Lbl(it, size = 12.sp, color = pal.sub) }
        }
        if (c.number != null) {
            IconButton("chat", 38.dp, pal.fg) { Search.text(ctx, c); onDone() }
            IconButton("call", 38.dp, pal.acc) { Search.call(ctx, c); onDone() }
        }
    }
}
