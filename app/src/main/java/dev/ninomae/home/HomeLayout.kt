package dev.ninomae.home

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * Something placed on a home screen page.
 * type: app, folder, widget (Android), nwidget (Ninomae: stack, greeting, player, jump, clock), shortcut.
 */
data class HomeItem(
    val id: String,
    val screen: Int,
    val page: Int,
    val x: Int,
    val y: Int,
    val w: Int = 1,
    val h: Int = 1,
    val type: String,
    val app: String? = null,
    val apps: List<String> = emptyList(),
    val name: String = "",
    val widgetId: Int = 0,
    val kind: String = "",
    val shortcutId: String = "",
    val pkg: String = "",
    val userSerial: Long = 0,
) {
    val resizable get() = type == "widget" || type == "nwidget" || type == "stack" || type == "folder"
    val isWidget get() = type == "widget" || type == "nwidget" || type == "stack"

    /** A stack's widgets: "w:<id>" for Android widgets, "n:<kind>" for Ninomae's. */
    val stackParts: List<String>
        get() = when (type) {
            "stack" -> apps
            "widget" -> listOf("w:$widgetId")
            "nwidget" -> listOf("n:$kind")
            else -> emptyList()
        }
}

/** Grid size per screen (settings): the inner screen is a desktop, the cover a phone. */
object GridSize {
    var innerCols by androidx.compose.runtime.mutableIntStateOf(8)
    var innerRows by androidx.compose.runtime.mutableIntStateOf(5)
    var coverCols by androidx.compose.runtime.mutableIntStateOf(4)
    var coverRows by androidx.compose.runtime.mutableIntStateOf(6)
}

fun gridCols(screen: Int) = if (screen == 0) GridSize.innerCols else GridSize.coverCols
fun gridRows(screen: Int) = if (screen == 0) GridSize.innerRows else GridSize.coverRows

fun loadHome(prefs: SharedPreferences, key: String = "homeItems"): List<HomeItem> = runCatching {
    val a = JSONArray(prefs.getString(key, "[]")!!)
    (0 until a.length()).map {
        val o = a.getJSONObject(it)
        val apps = o.optJSONArray("apps")
        HomeItem(
            id = o.getString("id"),
            screen = o.getInt("screen"),
            page = o.getInt("page"),
            x = o.getInt("x"),
            y = o.getInt("y"),
            w = o.optInt("w", 1),
            h = o.optInt("h", 1),
            type = o.getString("type"),
            app = o.optString("app").ifEmpty { null },
            apps = if (apps == null) emptyList() else (0 until apps.length()).map { i -> apps.getString(i) },
            name = o.optString("name"),
            widgetId = o.optInt("widgetId"),
            kind = o.optString("kind"),
            shortcutId = o.optString("shortcutId"),
            pkg = o.optString("pkg"),
            userSerial = o.optLong("userSerial"),
        )
    }
}.getOrDefault(emptyList())

fun homeToJson(items: List<HomeItem>): String {
    val a = JSONArray()
    items.forEach {
        a.put(
            JSONObject()
                .put("id", it.id).put("screen", it.screen).put("page", it.page)
                .put("x", it.x).put("y", it.y).put("w", it.w).put("h", it.h)
                .put("type", it.type).put("app", it.app ?: "")
                .put("apps", JSONArray(it.apps)).put("name", it.name)
                .put("widgetId", it.widgetId).put("kind", it.kind)
                .put("shortcutId", it.shortcutId).put("pkg", it.pkg).put("userSerial", it.userSerial),
        )
    }
    return a.toString()
}

// ---------- queries ----------

fun HomeState.pageCount(screen: Int): Int =
    (home.filter { it.screen == screen }.maxOfOrNull { it.page } ?: 0) + 1

fun HomeState.itemAt(screen: Int, page: Int, x: Int, y: Int, ignore: String? = null): HomeItem? =
    home.firstOrNull {
        it.id != ignore && it.screen == screen && it.page == page &&
            x >= it.x && x < it.x + it.w && y >= it.y && y < it.y + it.h
    }

fun HomeState.fits(screen: Int, page: Int, x: Int, y: Int, w: Int, h: Int, ignore: String? = null): Boolean {
    if (x < 0 || y < 0 || x + w > gridCols(screen) || y + h > gridRows(screen)) return false
    for (cx in x until x + w) for (cy in y until y + h) if (itemAt(screen, page, cx, cy, ignore) != null) return false
    return true
}

/** First free spot of w×h, starting on [startPage]; a new page if they're all full. */
fun HomeState.firstFree(screen: Int, w: Int, h: Int, startPage: Int = 0): Triple<Int, Int, Int> {
    val pages = pageCount(screen)
    for (p in (startPage until pages) + (0 until startPage)) {
        for (y in 0..gridRows(screen) - h) for (x in 0..gridCols(screen) - w) {
            if (fits(screen, p, x, y, w, h)) return Triple(p, x, y)
        }
    }
    return Triple(pages, 0, 0)
}

// ---------- changes ----------

internal fun HomeState.commit(items: List<HomeItem>) {
    // Pages left empty in the middle close up.
    val fixed = (0..1).flatMap { screen ->
        val mine = items.filter { it.screen == screen }
        val used = mine.map { it.page }.distinct().sorted()
        mine.map { it.copy(page = used.indexOf(it.page)) }
    }
    home = fixed
    saveHome()
}

fun HomeState.addAppToHome(key: String, screen: Int, page: Int = 0) {
    if (home.any { it.screen == screen && it.type == "app" && it.app == key }) return
    val (p, x, y) = firstFree(screen, 1, 1, page)
    commit(home + HomeItem(newId(), screen, p, x, y, type = "app", app = key))
}

fun HomeState.placeAt(screen: Int, page: Int, x: Int, y: Int, item: HomeItem) {
    commit(home.filter { it.id != item.id } + item.copy(screen = screen, page = page, x = x, y = y))
}

fun HomeState.removeItem(id: String) {
    val item = home.firstOrNull { it.id == id } ?: return
    item.stackParts.filter { it.startsWith("w:") }.forEach { AppWidgets.delete(it.drop(2).toIntOrNull() ?: 0) }
    commit(home.filter { it.id != id })
}

/** Drop one widget onto another: they become a stack you swipe through, at the target's size. */
fun HomeState.stackInto(target: HomeItem, src: HomeItem) {
    val parts = (target.stackParts + src.stackParts).distinct()
    val stack = target.copy(type = "stack", apps = parts, widgetId = 0, kind = "")
    commit(home.filter { it.id != src.id && it.id != target.id } + stack)
}

/** Take one widget back out of a stack, into the first free space. */
fun HomeState.unstack(stackId: String, part: String) {
    val s = home.firstOrNull { it.id == stackId } ?: return
    val left = s.apps - part
    fun single(p: String, base: HomeItem) = if (p.startsWith("w:"))
        base.copy(type = "widget", widgetId = p.drop(2).toIntOrNull() ?: 0, kind = "", apps = emptyList())
    else base.copy(type = "nwidget", kind = p.drop(2), widgetId = 0, apps = emptyList())
    val rest = when (left.size) {
        0 -> null
        1 -> single(left.first(), s)
        else -> s.copy(apps = left)
    }
    val others = home.filter { it.id != stackId } + listOfNotNull(rest)
    home = others
    val (p, x, y) = firstFree(s.screen, s.w, s.h, s.page)
    commit(others + single(part, s.copy(id = newId(), page = p, x = x, y = y)))
}

/** After the grid shrinks, anything that no longer fits moves to the first free space. */
fun HomeState.reflow() {
    val kept = mutableListOf<HomeItem>()
    val moved = mutableListOf<HomeItem>()
    home.forEach { if (it.x + it.w <= gridCols(it.screen) && it.y + it.h <= gridRows(it.screen)) kept += it else moved += it }
    if (moved.isEmpty()) return
    home = kept
    moved.forEach { m ->
        val w = m.w.coerceAtMost(gridCols(m.screen))
        val h = m.h.coerceAtMost(gridRows(m.screen))
        val (p, x, y) = firstFree(m.screen, w, h, m.page)
        home = home + m.copy(page = p, x = x, y = y, w = w, h = h)
    }
    commit(home)
}

/** Move an item's edges: [dx]/[dy] shift its corner, [dw]/[dh] change its size. Only if it still fits. */
fun HomeState.reshape(id: String, dx: Int, dy: Int, dw: Int, dh: Int) {
    val it = home.firstOrNull { i -> i.id == id } ?: return
    val x = it.x + dx
    val y = it.y + dy
    val w = it.w + dw
    val h = it.h + dh
    if (w < 1 || h < 1 || x < 0 || y < 0 || x + w > gridCols(it.screen) || y + h > gridRows(it.screen)) return
    if (fits(it.screen, it.page, x, y, w, h, ignore = id)) commit(home.map { i -> if (i.id == id) i.copy(x = x, y = y, w = w, h = h) else i })
}

/** Drop [key] onto [target]: two apps make a folder, an app joins a folder. */
fun HomeState.mergeInto(target: HomeItem, key: String, removeId: String?) {
    val rest = home.filter { it.id != removeId && it.id != target.id }
    val merged = when (target.type) {
        "folder" -> target.copy(apps = (target.apps + key).distinct())
        "app" -> target.copy(type = "folder", apps = listOf(target.app!!, key).distinct(), app = null, name = "Folder")
        else -> return
    }
    commit(rest + merged)
}

fun HomeState.renameFolder(id: String, name: String) =
    commit(home.map { if (it.id == id) it.copy(name = name) else it })

fun HomeState.removeFromFolder(folderId: String, key: String) {
    val f = home.firstOrNull { it.id == folderId } ?: return
    val left = f.apps - key
    val next = when (left.size) {
        0 -> null
        1 -> f.copy(type = "app", app = left.first(), apps = emptyList(), name = "")
        else -> f.copy(apps = left)
    }
    commit(home.filter { it.id != folderId } + listOfNotNull(next))
}

fun HomeState.addNinomaeWidget(kind: String, screen: Int, page: Int, size: Pair<Int, Int>? = null) {
    val (w, h) = size ?: ninomaeSize(kind, screen)
    val (p, x, y) = firstFree(screen, w, h, page)
    commit(home + HomeItem(newId(), screen, p, x, y, w, h, type = "nwidget", kind = kind))
}

fun HomeState.addAndroidWidget(widgetId: Int, screen: Int, page: Int, w: Int, h: Int) {
    val cw = w.coerceIn(1, gridCols(screen))
    val ch = h.coerceIn(1, gridRows(screen))
    val (p, x, y) = firstFree(screen, cw, ch, page)
    commit(home + HomeItem(newId(), screen, p, x, y, cw, ch, type = "widget", widgetId = widgetId))
}

fun HomeState.addShortcut(id: String, pkg: String, serial: Long, label: String, screen: Int) {
    val (p, x, y) = firstFree(screen, 1, 1, 0)
    commit(home + HomeItem(newId(), screen, p, x, y, type = "shortcut", shortcutId = id, pkg = pkg, userSerial = serial, name = label))
}

fun ninomaeSize(kind: String, screen: Int): Pair<Int, Int> = when (kind) {
    "stack" -> if (screen == 0) 4 to 3 else 4 to 3
    "clock" -> 4 to 1
    "calendar" -> 4 to 4
    "photos" -> 4 to 2
    else -> 4 to 1
}

val ninomaeWidgets = listOf(
    Triple("stack", "Ninomae stack", "Greeting, player and jump back in, joined"),
    Triple("greeting", "Greeting", "Your name, the date, battery and uptime"),
    Triple("player", "Player", "What's playing, tinted by the cover"),
    Triple("jump", "Jump back in", "Your pairs and recent apps"),
    Triple("calendar", "Calendar", "This month and what's coming up"),
    Triple("photos", "Photos", "On this day, favourites or random, from your Immich"),
    Triple("clock", "Clock", "A big clock and the date, right on the wallpaper"),
)
