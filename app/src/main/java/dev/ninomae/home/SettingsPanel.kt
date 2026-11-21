package dev.ninomae.home

import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.zIndex
import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class Category(val id: String, val glyph: String, val title: String)

private val CATS = listOf(
    Category("look", "palette", "Look"),
    Category("home", "grid_view", "Home screen"),
    Category("gestures", "swipe", "Gestures"),
    Category("edge", "view_sidebar", "Edge sidebar"),
    Category("photos", "photo_library", "Photos"),
    Category("search", "search", "Search"),
    Category("glance", "event", "At a glance"),
    Category("bar", "smartphone", "Status bar"),
    Category("hidden", "visibility_off", "Hidden apps"),
    Category("access", "verified_user", "Access"),
    Category("backup", "backup", "Backup"),
)

/** Every setting by name, for search: its page, symbol, name and words people might type. */
private data class Entry(val page: String, val glyph: String, val title: String, val words: String = "")

private val INDEX = listOf(
    Entry("look", "wallpaper", "Wallpaper", "background picture glass frosted"),
    Entry("look", "palette", "Colours", "color theme accent swatch samsung"),
    Entry("look", "apps", "App icons", "themed tint original icon"),
    Entry("look", "style", "Icon pack", "icons pack"),
    Entry("look", "rounded_corner", "Icon shape", "circle round square squircle"),
    Entry("look", "blur_on", "Glass", "frost blur tint dark transparent strength"),
    Entry("home", "person", "Your name", "greeting"),
    Entry("home", "grid_view", "Grid size", "columns rows across down layout"),
    Entry("home", "label", "App names", "labels text"),
    Entry("home", "circle_notifications", "Notification dots", "badges"),
    Entry("home", "view_agenda", "Widgets page", "today feed left page calendar"),
    Entry("home", "history", "Recent apps in the dock", "dock recents"),
    Entry("home", "lock", "Lock the layout", "lock"),
    Entry("home", "edit", "Edit the home screen", "widgets move"),
    Entry("gestures", "swipe_down", "Swipe down", "deck notifications panel"),
    Entry("gestures", "swipe_up", "Swipe up", "drawer all apps"),
    Entry("gestures", "touch_app", "Double-tap to lock", "lock screen off"),
    Entry("edge", "view_sidebar", "Edge sidebar", "side edge slide shortcuts favourites rail"),
    Entry("edge", "swap_horiz", "Edge sidebar side", "left right"),
    Entry("photos", "photo_library", "Photos", "immich memories on this day favourites pictures gallery widget"),
    Entry("photos", "key", "Immich API key", "server key photos"),
    Entry("search", "calculate", "Sums and conversions", "maths calculator units"),
    Entry("search", "person_search", "Contacts in search", "people call text"),
    Entry("search", "event", "Calendar in search", "events appointments"),
    Entry("search", "description", "Files in search", "documents pdf photos downloads"),
    Entry("search", "settings", "Android settings in search", "wifi battery"),
    Entry("glance", "event", "Next event", "calendar"),
    Entry("glance", "partly_cloudy_day", "Weather", "temperature location"),
    Entry("bar", "smartphone", "Status bar", "cover inner samsung ninomae bar"),
    Entry("hidden", "visibility_off", "Hidden apps", "hide show"),
    Entry("access", "verified_user", "Access", "permissions home app notification accessibility"),
    Entry("backup", "backup", "Backup", "save restore export import"),
)

/**
 * Settings on frosted glass (T2 · Bento): a search for any setting, then tiles of different sizes,
 * each showing what it's set to. A tile opens its page; back (or ←) returns here.
 */
@Composable
fun SettingsPanel(st: HomeState, ui: Ui, compact: Boolean, modifier: Modifier) {
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    var q by remember { mutableStateOf("") }
    LaunchedEffect(ui.layer) {
        if (ui.layer != Layer.Settings) { page = null; q = "" }
        else ui.settingsPage?.let { page = it; ui.settingsPage = null }
    }
    BackHandler(enabled = page != null && ui.layer == Layer.Settings) { page = null }

    Box(modifier) {
        GlassBackdrop(st) { ui.settings }
        Column(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    val e = FastOutSlowInEasing.transform(((ui.settings - 0.12f) / 0.88f).coerceIn(0f, 1f))
                    alpha = e
                    translationY = (1f - e) * 40.dp.toPx()
                    val k = 0.97f + 0.03f * e
                    scaleX = k; scaleY = k
                }
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(horizontal = 14.dp),
        ) {
            AnimatedContent(
                page,
                transitionSpec = {
                    val forward = targetState != null
                    (slideInHorizontally { if (forward) it / 5 else -it / 5 } + fadeIn()) togetherWith
                        (slideOutHorizontally { if (forward) -it / 5 else it / 5 } + fadeOut())
                },
                label = "settings",
            ) { p ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 24.dp)) {
                    if (p == null) {
                        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Title("Settings", Modifier.weight(1f), size = 32.sp)
                            GlassButton("close", 44.dp) { ui.close() }
                        }
                        Spacer(Modifier.height(12.dp))
                        SearchField(q) { q = it }
                        Spacer(Modifier.height(10.dp))
                        if (q.isBlank()) Bento(st, compact) { page = it }
                        else Results(q) { page = it; q = "" }
                    } else {
                        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                            GlassButton("arrow_back", 44.dp) { page = null }
                            Spacer(Modifier.width(12.dp))
                            Title(CATS.first { it.id == p }.title, size = 30.sp)
                        }
                        Spacer(Modifier.height(16.dp))
                        PageBody(st, ui, p)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(q: String, onChange: (String) -> Unit) {
    val pal = LocalPal.current
    Row(
        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(25.dp)).background(glass(0.12f))
            .border(1.dp, glassEdge(), RoundedCornerShape(25.dp)).padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph("search", size = 21.dp, color = pal.acc)
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            q, onChange, Modifier.weight(1f), singleLine = true,
            textStyle = TextStyle(color = pal.fg, fontSize = 16.sp, fontFamily = Sans),
            cursorBrush = SolidColor(pal.acc),
            decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart) { if (q.isEmpty()) Lbl("Search settings", size = 15.sp, color = pal.dim); inner() } },
        )
        if (q.isNotEmpty()) GlassButton("close") { onChange("") }
    }
}

@Composable
private fun Results(q: String, onOpen: (String) -> Unit) {
    val pal = LocalPal.current
    val t = q.trim().lowercase()
    val hits = INDEX.filter { e ->
        val cat = CATS.first { it.id == e.page }.title
        e.title.lowercase().contains(t) || e.words.contains(t) || cat.lowercase().contains(t)
    }
    if (hits.isEmpty()) {
        Lbl("No setting matches “${q.trim()}”", Modifier.fillMaxWidth().padding(16.dp), color = pal.sub)
        return
    }
    Rows(*hits.map { e ->
        @Composable { NavRow(e.glyph, e.title, CATS.first { it.id == e.page }.title) { onOpen(e.page) } }
    }.toTypedArray())
}

// ---------------------------------------------------------------- bento

@Composable
private fun Bento(st: HomeState, compact: Boolean, open: (String) -> Unit) {
    val ctx = LocalContext.current
    val gap = 8.dp
    val search = @Composable { m: Modifier ->
        Tile(m, "Search", if (st.searchContacts && Search.canReadContacts(ctx)) "Apps, maths, people" else "Apps, maths, settings", { open("search") }) {
            Title("12 × 4 = 48", size = 18.sp, color = LocalPal.current.acc)
        }
    }
    val glance = @Composable { m: Modifier -> GlanceTile(st, m) { open("glance") } }

    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(Modifier.fillMaxWidth().height(248.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
            LookTile(st, Modifier.weight(1f).fillMaxHeight()) { open("look") }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                HomeTile(st, compact, Modifier.fillMaxWidth().weight(1f)) { open("home") }
                GesturesTile(st, Modifier.fillMaxWidth().weight(1f)) { open("gestures") }
            }
            if (!compact) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                search(Modifier.fillMaxWidth().weight(1f))
                glance(Modifier.fillMaxWidth().weight(1f))
            }
        }
        if (compact) Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
            search(Modifier.weight(1f).fillMaxHeight())
            glance(Modifier.weight(1f).fillMaxHeight())
        }
        // The small ones, joined into a strip. Access gets a dot while something is still off.
        val access = listOf(st.isDefaultHome, st.notifAccess, st.a11yOn, st.canWrite).count { it }
        val small = listOf(
            Triple("bar", "smartphone", "Status bar") to false,
            Triple("hidden", "visibility_off", if (st.hidden.isEmpty()) "Hidden" else "Hidden · ${st.hidden.size}") to false,
            Triple("access", "verified_user", "Access") to (access < 4),
            Triple("backup", "backup", "Backup") to false,
        )
        Row(Modifier.fillMaxWidth().height(88.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            small.forEachIndexed { i, (c, flag) ->
                val l = if (i == 0) 22.dp else 6.dp
                val r = if (i == small.lastIndex) 22.dp else 6.dp
                Box(
                    Modifier.weight(1f).fillMaxHeight().glassPiece(corners(l, r, r, l)).pressScale(0.96f).clickable { open(c.first) },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        GlassBadge(c.second, 32.dp)
                        Spacer(Modifier.height(6.dp))
                        Lbl(c.third, size = 12.sp)
                    }
                    if (flag) Dot(Modifier.align(Alignment.TopEnd).padding(10.dp))
                }
            }
        }
        // The two wide tiles, joined.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            EdgeTile(st, corners(22.dp, 22.dp, 6.dp, 6.dp)) { open("edge") }
            PhotosTile(st, corners(6.dp, 6.dp, 22.dp, 22.dp)) { open("photos") }
        }
    }
}

/** A wide tile under the rest: the edge sidebar, with what it's set to. */
@Composable
private fun EdgeTile(st: HomeState, shape: Shape, onClick: () -> Unit) {
    val state = if (!st.edgeOn) "Off" else listOf("On", if (st.edgeRight) "right" else "left", st.edgeList().size.let { if (it == 1) "1 app" else "$it apps" }).joinToString(" · ")
    WideTile("view_sidebar", "Edge sidebar", state, shape, onClick)
}

@Composable
private fun PhotosTile(st: HomeState, shape: Shape, onClick: () -> Unit) {
    val state = if (!Photos.configured(st)) "Connect your Immich" else "Immich · " + when (st.immichMode) { 0 -> "on this day"; 1 -> "favourites"; else -> "random" }
    WideTile("photo_library", "Photos", state, shape, onClick)
}

/** A wide tile under the bento: a badge, its name, and what it's set to. */
@Composable
private fun WideTile(glyph: String, name: String, state: String, shape: Shape, onClick: () -> Unit) {
    val pal = LocalPal.current
    Row(
        Modifier.fillMaxWidth().height(64.dp).glassPiece(shape).pressScale(0.98f).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassBadge(glyph)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Lbl(name, size = 15.sp, weight = FontWeight.Medium)
            Lbl(state, size = 11.sp, color = pal.sub)
        }
        Glyph("north_east", size = 16.dp, color = pal.dim)
    }
}

private fun Modifier.glassPiece(shape: Shape): Modifier = composed {
    clip(shape).background(glass(0.07f)).border(1.dp, glassEdge(), shape)
}

/** A bento tile: something live at the top, then its name and what it's set to. */
@Composable
private fun Tile(modifier: Modifier, name: String, state: String, onClick: () -> Unit, top: @Composable BoxScope.() -> Unit) {
    val pal = LocalPal.current
    Box(modifier.glassPiece(RoundedCornerShape(22.dp)).pressScale(0.97f).clickable(onClick = onClick).padding(14.dp)) {
        Box(Modifier.align(Alignment.TopStart), content = top)
        Glyph("north_east", Modifier.align(Alignment.TopEnd), size = 16.dp, color = pal.dim)
        Column(Modifier.align(Alignment.BottomStart)) {
            Lbl(name, size = 14.sp, weight = FontWeight.Medium)
            Lbl(state, size = 11.sp, color = pal.sub)
        }
    }
}

@Composable
private fun LookTile(st: HomeState, modifier: Modifier, onClick: () -> Unit) {
    val pal = LocalPal.current
    val source = when (st.colourSource) {
        0 -> "Samsung's colours"
        1 -> "From your wallpaper"
        else -> (swatches.firstOrNull { it.first == st.swatch }?.second ?: "Your") + " swatch"
    }
    Box(modifier.glassPiece(RoundedCornerShape(22.dp)).pressScale(0.97f).clickable(onClick = onClick).padding(14.dp)) {
        Box(Modifier.align(Alignment.TopCenter)) { HomePreview(st, 84.dp) }
        // The wallpaper changed outside Ninomae: pick it again for the glass.
        if (st.glassStale) Dot(Modifier.align(Alignment.TopEnd))
        Column(Modifier.align(Alignment.BottomStart)) {
            Lbl("Look", size = 15.sp, weight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(pal.acc))
                Spacer(Modifier.width(5.dp))
                Lbl(source + if (st.iconStyle == 1) " · themed" else "", size = 11.sp, color = pal.sub)
            }
        }
    }
}

@Composable
private fun HomeTile(st: HomeState, compact: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val pal = LocalPal.current
    val cols = if (compact) GridSize.coverCols else GridSize.innerCols
    val rows = if (compact) GridSize.coverRows else GridSize.innerRows
    Tile(modifier, "Home screen", "$cols × $rows" + if (st.labels) " · names on" else "", onClick) {
        MiniGrid(minOf(cols, 6), 3, 12.dp, lit = setOf(1, 7, 14), pal.acc)
    }
}

@Composable
private fun MiniGrid(cols: Int, rows: Int, cell: Dp, lit: Set<Int>, on: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(cell / 3)) {
        repeat(rows) { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(cell / 3)) {
                repeat(cols) { c ->
                    Box(Modifier.size(cell).clip(RoundedCornerShape(cell * 0.3f)).background(if (r * cols + c in lit) on else glass(0.14f)))
                }
            }
        }
    }
}

@Composable
private fun GesturesTile(st: HomeState, modifier: Modifier, onClick: () -> Unit) {
    val down = when (st.swipeDown) { 0 -> "deck"; 1 -> "Samsung"; else -> null }
    val state = listOfNotNull("Drawer", down, if (st.doubleTapLock) "lock" else null).joinToString(" · ")
    Tile(modifier, "Gestures", state, onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("swipe_up", "swipe_down", "touch_app").forEach { GlassBadge(it, 30.dp) }
        }
    }
}

@Composable
private fun GlanceTile(st: HomeState, modifier: Modifier, onClick: () -> Unit) {
    val pal = LocalPal.current
    val ev = Glance.event
    val w = Glance.weather
    val state = listOfNotNull(if (st.glanceCalendar) "Calendar" else null, if (st.glanceWeather) "weather" else null)
        .joinToString(" + ").ifEmpty { "Off" }
    Tile(modifier, "At a glance", state, onClick) {
        when {
            ev != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(4.dp, 30.dp).clip(RoundedCornerShape(2.dp)).background(pal.acc))
                Spacer(Modifier.width(8.dp))
                Column { Lbl(ev.title, Modifier.width(110.dp), size = 13.sp); Lbl(ev.whenText, size = 11.sp, color = pal.sub) }
            }
            w != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph(w.glyph, size = 22.dp, color = pal.acc)
                Spacer(Modifier.width(6.dp))
                Title(w.temp, size = 18.sp)
            }
            else -> Lbl("Nothing soon", size = 13.sp, color = pal.sub)
        }
    }
}

// ---------------------------------------------------------------- pages

@Composable
private fun PageBody(st: HomeState, ui: Ui, p: String) {
    val ctx = LocalContext.current
    when (p) {
        "look" -> LookPage(st, ui)
        "home" -> HomePage(st, ui)
        "gestures" -> Rows(
            { Row3("swipe_down", "Swipe down on the cover", "On an empty spot of the home screen", wide = true) { Segs(listOf("Deck", "Samsung", "Off"), st.swipeDown) { st.updateSwipeDown(true, it) } } },
            { Row3("swipe_down", "Swipe down on the inner screen", "The bar's status pieces open the deck too", wide = true) { Segs(listOf("Deck", "Samsung", "Off"), st.swipeDownInner) { st.updateSwipeDown(false, it) } } },
            { Row3("swipe_up", "Swipe up", "Opens all apps, following your finger. Swipe down there to close") {} },
            { Row3("touch_app", "Double-tap to lock", "Uses accessibility") { GSwitch(st.doubleTapLock) { st.updateDoubleTapLock(!st.doubleTapLock) } } },
        )
        "edge" -> EdgePage(st)
        "photos" -> PhotosPage(st)
        "search" -> Rows(
            { Row3("calculate", "Sums and conversions", "Type 12*7, 15% of 80, or 5 km to mi") {} },
            {
                Row3("person_search", "Contacts", if (Search.canReadContacts(ctx)) "Call or text from search" else "Needs permission to read contacts") {
                    if (st.searchContacts && !Search.canReadContacts(ctx)) GPill("Allow", accent = true) { ui.askPermissions(arrayOf(Manifest.permission.READ_CONTACTS)) }
                    else GSwitch(st.searchContacts) { st.updateSearchContacts(!st.searchContacts) }
                }
            },
            {
                Row3("event", "Calendar", if (Search.canReadCalendar(ctx)) "Your events, a month back to a year ahead" else "Needs permission to read your calendar") {
                    if (st.searchCalendar && !Search.canReadCalendar(ctx)) GPill("Allow", accent = true) { ui.askPermissions(arrayOf(Manifest.permission.READ_CALENDAR)) }
                    else GSwitch(st.searchCalendar) { st.updateSearchCalendar(!st.searchCalendar) }
                }
            },
            {
                Row3("description", "Files", if (Search.canReadFiles()) "Files on the phone, by name, newest first" else "Needs All files access") {
                    if (st.searchFiles && !Search.canReadFiles()) GPill("Allow", accent = true) { Search.allowFiles(ctx) }
                    else GSwitch(st.searchFiles) { st.updateSearchFiles(!st.searchFiles) }
                }
            },
            { Row3("settings", "Android settings", "Wi-Fi, battery, storage and more, by name") {} },
            { Row3("travel_explore", "The web and the Play Store", "At the end of every search") {} },
        )
        "glance" -> Rows(
            {
                val can = Glance.has(ctx, Manifest.permission.READ_CALENDAR)
                Row3("event", "Next event", if (can) "Under the clock, for the next 24 hours" else "Needs permission to read your calendar") {
                    if (st.glanceCalendar && !can) GPill("Allow", accent = true) { ui.askPermissions(arrayOf(Manifest.permission.READ_CALENDAR)) }
                    else GSwitch(st.glanceCalendar) { st.updateGlance(!st.glanceCalendar, st.glanceWeather) }
                }
            },
            {
                Row3("partly_cloudy_day", "Weather", "Sends your rough location (about 1 km) to Open-Meteo every 30 minutes") {
                    GSwitch(st.glanceWeather) {
                        val on = !st.glanceWeather
                        st.updateGlance(st.glanceCalendar, on)
                        if (on && !Glance.has(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)) ui.askPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                }
            },
        )
        "bar" -> Rows(
            { Row3("smartphone", "Cover screen", "Ninomae's bar, or Samsung's like in your apps", wide = true) { Segs(listOf("Ninomae", "Samsung"), if (st.barCover) 0 else 1) { st.updateBar(true, it == 0) } } },
            { Row3("tablet", "Inner screen", "Ninomae's bar holds your pairs and the player", wide = true) { Segs(listOf("Ninomae", "Samsung"), if (st.barInner) 0 else 1) { st.updateBar(false, it == 0) } } },
        )
        "hidden" -> {
            val hidden = st.hidden.mapNotNull { st.app(it) }
            if (hidden.isEmpty()) Rows({ Row3("visibility", "Nothing hidden", "Hold an app and pick Hide to tuck it away") {} })
            else Rows(*hidden.map { app -> @Composable { AppRow(app) { GPill("Show") { st.setHidden(app.key, false) } } } }.toTypedArray())
        }
        "access" -> Rows(
            { Access("home", "Home app", "Ninomae as your home screen, needed for app shortcuts", st.isDefaultHome) { Actions.homeSettings(ctx) } },
            { Access("notifications", "Notification access", "Notifications, dots and the player", st.notifAccess) { Actions.openListenerSettings(ctx) } },
            { Access("splitscreen_left", "Accessibility", "Split screen, pairs, lock, screenshot, power menu", st.a11yOn) { Actions.openAccessibility(ctx) } },
            { Access("brightness_6", "Change system settings", "Brightness and auto-rotate", st.canWrite) { Actions.openWriteSettings(ctx) } },
        )
        "backup" -> Rows(
            { Row3("save", "Save a backup", "Your layout, dock, pairs, folders and settings, as a file") { GPill("Save", accent = true) { ui.backup() } } },
            { Row3("settings_backup_restore", "Restore", "Widgets come back only on this phone") { GPill("Open") { ui.restore() } } },
        )
    }
}

@Composable
private fun LookPage(st: HomeState, ui: Ui) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val packs = remember { Icons.packs(ctx) }
    val choices = listOf<IconPack?>(null) + packs
    val shared = remember(st.colourSource) { wallpaperSeed(ctx) != null }
    Rows(
        // The wallpaper, as its own card.
        {
            Row(Modifier.fillMaxWidth().glassPiece(LocalRowShape.current).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                HomePreview(st, 84.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Lbl("Wallpaper", size = 17.sp, weight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Lbl(
                        when {
                            st.glass != null -> "The drawer and settings frost it"
                            st.glassStale -> "Your wallpaper changed outside Ninomae. Pick it here again to frost the glass"
                            else -> "Samsung won't share yours. Pick it here and the drawer and settings turn to frosted glass"
                        },
                        size = 12.sp, color = pal.sub, maxLines = 4,
                    )
                    Spacer(Modifier.height(12.dp))
                    GPill(if (st.glass != null) "Change" else "Pick", glyph = "image", accent = true) { ui.pickWallpaper() }
                }
            }
        },
        // Colours: where they come from, and the swatches.
        {
            Column(Modifier.fillMaxWidth().glassPiece(LocalRowShape.current).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassBadge("palette", 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Lbl("Colours", size = 15.sp, weight = FontWeight.Medium)
                        Lbl(
                            when {
                                st.colourSource == 0 -> "Samsung's palette, the same as your other apps"
                                st.colourSource == 1 && (shared || st.wallSeed != 0) -> "Worked out from your wallpaper"
                                st.colourSource == 1 -> "Pick your wallpaper above to take its colours. Until then, your swatch"
                                else -> "Your swatch, whatever the wallpaper"
                            },
                            size = 11.sp, color = pal.sub, maxLines = 3,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Segs(listOf("Wallpaper", "Swatch", "Samsung"), listOf(1, 2, 0).indexOf(st.colourSource)) { st.updateColourSource(listOf(1, 2, 0)[it]) }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    swatches.forEach { (c, _) ->
                        val on = st.swatch == c && st.colourSource == 2
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(Color(c))
                                .then(if (on) Modifier.border(3.dp, pal.fg, CircleShape) else Modifier)
                                .clickable { st.updateSwatch(c); st.updateColourSource(2) },
                        )
                    }
                }
            }
        },
        // App icons: two cards to pick from, showing your dock both ways.
        {
            Column(Modifier.fillMaxWidth().glassPiece(LocalRowShape.current).padding(12.dp)) {
                Lbl("App icons", Modifier.padding(start = 2.dp, bottom = 10.dp), size = 15.sp, weight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Original" to 0, "Themed" to 1).forEach { (name, v) ->
                        val on = st.iconStyle == v
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                                .background(if (on) pal.acc.copy(alpha = 0.18f) else glass(0.07f))
                                .border(if (on) 2.dp else 1.dp, if (on) pal.acc else glassEdge(), RoundedCornerShape(16.dp))
                                .clickable { st.updateIcons(v, st.iconPack) }.padding(vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CompositionLocalProvider(LocalThemedIcons provides (v == 1)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    st.pinned.take(3).mapNotNull { st.app(it) }.forEach { GlassIcon(it, 30.dp) }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Lbl(name, size = 13.sp, color = if (on) pal.acc else pal.fg, weight = FontWeight.Medium)
                        }
                    }
                }
            }
        },
        // Icon shape: four cards, each showing your dock in that shape.
        {
            Column(Modifier.fillMaxWidth().glassPiece(LocalRowShape.current).padding(12.dp)) {
                Lbl("Icon shape", Modifier.padding(start = 2.dp, bottom = 10.dp), size = 15.sp, weight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Squircle", "Circle", "Rounded", "Square").forEachIndexed { v, name ->
                        val on = st.iconShape == v
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                .background(if (on) pal.acc.copy(alpha = 0.18f) else glass(0.07f))
                                .border(if (on) 2.dp else 1.dp, if (on) pal.acc else glassEdge(), RoundedCornerShape(14.dp))
                                .clickable { st.updateIconShape(v) }.padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CompositionLocalProvider(LocalIconShape provides v) {
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    st.pinned.take(2).mapNotNull { st.app(it) }.forEach { GlassIcon(it, 24.dp) }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Lbl(name, size = 12.sp, color = if (on) pal.acc else pal.fg, weight = FontWeight.Medium)
                        }
                    }
                }
            }
        },
        // Glass: how frosted, and how dark. This page sits on the glass, so it shows the change as you go.
        {
            Column(Modifier.fillMaxWidth().glassPiece(LocalRowShape.current).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassBadge("blur_on", 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Lbl("Glass", size = 15.sp, weight = FontWeight.Medium)
                        Lbl("This page is on it, so you see the change as you go", size = 11.sp, color = pal.sub)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Lbl("Frost", Modifier.weight(1f), size = 14.sp)
                    Segs(listOf("Light", "Medium", "Heavy"), st.glassFrost) { st.updateGlassFrost(it) }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Lbl("Tint", Modifier.width(56.dp), size = 14.sp)
                    // 0.5× (lighter) to 1.6× (darker) the designed wash; 1 sits a third of the way along.
                    val f = ((st.glassTint - 0.5f) / 1.1f).coerceIn(0f, 1f)
                    Box(
                        Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(14.dp)).background(glass(0.10f))
                            .slideToSet { v -> st.updateGlassTint(0.5f + 1.1f * v) },
                    ) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(f.coerceAtLeast(0.08f)).clip(RoundedCornerShape(14.dp)).background(pal.acc))
                        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Lbl("Lighter", Modifier.weight(1f), size = 11.sp, color = if (f > 0.2f) pal.ink else pal.sub)
                            Lbl("Darker", size = 11.sp, color = pal.sub)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    GPill("Reset") { st.updateGlassTint(1f) }
                }
                if (st.glassFrost == 0 && !Glass.canLighten(LocalContext.current)) {
                    Spacer(Modifier.height(8.dp))
                    Lbl("Light shows once the wallpaper is picked here again (above)", size = 11.sp, color = pal.sub, maxLines = 2)
                }
            }
        },
        { Row3("style", "Icon pack", if (packs.isEmpty()) "Install one from the Play Store and it shows here" else "Apps it doesn't cover keep their own icon") {} },
        *choices.map { p ->
            @Composable {
                val chosen = st.iconPack == p?.pkg
                Row(
                    Modifier.fillMaxWidth().height(52.dp).glassPiece(LocalRowShape.current).clickable { st.updateIcons(st.iconStyle, p?.pkg) }
                        .padding(start = 64.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Lbl(p?.label ?: "None, the apps' own", Modifier.weight(1f), color = if (chosen) pal.acc else pal.fg)
                    Radio(chosen)
                }
            }
        }.toTypedArray(),
    )
}

@Composable
private fun HomePage(st: HomeState, ui: Ui) {
    val pal = LocalPal.current
    Rows(
        // Both grids at a glance, then their sizes.
        {
            Row(Modifier.fillMaxWidth().glassPiece(LocalRowShape.current).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                listOf(Triple("Cover", GridSize.coverCols, GridSize.coverRows), Triple("Inner", GridSize.innerCols, GridSize.innerRows)).forEach { (name, c, r) ->
                    Column {
                        MiniGrid(c, r, if (c > 6) 9.dp else 12.dp, lit = setOf(0, c + 2, 2 * c + 1), pal.acc)
                        Spacer(Modifier.height(8.dp))
                        Lbl(name, size = 13.sp, weight = FontWeight.Medium)
                        Lbl("$c × $r", size = 11.sp, color = pal.sub)
                    }
                }
            }
        },
        { Row3("grid_view", "Cover grid, across", "") { Segs(listOf("4", "5"), GridSize.coverCols - 4) { st.updateGrid(1, 4 + it, GridSize.coverRows) } } },
        { Row3("table_rows", "Cover grid, down", "") { Segs(listOf("5", "6", "7"), GridSize.coverRows - 5) { st.updateGrid(1, GridSize.coverCols, 5 + it) } } },
        { Row3("grid_view", "Inner grid, across", "", wide = true) { Segs(listOf("6", "7", "8", "9"), GridSize.innerCols - 6) { st.updateGrid(0, 6 + it, GridSize.innerRows) } } },
        { Row3("table_rows", "Inner grid, down", "") { Segs(listOf("4", "5", "6"), GridSize.innerRows - 4) { st.updateGrid(0, GridSize.innerCols, 4 + it) } } },
        {
            Row3("person", "Your name", "Shown in the greeting") {
                Box(
                    Modifier.width(130.dp).height(36.dp).clip(RoundedCornerShape(18.dp)).background(glass(0.12f)).padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        st.name, { st.updateName(it.take(20)) }, Modifier.fillMaxWidth(), singleLine = true,
                        textStyle = TextStyle(color = pal.fg, fontSize = 14.sp, fontFamily = Sans),
                        cursorBrush = SolidColor(pal.acc),
                        decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart) { if (st.name.isEmpty()) Lbl("Name", size = 14.sp, color = pal.dim); inner() } },
                    )
                }
            }
        },
        { Row3("label", "App names", "Under icons on the home screen") { GSwitch(st.labels) { st.updateLabels(!st.labels) } } },
        { Row3("circle_notifications", "Notification dots", "On apps with notifications") { GSwitch(st.dots) { st.updateDots(!st.dots) } } },
        { Row3("view_agenda", "Widgets page", "Left of your first page: today, then your widgets") { GSwitch(st.feedOn) { st.updateFeedOn(!st.feedOn) } } },
        { Row3("history", "Recent apps in the dock", "The dock's middle piece, inner screen") { GSwitch(st.dockRecentsOn) { st.updateDockRecents(!st.dockRecentsOn) } } },
        { Row3("lock", "Lock the layout", "Nothing moves or gets removed until you unlock it") { GSwitch(st.locked) { st.updateLocked(!st.locked) } } },
        { Row3("edit", "Edit the home screen", "Or hold an empty spot") { GPill("Edit") { ui.close(); ui.editing = true } } },
    )
}

// ---------------------------------------------------------------- rows and controls

private val LocalRowShape = androidx.compose.runtime.compositionLocalOf<Shape> { RoundedCornerShape(6.dp) }

/** Rows joined into one block: big corners at its ends, small ones between. */
@Composable
private fun Rows(vararg rows: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEachIndexed { i, r ->
            val top = if (i == 0) 22.dp else 6.dp
            val end = if (i == rows.lastIndex) 22.dp else 6.dp
            CompositionLocalProvider(LocalRowShape provides corners(top, top, end, end)) { r() }
        }
    }
}

/** A settings row. [wide] controls (several choices) sit under the text so it never gets squeezed. */
@Composable
private fun Row3(glyph: String, title: String, sub: String, wide: Boolean = false, trailing: @Composable () -> Unit) {
    val pal = LocalPal.current
    Column(Modifier.fillMaxWidth().heightIn(min = 68.dp).glassPiece(LocalRowShape.current).padding(horizontal = 12.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassBadge(glyph)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Lbl(title, size = 15.sp, weight = FontWeight.Medium)
                if (sub.isNotEmpty()) Lbl(sub, size = 11.sp, color = pal.sub, maxLines = 3)
            }
            if (!wide) {
                Spacer(Modifier.width(10.dp))
                trailing()
            }
        }
        if (wide) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.padding(start = 50.dp)) { trailing() }
        }
    }
}

/** A row that opens a page (search results). */
@Composable
private fun NavRow(glyph: String, title: String, sub: String, onClick: () -> Unit) {
    val pal = LocalPal.current
    Row(
        Modifier.fillMaxWidth().height(64.dp).glassPiece(LocalRowShape.current).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassBadge(glyph)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Lbl(title, size = 15.sp, weight = FontWeight.Medium)
            Lbl(sub, size = 11.sp, color = pal.sub)
        }
        Glyph("chevron_right", size = 20.dp, color = pal.dim)
    }
}

@Composable
private fun AppRow(app: AppEntry, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(60.dp).glassPiece(LocalRowShape.current).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIcon(app, 36.dp)
        Spacer(Modifier.width(12.dp))
        Lbl(app.label, Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun Access(glyph: String, title: String, sub: String, granted: Boolean, onClick: () -> Unit) {
    val pal = LocalPal.current
    Row3(glyph, title, sub) {
        if (granted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph("check_circle", size = 18.dp, color = pal.acc, fill = true)
                Spacer(Modifier.width(6.dp))
                Lbl("On", size = 13.sp, color = pal.acc)
            }
        } else GPill("Allow", accent = true, onClick = onClick)
    }
}

/** Choices as joined pills: round at the ends, the chosen one in the accent. */
@Composable
private fun Segs(options: List<String>, chosen: Int, onPick: (Int) -> Unit) {
    val pal = LocalPal.current
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEachIndexed { i, o ->
            val l = if (i == 0) 18.dp else 6.dp
            val r = if (i == options.lastIndex) 18.dp else 6.dp
            val on = i == chosen
            Box(
                Modifier.height(36.dp).clip(corners(l, r, r, l)).background(if (on) pal.acc else glass(0.12f))
                    .clickable { onPick(i) }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Lbl(o, size = 13.sp, color = if (on) pal.ink else pal.fg, weight = if (on) FontWeight.SemiBold else FontWeight.Normal) }
        }
    }
}

@Composable
private fun GSwitch(on: Boolean, onClick: () -> Unit) {
    val pal = LocalPal.current
    val x by animateDpAsState(if (on) 22.dp else 4.dp, label = "switch")
    Box(Modifier.size(46.dp, 28.dp).clip(CircleShape).background(if (on) pal.acc else glass(0.14f)).clickable(onClick = onClick)) {
        Box(Modifier.offset(x = x, y = 4.dp).size(20.dp).clip(CircleShape).background(if (on) pal.ink else pal.sub))
    }
}

@Composable
private fun GPill(text: String, glyph: String? = null, accent: Boolean = false, onClick: () -> Unit) {
    val pal = LocalPal.current
    Row(
        Modifier.height(38.dp).clip(RoundedCornerShape(19.dp)).background(if (accent) pal.acc else glass(0.12f))
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            Glyph(glyph, size = 17.dp, color = if (accent) pal.ink else pal.fg)
            Spacer(Modifier.width(6.dp))
        }
        Lbl(text, size = 14.sp, color = if (accent) pal.ink else pal.fg, weight = FontWeight.SemiBold)
    }
}

@Composable
private fun Radio(on: Boolean) {
    val pal = LocalPal.current
    Box(Modifier.size(20.dp).clip(CircleShape).background(if (on) pal.acc else glass(0.14f)), contentAlignment = Alignment.Center) {
        if (on) Box(Modifier.size(8.dp).clip(CircleShape).background(pal.ink))
    }
}

/**
 * The edge sidebar's own page: a preview with the switch, which side and where it opens, then its apps
 * (drag the handle to reorder, × to take one off) and Add apps, which opens a picker right here.
 */
@Composable
private fun EdgePage(st: HomeState) {
    val pal = LocalPal.current
    val density = LocalDensity.current
    var adding by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // ---------- preview + switch ----------
        Row(
            Modifier.fillMaxWidth().glassPiece(corners(22.dp, 22.dp, 6.dp, 6.dp)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                HomePreview(st, 84.dp)
                // The rail, in miniature, on its side of the little phone.
                Column(
                    Modifier.align(if (st.edgeRight) Alignment.CenterEnd else Alignment.CenterStart)
                        .clip(if (st.edgeRight) corners(8.dp, 0.dp, 0.dp, 8.dp) else corners(0.dp, 8.dp, 8.dp, 0.dp))
                        .background(pal.l0.copy(alpha = 0.85f)).padding(horizontal = 5.dp, vertical = 6.dp)
                        .graphicsLayer { alpha = if (st.edgeOn) 1f else 0.35f },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    repeat(5) { i -> Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(if (i == 2) pal.acc else pal.sub.copy(alpha = 0.6f))) }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Title("Slide up along the edge", size = 17.sp)
                Spacer(Modifier.height(4.dp))
                Lbl("Glide onto an app and lift to open it. The ones near your finger swell, and its name shows beside the rail.", size = 12.sp, color = pal.sub, maxLines = 4)
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Lbl(if (st.edgeOn) "On" else "Off", Modifier.weight(1f), size = 14.sp)
                    GSwitch(st.edgeOn) { st.updateEdgeOn(!st.edgeOn) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(60.dp).glassPiece(RoundedCornerShape(6.dp)).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Lbl("Side", Modifier.weight(1f), size = 15.sp)
            Segs(listOf("Left", "Right"), if (st.edgeRight) 1 else 0) { st.updateEdgeRight(it == 1) }
        }
        Row(Modifier.fillMaxWidth().height(60.dp).glassPiece(corners(6.dp, 6.dp, 22.dp, 22.dp)).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Lbl("Opens", Modifier.weight(1f), size = 15.sp)
            Segs(listOf("At your thumb", "Middle"), if (st.edgeMiddle) 1 else 0) { st.updateEdgeMiddle(it == 1) }
        }

        // ---------- its apps ----------
        Row(Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
            Title("Its apps", size = 18.sp)
            Spacer(Modifier.width(10.dp))
            Lbl("drag the handle to reorder", Modifier.padding(bottom = 2.dp), size = 12.sp, color = pal.dim)
        }
        val apps = st.edgeList()
        val step = with(density) { (54.dp + 4.dp).toPx() }
        var dragKey by remember { mutableStateOf<String?>(null) }
        var dragY by remember { mutableFloatStateOf(0f) }
        apps.forEachIndexed { i, app -> androidx.compose.runtime.key(app.key) {
            val top = if (i == 0) 22.dp else 6.dp
            val dragging = dragKey == app.key
            Row(
                Modifier.fillMaxWidth().height(54.dp)
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { if (dragging) { translationY = dragY; scaleX = 1.02f; scaleY = 1.02f } }
                    .glassPiece(corners(top, top, 6.dp, 6.dp))
                    .padding(end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(44.dp, 54.dp).pointerInput(app.key) {
                        detectVerticalDragGestures(
                            onDragStart = { dragKey = app.key; dragY = 0f },
                            onDragEnd = { dragKey = null; dragY = 0f },
                            onDragCancel = { dragKey = null; dragY = 0f },
                        ) { c, dy ->
                            c.consume()
                            dragY += dy
                            // Past half a row: it swaps with its neighbour and the offset carries on from there.
                            if (dragY > step / 2) { st.moveEdge(app.key, 1); dragY -= step }
                            if (dragY < -step / 2) { st.moveEdge(app.key, -1); dragY += step }
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) { Glyph("drag_indicator", size = 20.dp, color = if (dragging) pal.acc else pal.dim) }
                GlassIcon(app, 34.dp)
                Spacer(Modifier.width(12.dp))
                Lbl(app.label, Modifier.weight(1f), size = 15.sp)
                Box(Modifier.size(40.dp).clip(CircleShape).clickable { st.toggleEdge(app.key) }, contentAlignment = Alignment.Center) {
                    Glyph("close", size = 18.dp, color = pal.sub)
                }
            }
        } }
        // ---------- add apps: a picker right here ----------
        Row(
            Modifier.fillMaxWidth().height(54.dp).glassPiece(corners(if (apps.isEmpty()) 22.dp else 6.dp, if (apps.isEmpty()) 22.dp else 6.dp, if (adding) 6.dp else 22.dp, if (adding) 6.dp else 22.dp))
                .clickable { adding = !adding }.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(pal.acc), contentAlignment = Alignment.Center) {
                Glyph(if (adding) "check" else "add", size = 20.dp, color = pal.ink)
            }
            Spacer(Modifier.width(12.dp))
            Lbl(if (adding) "Done" else "Add apps", Modifier.weight(1f), size = 15.sp)
        }
        if (adding) {
            val all = remember(st.apps, st.hidden) { st.apps.filter { it.key !in st.hidden } }
            Column(Modifier.fillMaxWidth().glassPiece(corners(6.dp, 6.dp, 22.dp, 22.dp)).padding(6.dp)) {
                all.forEach { app ->
                    val inside = st.isOnEdge(app.key)
                    Row(
                        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (inside) pal.acc.copy(alpha = 0.14f) else Color.Transparent)
                            .clickable { st.toggleEdge(app.key) }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlassIcon(app, 34.dp)
                        Spacer(Modifier.width(12.dp))
                        Lbl(app.label, Modifier.weight(1f))
                        Radio(inside)
                    }
                }
            }
        }
    }
}

/**
 * Photos: your Immich server and API key for the photo widget, what it shows, and a connection check.
 * The key stays on the phone and is left out of backups.
 */
@Composable
private fun PhotosPage(st: HomeState) {
    val pal = LocalPal.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var url by remember { mutableStateOf(st.immichUrl) }
    var key by remember { mutableStateOf(st.immichKey) }
    var showKey by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    // Saved when you leave the page (not on every key), then the widget reloads.
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { if (url.trim() != st.immichUrl || key.trim() != st.immichKey) st.updateImmich(url = url.trim(), key = key.trim()) }
    }
    fun test() {
        st.updateImmich(url = url.trim(), key = key.trim())
        testing = true
        scope.launch {
            status = Photos.test(st)
            testing = false
            Photos.refresh(st, force = true)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PhotoField("dns", "Immich server", url, "https://photos.example.com", corners(22.dp, 22.dp, 6.dp, 6.dp), false, { url = it }) {}
        PhotoField("key", "API key", key, "Paste the key you made in Immich", RoundedCornerShape(6.dp), !showKey, { key = it }) {
            Box(Modifier.size(40.dp).clip(CircleShape).clickable { showKey = !showKey }, contentAlignment = Alignment.Center) {
                Glyph(if (showKey) "visibility_off" else "visibility", size = 18.dp, color = pal.sub)
            }
        }
        Row(Modifier.fillMaxWidth().height(60.dp).glassPiece(RoundedCornerShape(6.dp)).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Lbl("Show", Modifier.weight(1f), size = 15.sp)
            Segs(listOf("On this day", "Favourites", "Random"), st.immichMode) { st.updateImmich(mode = it) }
        }
        val ok = status != null && !status!!.startsWith("Not connected")
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).glassPiece(corners(6.dp, 6.dp, 22.dp, 22.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(if (ok) Color(0xFF8CCB9B) else glass(0.12f)),
                contentAlignment = Alignment.Center,
            ) { Glyph(if (ok) "check" else if (status != null) "cloud_off" else "sync", size = 18.dp, color = if (ok) Color(0xFF14301C) else pal.fg) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Lbl(if (testing) "Checking…" else if (ok) "Connected" else if (status != null) "Not connected" else "Check the connection", size = 15.sp)
                Lbl(status?.removePrefix("Not connected: ")?.replaceFirstChar { it.uppercase() } ?: "Reaches the server with your key", size = 11.sp, color = pal.sub, maxLines = 2)
            }
            Spacer(Modifier.width(8.dp))
            GPill("Test") { if (!testing) test() }
        }
        Spacer(Modifier.height(12.dp))
        Lbl(
            "Make a key in Immich: Account settings → API keys. Give it access to assets and memories. " +
                "At home, the server's address on your network works too (http://server.lan:2283). " +
                "The key stays on this phone and isn't saved in backups.",
            Modifier.padding(horizontal = 6.dp), size = 12.sp, color = pal.sub, maxLines = 6,
        )
    }
}

@Composable
private fun PhotoField(glyph: String, title: String, value: String, hint: String, shape: Shape, hidden: Boolean, onChange: (String) -> Unit, trailing: @Composable () -> Unit) {
    val pal = LocalPal.current
    Row(Modifier.fillMaxWidth().glassPiece(shape).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        GlassBadge(glyph)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Lbl(title, size = 15.sp, weight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            BasicTextField(
                value, { onChange(it.trim()) }, Modifier.fillMaxWidth(), singleLine = true,
                textStyle = TextStyle(color = pal.fg, fontSize = 13.sp, fontFamily = Sans),
                cursorBrush = SolidColor(pal.acc),
                visualTransformation = if (hidden) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = if (hidden) androidx.compose.ui.text.input.KeyboardType.Password else androidx.compose.ui.text.input.KeyboardType.Uri,
                    autoCorrectEnabled = false,
                ),
                decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart) { if (value.isEmpty()) Lbl(hint, size = 13.sp, color = pal.dim); inner() } },
            )
        }
        trailing()
    }
}
