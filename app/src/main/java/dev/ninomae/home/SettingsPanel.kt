package dev.ninomae.home

import android.Manifest
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class Category(val id: String, val glyph: String, val title: String, val about: String)

/** Launcher settings: a list of categories, each opening its own page of rows. */
@Composable
fun SettingsPanel(st: HomeState, ui: Ui, modifier: Modifier) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    val access = listOf(st.isDefaultHome, st.notifAccess, st.a11yOn, st.canWrite).count { it }
    val cats = listOf(
        Category(
            "look", "palette", "Look",
            when {
                st.colourSource == 0 -> "Samsung's colours"
                st.colourSource == 1 && wallpaperSeed(ctx) != null -> "Colours from your wallpaper"
                else -> (swatches.firstOrNull { it.first == st.swatch }?.second ?: "Your") + " swatch"
            },
        ),
        Category("home", "grid_view", "Home screen", "Grid, names, dots, lock"),
        Category("gestures", "swipe", "Gestures", "Swipes and double-tap"),
        Category("search", "search", "Search", "Apps, maths, contacts, settings"),
        Category("glance", "event", "At a glance", "Calendar and weather by the clock"),
        Category("bar", "smartphone", "Status bar", "Ninomae's or Samsung's, per screen"),
        Category("hidden", "visibility_off", "Hidden apps", if (st.hidden.isEmpty()) "None" else "${st.hidden.size} hidden"),
        Category("access", "verified_user", "Access", "$access of 4 on"),
        Category("backup", "backup", "Backup", "Save or restore your home"),
    )
    val big = Rad.normal
    val j = Rad.joined

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Piece(Modifier.fillMaxWidth().height(64.dp), corners(big, big, j, j), edge = true) {
            Row(Modifier.fillMaxSize().padding(start = 10.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val cat = cats.firstOrNull { it.id == page }
                if (cat == null) {
                    Spacer(Modifier.width(4.dp))
                    SqIcon("tune", true, 36.dp)
                } else {
                    IconButton("arrow_back", 40.dp, pal.fg) { page = null }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Title(cat?.title ?: "Ninomae Home", size = 18.sp)
                    Lbl(cat?.about ?: "Version 0.4", size = 12.sp, color = pal.sub)
                }
                IconButton("close") { ui.close() }
            }
        }

        AnimatedContent(
            page,
            transitionSpec = {
                val forward = targetState != null
                (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
            },
            label = "settings",
        ) { p ->
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (p) {
                    null -> cats.forEachIndexed { i, c ->
                        val end = if (i == cats.lastIndex) big else j
                        Piece(Modifier.fillMaxWidth().height(64.dp), corners(j, j, end, end), edge = true, onClick = { page = c.id }) {
                            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                SqIcon(c.glyph, false)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Lbl(c.title, weight = FontWeight.SemiBold)
                                    Lbl(c.about, size = 12.sp, color = pal.sub)
                                }
                                Glyph("chevron_right", size = 20.dp, color = pal.dim)
                            }
                        }
                    }
                    "look" -> LookPage(st)
                    "home" -> HomePage(st, ui)
                    "gestures" -> Rows(
                        { Row3("swipe_down", "Swipe down on the cover", "On an empty spot of the home screen", wide = true) { Segs(listOf("Deck", "Samsung", "Off"), st.swipeDown) { st.updateSwipeDown(true, it) } } },
                        { Row3("swipe_down", "Swipe down on the inner screen", "The bar's status pieces open the deck too", wide = true) { Segs(listOf("Deck", "Samsung", "Off"), st.swipeDownInner) { st.updateSwipeDown(false, it) } } },
                        { Row3("swipe_up", "Swipe up", "Raises all apps, following your finger") {} },
                        { Row3("touch_app", "Double-tap to lock", "Uses accessibility") { MiniSwitch(st.doubleTapLock) { st.updateDoubleTapLock(!st.doubleTapLock) } } },
                    )
                    "search" -> Rows(
                        { Row3("calculate", "Sums and conversions", "Type 12*7, 15% of 80, or 5 km to mi") {} },
                        {
                            Row3("person_search", "Contacts", if (Search.canReadContacts(ctx)) "Call or text from search" else "Needs permission to read contacts") {
                                if (st.searchContacts && !Search.canReadContacts(ctx)) AccentButton("Allow", height = 32.dp) { ui.askPermissions(arrayOf(Manifest.permission.READ_CONTACTS)) }
                                else MiniSwitch(st.searchContacts) { st.updateSearchContacts(!st.searchContacts) }
                            }
                        },
                        { Row3("settings", "Android settings", "Wi-Fi, battery, storage and more, by name") {} },
                        { Row3("travel_explore", "The web and the Play Store", "At the end of every search") {} },
                    )
                    "glance" -> Rows(
                        {
                            val can = Glance.has(ctx, Manifest.permission.READ_CALENDAR)
                            Row3("event", "Next event", if (can) "Under the clock, for the next 24 hours" else "Needs permission to read your calendar") {
                                if (st.glanceCalendar && !can) AccentButton("Allow", height = 32.dp) { ui.askPermissions(arrayOf(Manifest.permission.READ_CALENDAR)) }
                                else MiniSwitch(st.glanceCalendar) { st.updateGlance(!st.glanceCalendar, st.glanceWeather) }
                            }
                        },
                        {
                            Row3("partly_cloudy_day", "Weather", "Sends your rough location (about 1 km) to Open-Meteo every 30 minutes") {
                                MiniSwitch(st.glanceWeather) {
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
                        else Rows(*hidden.map { app -> @Composable { AppRow(app) { PlainButton("Show") { st.setHidden(app.key, false) } } } }.toTypedArray())
                    }
                    "access" -> Rows(
                        { Access("home", "Home app", "Ninomae as your home screen; needed for app shortcuts", st.isDefaultHome) { Actions.homeSettings(ctx) } },
                        { Access("notifications", "Notification access", "Notifications, dots and the player", st.notifAccess) { Actions.openListenerSettings(ctx) } },
                        { Access("splitscreen_left", "Accessibility", "Split screen, pairs, lock, screenshot, power menu", st.a11yOn) { Actions.openAccessibility(ctx) } },
                        { Access("brightness_6", "Change system settings", "Brightness and auto-rotate", st.canWrite) { Actions.openWriteSettings(ctx) } },
                    )
                    "backup" -> Rows(
                        { Row3("save", "Save a backup", "Your layout, dock, pairs, folders and settings, as a file") { AccentButton("Save", height = 32.dp) { ui.backup() } } },
                        { Row3("settings_backup_restore", "Restore", "Widgets come back only on this phone") { PlainButton("Open") { ui.restore() } } },
                    )
                }
            }
        }
    }
}

@Composable
private fun LookPage(st: HomeState) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val packs = remember { Icons.packs(ctx) }
    val choices = listOf<IconPack?>(null) + packs
    val shared = remember(st.colourSource) { wallpaperSeed(ctx) != null }
    Rows(
        {
            Row3(
                "palette", "Colours",
                when {
                    st.colourSource == 0 -> "Samsung's palette, the same as your other apps"
                    st.colourSource == 1 && shared -> "Worked out from your wallpaper, like your desktop"
                    st.colourSource == 1 -> "Android won't share this wallpaper's colours, so your swatch is used"
                    else -> "Your swatch, whatever the wallpaper"
                },
                wide = true,
            ) { Segs(listOf("Wallpaper", "Swatch", "Samsung"), listOf(1, 2, 0).indexOf(st.colourSource)) { st.updateColourSource(listOf(1, 2, 0)[it]) } }
        },
        {
            Piece(Modifier.fillMaxWidth().height(64.dp), LocalRowShape.current, edge = true) {
                Row(
                    Modifier.fillMaxSize().padding(start = 58.dp, end = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    swatches.forEach { (c, _) ->
                        val on = st.swatch == c && st.colourSource != 0
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(c))
                                .then(if (on) Modifier.padding(3.dp).clip(CircleShape).background(pal.l3).padding(3.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(c)) else Modifier)
                                .then(Modifier.clickableNoRipple { st.updateSwatch(c); if (st.colourSource == 0) st.updateColourSource(2) }),
                        )
                    }
                }
            }
        },
        { Row3("apps", "App icons", "Themed tints every icon that supports it") { Segs(listOf("Original", "Themed"), st.iconStyle) { st.updateIcons(it, st.iconPack) } } },
        { Row3("style", "Icon pack", if (packs.isEmpty()) "Install one from the Play Store and it shows here" else "Apps it doesn't cover keep their own icon") {} },
        *choices.map { p ->
            @Composable {
                val chosen = st.iconPack == p?.pkg
                Piece(Modifier.fillMaxWidth().height(52.dp), LocalRowShape.current, edge = true, onClick = { st.updateIcons(st.iconStyle, p?.pkg) }) {
                    Row(Modifier.fillMaxSize().padding(start = 58.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Lbl(p?.label ?: "None, the apps' own", Modifier.weight(1f), color = if (chosen) pal.acc else pal.fg)
                        Radio(chosen)
                    }
                }
            }
        }.toTypedArray(),
    )
}

@Composable
private fun HomePage(st: HomeState, ui: Ui) {
    val pal = LocalPal.current
    Rows(
        {
            Row3("person", "Your name", "Shown in the greeting") {
                Box(
                    Modifier.width(140.dp).height(34.dp).clip(RoundedCornerShape(Rad.segment)).background(pal.l5).padding(horizontal = 10.dp),
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
        { Row3("grid_view", "Cover grid, across", "${GridSize.coverCols} × ${GridSize.coverRows}") { Segs(listOf("4", "5"), GridSize.coverCols - 4) { st.updateGrid(1, 4 + it, GridSize.coverRows) } } },
        { Row3("table_rows", "Cover grid, down", "") { Segs(listOf("5", "6", "7"), GridSize.coverRows - 5) { st.updateGrid(1, GridSize.coverCols, 5 + it) } } },
        { Row3("grid_view", "Inner grid, across", "${GridSize.innerCols} × ${GridSize.innerRows}", wide = true) { Segs(listOf("6", "7", "8", "9"), GridSize.innerCols - 6) { st.updateGrid(0, 6 + it, GridSize.innerRows) } } },
        { Row3("table_rows", "Inner grid, down", "") { Segs(listOf("4", "5", "6"), GridSize.innerRows - 4) { st.updateGrid(0, GridSize.innerCols, 4 + it) } } },
        { Row3("label", "App names", "Under icons on the home screen") { MiniSwitch(st.labels) { st.updateLabels(!st.labels) } } },
        { Row3("circle_notifications", "Notification dots", "On apps with notifications") { MiniSwitch(st.dots) { st.updateDots(!st.dots) } } },
        { Row3("history", "Recent apps in the dock", "The dock's middle piece, inner screen") { MiniSwitch(st.dockRecentsOn) { st.updateDockRecents(!st.dockRecentsOn) } } },
        { Row3("lock", "Lock the layout", "Nothing moves or gets removed until you unlock it") { MiniSwitch(st.locked) { st.updateLocked(!st.locked) } } },
        { Row3("edit", "Edit the home screen", "Or hold an empty spot") { PlainButton("Edit") { ui.close(); ui.editing = true } } },
    )
}

private val LocalRowShape = androidx.compose.runtime.compositionLocalOf<Shape> { RoundedCornerShape(Rad.joined) }

/** Rows joined into one block: small corners between them, a big one at the very end. */
@Composable
private fun Rows(vararg rows: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEachIndexed { i, r ->
            val end = if (i == rows.lastIndex) Rad.normal else Rad.joined
            androidx.compose.runtime.CompositionLocalProvider(LocalRowShape provides corners(Rad.joined, Rad.joined, end, end)) { r() }
        }
    }
}

/** A settings row. [wide] controls (several choices) sit under the text so it never gets squeezed. */
@Composable
private fun Row3(glyph: String, title: String, sub: String, wide: Boolean = false, trailing: @Composable () -> Unit) {
    val pal = LocalPal.current
    Piece(Modifier.fillMaxWidth().heightIn(min = 64.dp), LocalRowShape.current, edge = true) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SqIcon(glyph, false)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Lbl(title, size = 14.sp)
                    if (sub.isNotEmpty()) Lbl(sub, size = 12.sp, color = pal.sub, maxLines = 3)
                }
                if (!wide) {
                    Spacer(Modifier.width(10.dp))
                    trailing()
                }
            }
            if (wide) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.padding(start = 44.dp)) { trailing() }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppEntry, trailing: @Composable () -> Unit) {
    Piece(Modifier.fillMaxWidth().height(58.dp), LocalRowShape.current, edge = true) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app, 34.dp)
            Spacer(Modifier.width(12.dp))
            Lbl(app.label, Modifier.weight(1f))
            trailing()
        }
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
        } else AccentButton("Allow", height = 32.dp, onClick = onClick)
    }
}

@Composable
private fun Segs(options: List<String>, chosen: Int, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { i, o -> Seg(o, i == chosen) { onPick(i) } }
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    pointerInput(onClick) { detectTapGestures { onClick() } }

@Composable
private fun Radio(on: Boolean) {
    val pal = LocalPal.current
    Box(Modifier.size(20.dp).clip(CircleShape).background(if (on) pal.acc else pal.l5), contentAlignment = Alignment.Center) {
        if (on) Box(Modifier.size(8.dp).clip(CircleShape).background(pal.ink))
    }
}
