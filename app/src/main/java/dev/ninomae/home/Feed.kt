package dev.ninomae.home

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter

/* The widgets page (M3a · Joined): the date and weather, a rose "up next" card joined to the player, then your widgets. */

/** Feed widgets reuse HomeItem: y is the order, w is 1 (half width) or 2 (full), h the height in rows of [FEED_ROW] dp. */
const val FEED_ROW = 92

/** Before half widths existed every feed widget was full width (stored as w = 1), so old lists are widened once. */
fun loadFeed(prefs: SharedPreferences): List<HomeItem> {
    val items = loadHome(prefs, "feedItems")
    if (prefs.getBoolean("feedWidths", false)) return items
    val wide = items.map { it.copy(w = 2) }
    prefs.edit().putString("feedItems", homeToJson(wide)).putBoolean("feedWidths", true).apply()
    return wide
}

fun HomeState.saveFeed() = prefs.edit().putString("feedItems", homeToJson(feed)).apply()

private fun HomeState.commitFeed(items: List<HomeItem>) {
    feed = items.sortedBy { it.y }.mapIndexed { i, it -> it.copy(y = i) }
    saveFeed()
}

fun HomeState.addFeedAndroidWidget(widgetId: Int, w: Int, rows: Int) =
    commitFeed(feed + HomeItem(newId(), -1, 0, 0, feed.size, w.coerceIn(1, 2), rows.coerceIn(1, 5), type = "widget", widgetId = widgetId))

fun HomeState.addFeedNinomae(kind: String) {
    val rows = when (kind) { "stack" -> 3; else -> 1 }
    commitFeed(feed + HomeItem(newId(), -1, 0, 0, feed.size, 2, rows, type = "nwidget", kind = kind))
}

/** Move a widget one place up (-1) or down (+1). */
fun HomeState.moveFeed(id: String, dir: Int) {
    val i = feed.indexOfFirst { it.id == id }
    val j = i + dir
    if (i < 0 || j !in feed.indices) return
    val list = feed.toMutableList()
    val a = list[i]
    list[i] = list[j].copy(y = i)
    list[j] = a.copy(y = j)
    commitFeed(list)
}

fun HomeState.resizeFeed(id: String, dRows: Int) =
    commitFeed(feed.map { if (it.id == id) it.copy(h = (it.h + dRows).coerceIn(1, 5)) else it })

fun HomeState.widenFeed(id: String) =
    commitFeed(feed.map { if (it.id == id) it.copy(w = if (it.w == 1) 2 else 1) else it })

fun HomeState.removeFeed(id: String) {
    val item = feed.firstOrNull { it.id == id } ?: return
    if (item.type == "widget") AppWidgets.delete(item.widgetId)
    commitFeed(feed.filter { it.id != id })
}

/** Half-width widgets next to each other share a row; everything else gets its own. */
private fun feedRows(items: List<HomeItem>): List<List<HomeItem>> {
    val out = mutableListOf<List<HomeItem>>()
    var i = 0
    while (i < items.size) {
        val a = items[i]
        val b = items.getOrNull(i + 1)
        if (a.w == 1 && b != null && b.w == 1) { out += listOf(a, b); i += 2 } else { out += listOf(a); i++ }
    }
    return out
}

private val Big = 26.dp
private val Small = 6.dp

@Composable
fun FeedPage(st: HomeState, ui: Ui, compact: Boolean) {
    val pal = LocalPal.current
    val now = rememberNow()
    var editing by remember { mutableStateOf(false) }
    LaunchedEffect(ui.page) { if (ui.page >= 0) editing = false }
    val rows = remember(st.feed) { feedRows(st.feed) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        contentPadding = PaddingValues(top = 6.dp, bottom = 16.dp),
    ) {
        item(key = "date") {
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Shadowed(now.format(DateTimeFormatter.ofPattern("d MMMM")), 30.sp, pal.fg, FontWeight.Black, TitleFont)
                    Shadowed(now.format(DateTimeFormatter.ofPattern("EEEE")), 14.sp, pal.sub, FontWeight.Medium, Sans)
                }
                Glance.weather?.let { w ->
                    Row(
                        Modifier.height(40.dp).frosted(st, RoundedCornerShape(20.dp), wash = 0.35f, edge = 0.2f).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Glyph(w.glyph, size = 18.dp, color = pal.acc, fill = true)
                        Spacer(Modifier.width(7.dp))
                        Num(w.temp, size = 16.sp)
                        Spacer(Modifier.width(7.dp))
                        Lbl(w.text, size = 12.sp, color = pal.sub)
                    }
                }
            }
        }
        item(key = "today") { TodayBlock(st) }
        item(key = "head") {
            Row(Modifier.fillMaxWidth().padding(start = 6.dp, top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Shadowed("Widgets", 17.sp, pal.fg, FontWeight.Black, TitleFont)
                Spacer(Modifier.weight(1f))
                if (st.feed.isNotEmpty()) {
                    GlassSquare(if (editing) "check" else "edit", accent = editing) { editing = !editing }
                    Spacer(Modifier.width(5.dp))
                }
                GlassSquare("add", accent = !editing) { ui.pickForFeed = true; ui.layer = Layer.Widgets }
            }
        }
        items(rows, key = { it.first().id }) { row ->
            val tall = row.maxOf { it.h }
            Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEachIndexed { i, item ->
                    val shape = when {
                        row.size == 1 -> RoundedCornerShape(Big)
                        i == 0 -> corners(Big, Small, Small, Big)
                        else -> corners(Small, Big, Big, Small)
                    }
                    val width = if (row.size == 2 || item.w == 2) Modifier.weight(1f) else Modifier.fillMaxWidth(0.5f)
                    Column(width, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // The calendar draws its own joined glass pieces and takes the height it needs.
                        if (item.type == "nwidget" && item.kind == "calendar") CalendarWidget(st, ui, Modifier.fillMaxWidth(), fit = false)
                        else BoxWithConstraints(Modifier.fillMaxWidth().height((FEED_ROW * tall).dp).frosted(st, shape, wash = 0.30f, edge = 0.2f)) {
                            when (item.type) {
                                "widget" -> AndroidWidget(item.widgetId, maxWidth.value, maxHeight.value, Modifier.clip(shape))
                                "nwidget" -> NinomaeWidget(st, ui, item.kind)
                            }
                        }
                        if (editing) FeedTools(st, item, labels = row.size == 1 && item.w == 2 && !compact)
                    }
                }
            }
        }
        if (st.feed.isEmpty()) item(key = "empty") {
            Row(
                Modifier.fillMaxWidth().height(64.dp).frosted(st, RoundedCornerShape(Big), wash = 0.5f, edge = 0.3f)
                    .clickable { ui.pickForFeed = true; ui.layer = Layer.Widgets },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph("add", size = 22.dp, color = pal.acc)
                Spacer(Modifier.width(6.dp))
                Lbl("Add a widget", size = 14.sp, weight = FontWeight.Medium)
            }
        }
    }
}

/** The rose "up next" card joined on top of the player. Either can be missing; then the other gets all its corners. */
@Composable
private fun TodayBlock(st: HomeState) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val now = rememberNow()
    val media by Media.now.collectAsState()
    val alarm = remember(now.minute) { ctx.getSystemService(android.app.AlarmManager::class.java)?.nextAlarmClock?.triggerTime }
        ?.takeIf { it - System.currentTimeMillis() < 24 * 3600_000L }
    val events = Glance.today
    val showCard = events.isNotEmpty() || alarm != null || st.glanceCalendar
    val m = media
    if (!showCard && m == null) return

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showCard) {
            val shape = if (m != null) corners(Big, Big, Small, Small) else RoundedCornerShape(Big)
            Column(
                Modifier.fillMaxWidth().clip(shape).background(pal.acc).clickable { Actions.calendar(ctx) }.padding(16.dp),
            ) {
                val next = events.firstOrNull()
                val alarmText = alarm?.let {
                    val t = android.text.format.DateFormat.getTimeFormat(ctx).format(java.util.Date(it))
                    val day = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    if (day != now.toLocalDate()) "$t  Alarm, tomorrow" else "$t  Alarm"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph("event", size = 20.dp, color = pal.ink, fill = true)
                    Spacer(Modifier.width(8.dp))
                    Lbl(if (next != null) "Up next" else "Today", Modifier.weight(1f), size = 12.sp, color = pal.ink, weight = FontWeight.Bold)
                    next?.takeIf { it.begin > 0 }?.let { e ->
                        val mins = ((e.begin - System.currentTimeMillis()) / 60_000).toInt()
                        val note = when {
                            mins < 1 -> "now"
                            mins < 60 -> "in $mins min"
                            mins < 180 -> "in ${mins / 60} h ${mins % 60} min"
                            else -> null
                        }
                        note?.let { Lbl(it, size = 12.sp, color = pal.ink, weight = FontWeight.Bold) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Lbl(next?.title ?: "Nothing planned", Modifier.weight(1f, fill = false), size = 26.sp, color = pal.ink, weight = FontWeight.Black, family = TitleFont)
                    next?.let { e ->
                        Spacer(Modifier.width(14.dp))
                        Lbl(if (e.end != null) "${e.time} – ${e.end}" else e.time, size = 13.sp, color = pal.ink, weight = FontWeight.Medium)
                    }
                }
                val chips = buildList {
                    events.drop(1).take(if (alarmText != null) 1 else 2).forEach { add("event" to "${it.time}  ${it.title}") }
                    alarmText?.let { add("alarm" to it) }
                }
                if (chips.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        chips.forEach { (g, t) ->
                            Row(
                                Modifier.weight(1f, fill = false).height(30.dp).clip(RoundedCornerShape(15.dp)).background(pal.ink.copy(alpha = 0.12f))
                                    .clickable { if (g == "alarm") Actions.clock(ctx) else Actions.calendar(ctx) }.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Glyph(g, size = 14.dp, color = pal.ink)
                                Spacer(Modifier.width(6.dp))
                                Lbl(t, size = 12.sp, color = pal.ink, weight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
        if (m != null) Player(st, m, if (showCard) corners(Small, Small, Big, Big) else RoundedCornerShape(Big))
    }
}

@Composable
private fun Player(st: HomeState, m: MediaNow, shape: Shape) {
    val pal = LocalPal.current
    Row(
        Modifier.fillMaxWidth().height(84.dp).frosted(st, shape, wash = 0.30f, edge = 0.2f)
            .background(pal.acc.copy(alpha = 0.10f)).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(m.art, 64.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Lbl(m.title, size = 15.sp, weight = FontWeight.SemiBold)
            Lbl(m.artist, size = 12.5.sp, color = pal.sub)
        }
        Glyph("skip_previous", Modifier.clip(RoundedCornerShape(12.dp)).clickable { Media.prev() }.padding(6.dp), size = 22.dp, fill = true)
        Box(
            Modifier.padding(horizontal = 2.dp).size(42.dp).clip(RoundedCornerShape(14.dp)).background(pal.acc).clickable { Media.toggle() },
            contentAlignment = Alignment.Center,
        ) { Glyph(if (m.playing) "pause" else "play_arrow", size = 22.dp, color = pal.ink, fill = true) }
        Glyph("skip_next", Modifier.clip(RoundedCornerShape(12.dp)).clickable { Media.next() }.padding(6.dp), size = 22.dp, fill = true)
    }
}

/** Under a widget in edit mode: move, size, width and remove, joined. Labels only when there's room. */
@Composable
private fun FeedTools(st: HomeState, item: HomeItem, labels: Boolean) {
    val pal = LocalPal.current
    val tools = listOf(
        Triple("arrow_upward", "Up") { st.moveFeed(item.id, -1) },
        Triple("arrow_downward", "Down") { st.moveFeed(item.id, 1) },
        Triple("unfold_more", "Taller") { st.resizeFeed(item.id, 1) },
        Triple("unfold_less", "Shorter") { st.resizeFeed(item.id, -1) },
        Triple(if (item.w == 1) "width_full" else "width_normal", if (item.w == 1) "Wide" else "Half") { st.widenFeed(item.id) },
        Triple("delete", "Remove") { st.removeFeed(item.id) },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        tools.forEachIndexed { i, (g, t, go) ->
            val l = if (i == 0) 18.dp else Small
            val r = if (i == tools.lastIndex) 18.dp else Small
            Row(
                Modifier.weight(1f).height(36.dp).frosted(st, corners(l, r, r, l), wash = 0.45f, edge = 0.16f).clickable(onClick = go),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(g, size = 16.dp, color = if (g == "delete") Color(0xFFE8A39B) else pal.acc)
                if (labels) { Spacer(Modifier.width(4.dp)); Lbl(t, size = 11.sp) }
            }
        }
    }
}

/** Text straight on the wallpaper, with a soft shadow so it reads on light and dark parts. */
@Composable
private fun Shadowed(text: String, size: TextUnit, color: Color, weight: FontWeight, family: FontFamily) {
    androidx.compose.foundation.text.BasicText(
        text,
        style = TextStyle(
            color = color, fontSize = size, fontWeight = weight, fontFamily = family,
            shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1.5f), 8f),
        ),
        maxLines = 1,
    )
}
