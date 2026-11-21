package dev.ninomae.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class AppEntry(
    val key: String,
    val label: String,
    val original: String,
    val pkg: String,
    val component: ComponentName,
    val user: UserHandle,
    val icon: ImageBitmap,
    val tint: Color,
    val isGame: Boolean,
    /** The app's monochrome layer, for themed icons. */
    val mono: ImageBitmap? = null,
    val installed: Long = 0,
    /** Android's category for the app (ApplicationInfo.CATEGORY_*), for the drawer's groups. */
    val category: Int = -1,
)

data class AppPair(val id: String, val name: String, val left: String, val right: String)

/** Everything the home screen shows, kept in Compose state and saved to prefs. */
class HomeState private constructor(private val ctx: Context) {
    companion object {
        @Volatile internal var inst: HomeState? = null
        fun get(c: Context): HomeState = inst ?: synchronized(this) {
            inst ?: HomeState(c.applicationContext).also { inst = it }
        }
    }

    val prefs = ctx.getSharedPreferences("home", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val launcherApps = ctx.getSystemService(LauncherApps::class.java)
    private val users = ctx.getSystemService(UserManager::class.java)

    var apps by mutableStateOf(emptyList<AppEntry>())
        private set
    val byKey by derivedStateOf { apps.associateBy { it.key } }
    val byPkg by derivedStateOf { apps.groupBy { it.pkg }.mapValues { it.value.first() } }

    var pinned by mutableStateOf(list("pinned"))
        private set
    var recent by mutableStateOf(list("recent"))
        private set
    var pairs by mutableStateOf(loadPairs())
        private set
    var launches by mutableStateOf(loadCounts())
        private set
    var activePair by mutableStateOf(prefs.getString("activePair", null))
        private set

    var name by mutableStateOf(prefs.getString("name", "") ?: "")
        private set

    /** Home screen items for both screens (0 inner, 1 cover). */
    var home by mutableStateOf(loadHome(prefs))
    var hidden by mutableStateOf(list("hidden").toSet())
        private set
    var renames by mutableStateOf(loadMap("renames"))
        private set

    var labels by mutableStateOf(prefs.getBoolean("labels", true))
        private set
    var dots by mutableStateOf(prefs.getBoolean("dots", true))
        private set
    /** The wallpaper, blurred, for the glass drawer (only when it was set from Ninomae). */
    /** The widgets page left of the first home page: on or off, and the widgets on it, top to bottom. */
    var feedOn by mutableStateOf(prefs.getBoolean("feedOn", true))
        private set
    var feed by mutableStateOf(loadFeed(prefs))
    fun updateFeedOn(v: Boolean) { feedOn = v; prefs.edit().putBoolean("feedOn", v).apply() }
    var glass by mutableStateOf(Glass.load(ctx))
        private set
    var wallPreview by mutableStateOf(Glass.loadPreview(ctx))
        private set
    /** The main colour of the wallpaper set from Ninomae (0 = none), used when Android won't share one. */
    var wallSeed by mutableIntStateOf(prefs.getInt("wallSeed", 0))
        private set

    fun setWallpaper(w: Glass.Set) {
        glass = w.frosted
        wallPreview = w.preview
        wallSeed = w.seed
        glassStale = false
        glassNote = false
        prefs.edit().putInt("wallSeed", w.seed).putInt("wallId", w.id).putBoolean("glassStale", false).putBoolean("glassNote", false).apply()
    }

    /** The wallpaper was changed outside Ninomae, so the glass went plain (G1b): Look keeps a dot until it's picked here. */
    var glassStale by mutableStateOf(prefs.getBoolean("glassStale", false))
        private set
    /** The one-time note above the dock about it. */
    var glassNote by mutableStateOf(prefs.getBoolean("glassNote", false))
        private set

    fun dismissGlassNote() { glassNote = false; prefs.edit().putBoolean("glassNote", false).apply() }

    /** On resume: has another app (Samsung's wallpaper picker, say) set a new wallpaper since ours? */
    fun checkWallpaper() {
        if (glass == null && wallPreview == null) return
        val id = Glass.currentId(ctx)
        if (id < 0) return
        val known = prefs.getInt("wallId", -1)
        // Wallpapers set before ids were kept: take the current one as ours.
        if (known == -1) { android.util.Log.i("Ninomae", "wallpaper id $id kept as ours"); prefs.edit().putInt("wallId", id).apply(); return }
        if (id == known) return
        android.util.Log.i("Ninomae", "wallpaper changed outside Ninomae ($known -> $id): glass goes plain")
        Glass.forget(ctx)
        glass = null
        wallPreview = null
        glassStale = true
        glassNote = true
        prefs.edit().putInt("wallId", id).putBoolean("glassStale", true).putBoolean("glassNote", true).apply()
    }
    var dockRecentsOn by mutableStateOf(prefs.getBoolean("dockRecents", true))
        private set
    /** 0 the deck, 1 Samsung's notification panel, 2 nothing. Until chosen, the cover follows its bar: ours → the deck. */
    var swipeDown by mutableIntStateOf(prefs.getInt("swipeDownCover", if (prefs.getBoolean("barCover", false)) 0 else 1))
        private set
    /** Same choice for the inner screen. Cover: Samsung's panel by default; inner: the deck. */
    var swipeDownInner by mutableIntStateOf(prefs.getInt("swipeDownInner2", 0))
        private set
    var doubleTapLock by mutableStateOf(prefs.getBoolean("doubleTapLock", true))
        private set
    /** The edge sidebar (V2a): slide up along an edge of the home screen for its own list of apps. */
    var edgeOn by mutableStateOf(prefs.getBoolean("edgeOn", true))
        private set
    /** Which edge: true right, false left. */
    var edgeRight by mutableStateOf(prefs.getBoolean("edgeRight", true))
        private set
    var edgeApps by mutableStateOf(list("edgeApps"))
        private set
    /** Immich for the photo widget: the server's address, your API key, and what to show (0 on this day, 1 favourites, 2 random). */
    var immichUrl by mutableStateOf(prefs.getString("immichUrl", "") ?: "")
        private set
    var immichKey by mutableStateOf(prefs.getString("immichKey", "") ?: "")
        private set
    var immichMode by mutableIntStateOf(prefs.getInt("immichMode", 0))
        private set
    fun updateImmich(url: String = immichUrl, key: String = immichKey, mode: Int = immichMode) {
        immichUrl = url; immichKey = key; immichMode = mode
        prefs.edit().putString("immichUrl", url).putString("immichKey", key).putInt("immichMode", mode).apply()
    }
    /** Look: the icon shape (see iconShapeOf), how dark the glass is (×, 1 as designed) and how frosted (0 light, 1 medium, 2 heavy). */
    var iconShape by mutableIntStateOf(prefs.getInt("iconShape", 0))
        private set
    fun updateIconShape(v: Int) { iconShape = v; prefs.edit().putInt("iconShape", v).apply() }
    var glassTint by androidx.compose.runtime.mutableFloatStateOf(prefs.getFloat("glassTint", 1f))
        private set
    fun updateGlassTint(v: Float) { glassTint = v; prefs.edit().putFloat("glassTint", v).apply() }
    var glassFrost by mutableIntStateOf(prefs.getInt("glassFrost", 1))
        private set
    fun updateGlassFrost(v: Int) {
        glassFrost = v
        prefs.edit().putInt("glassFrost", v).apply()
        scope.launch { Glass.refrost(ctx, v)?.let { glass = it } }
    }
    fun updateEdgeOn(v: Boolean) { edgeOn = v; prefs.edit().putBoolean("edgeOn", v).apply() }
    fun updateEdgeRight(v: Boolean) { edgeRight = v; prefs.edit().putBoolean("edgeRight", v).apply() }
    /** Where it opens: at your thumb (false) or always in the middle of the screen (true). */
    var edgeMiddle by mutableStateOf(prefs.getBoolean("edgeMiddle", false))
        private set
    fun updateEdgeMiddle(v: Boolean) { edgeMiddle = v; prefs.edit().putBoolean("edgeMiddle", v).apply() }
    fun isOnEdge(key: String) = key in edgeApps
    fun toggleEdge(key: String) { edgeApps = if (key in edgeApps) edgeApps - key else edgeApps + key; saveList("edgeApps", edgeApps) }
    /** Move an app in the edge list one place up (-1) or down (+1). */
    fun moveEdge(key: String, dir: Int) {
        val i = edgeApps.indexOf(key); val j = i + dir
        if (i < 0 || j !in edgeApps.indices) return
        edgeApps = edgeApps.toMutableList().apply { add(j, removeAt(i)) }
        saveList("edgeApps", edgeApps)
    }
    var searchCalendar by mutableStateOf(prefs.getBoolean("searchCalendar", true))
        private set
    var searchFiles by mutableStateOf(prefs.getBoolean("searchFiles", true))
        private set
    /** Your own drawer tags (T1): an app can have several; they sit in the drawer's rail. */
    var tags by mutableStateOf(loadTags(prefs))
        private set
    fun saveTags(list: List<DrawerTag>) { tags = list; prefs.edit().putString("drawerTags", tagsToJson(list)).apply() }
    var searchContacts by mutableStateOf(prefs.getBoolean("searchContacts", true))
        private set
    /** Locked: nothing on the home screen moves until it's unlocked. */
    var locked by mutableStateOf(prefs.getBoolean("locked", false))
        private set
    var glanceCalendar by mutableStateOf(prefs.getBoolean("glanceCalendar", true))
        private set
    /** Off until asked for: it sends a rough location to Open-Meteo. */
    var glanceWeather by mutableStateOf(prefs.getBoolean("glanceWeather", false))
        private set
    /** 0 Samsung's palette, 1 the wallpaper (or the swatch when Android won't share it), 2 the swatch. */
    var colourSource by mutableIntStateOf(prefs.getInt("colourSource", 1))
        private set
    var swatch by mutableIntStateOf(prefs.getInt("swatch", 0xFF9B7BC0.toInt()))
        private set
    /** Ninomae's bar (true) or Samsung's status bar, per screen. The cover is a phone, so Samsung's by default. */
    var barCover by mutableStateOf(prefs.getBoolean("barCover", false))
        private set
    var barInner by mutableStateOf(prefs.getBoolean("barInner", true))
        private set
    /** Which screen was last showing, for things added from outside (shortcuts from other apps). */
    var lastScreen = 0

    var notifAccess by mutableStateOf(false)
        private set
    var a11yOn by mutableStateOf(false)
        private set
    var canWrite by mutableStateOf(false)
        private set
    var isDefaultHome by mutableStateOf(false)
        private set

    fun app(key: String?): AppEntry? = key?.let { byKey[it] }

    // ---------- apps ----------

    private var callbackSet = false
    private var loadJob: kotlinx.coroutines.Job? = null

    /** Rendered icons, kept until the app updates or the icon style changes. */
    private data class Rendered(val stamp: String, val icon: ImageBitmap, val tint: Color, val mono: ImageBitmap?, val installed: Long)
    private val rendered = java.util.concurrent.ConcurrentHashMap<String, Rendered>()

    init {
        GridSize.innerCols = prefs.getInt("innerCols", 8)
        GridSize.innerRows = prefs.getInt("innerRows", 5)
        GridSize.coverCols = prefs.getInt("coverCols", 4)
        GridSize.coverRows = prefs.getInt("coverRows", 6)
    }

    fun updateGrid(screen: Int, cols: Int, rows: Int) {
        if (screen == 0) { GridSize.innerCols = cols; GridSize.innerRows = rows } else { GridSize.coverCols = cols; GridSize.coverRows = rows }
        prefs.edit().putInt(if (screen == 0) "innerCols" else "coverCols", cols).putInt(if (screen == 0) "innerRows" else "coverRows", rows).apply()
        reflow()
    }

    /** 0 the app's own, 1 themed (monochrome in your colours), 2 coloured (monochrome in each app's own colour). */
    var iconStyle by mutableIntStateOf(prefs.getInt("iconStyle", 0))
        private set
    var iconPack by mutableStateOf(prefs.getString("iconPack", null))
        private set

    fun updateIcons(style: Int, pack: String?) {
        iconStyle = style
        iconPack = pack
        prefs.edit().putInt("iconStyle", style).putString("iconPack", pack).apply()
        rendered.clear()
        load()
    }

    fun load() {
        if (!callbackSet) {
            callbackSet = true
            launcherApps.registerCallback(object : LauncherApps.Callback() {
                override fun onPackageRemoved(packageName: String?, user: UserHandle?) { widgetCheck = true; load() }
                override fun onPackageAdded(packageName: String?, user: UserHandle?) = load()
                override fun onPackageChanged(packageName: String?, user: UserHandle?) = load()
                override fun onPackagesAvailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = load()
                override fun onPackagesUnavailable(p: Array<out String>?, u: UserHandle?, r: Boolean) = load()
            }, Handler(Looper.getMainLooper()))
        }
        // Installs and updates arrive in bursts; wait for them to settle, then redo only what changed.
        val first = apps.isEmpty()
        loadJob?.cancel()
        loadJob = scope.launch {
            if (!first) kotlinx.coroutines.delay(300)
            val t0 = android.os.SystemClock.elapsedRealtime()
            val drawnBefore = IconCache.drawn.get()
            val list = withContext(Dispatchers.Default) {
                val px = (64 * ctx.resources.displayMetrics.density).toInt()
                // Icons are drawn (and themed masks worked out) on all cores at once; it's the slow part of a cold start.
                launcherApps.profiles.flatMap { user ->
                    launcherApps.getActivityList(null, user)
                        .filter { it.componentName.packageName != ctx.packageName }
                        .map { info -> async { entry(info, user, px) } }
                }.awaitAll().sortedBy { it.label.lowercase() }
            }
            apps = list
            android.util.Log.i("Ninomae", "apps: ${list.size} in ${android.os.SystemClock.elapsedRealtime() - t0} ms, ${IconCache.drawn.get() - drawnBefore} icons drawn, the rest cached")
            withContext(Dispatchers.IO) { IconCache.prune(ctx, list.map { it.key }.toSet()) }
            forgetRemoved()
            if (widgetCheck) { widgetCheck = false; forgetDeadWidgets() }
            firstRunDefaults()
            homeDefaults()
        }
    }

    private fun entry(info: LauncherActivityInfo, user: UserHandle, px: Int): AppEntry {
        val serial = users.getSerialNumberForUser(user)
        val key = "${info.componentName.flattenToShortString()}#$serial"
        val stamp = "${info.applicationInfo.sourceDir}|${iconPack}|$px"
        val r = rendered[key]?.takeIf { it.stamp == stamp } ?: IconCache.read(ctx, key, stamp)?.let {
            Rendered(stamp, it.icon.asImageBitmap(), Color(it.tint), it.mono?.asImageBitmap(), it.installed).also { r -> rendered[key] = r }
        } ?: run {
            val own = info.getIcon(0)
            val packed = Icons.fromPack(ctx, iconPack, info.componentName)
            val base = packed ?: own
            val drawn = (if (user == android.os.Process.myUserHandle()) base else ctx.packageManager.getUserBadgedIcon(base, user))
                .toBitmap(px, px, Bitmap.Config.ARGB_8888)
            val installed = runCatching { ctx.packageManager.getPackageInfo(info.componentName.packageName, 0).firstInstallTime }.getOrDefault(0L)
            // Samsung hands launchers flattened icons; the app's own resource still has its monochrome layer.
            val raw = Icons.rawIcon(ctx, info)
            val mono = Icons.monochrome(own, px) ?: raw?.let { Icons.monochrome(it, px) }
                ?: Icons.autoMonochrome(raw ?: own, px)
            val tint = averageColour(drawn)
            IconCache.drawn.incrementAndGet()
            IconCache.write(ctx, key, IconCache.Entry(stamp, drawn, tint.toArgb(), mono, installed))
            Rendered(stamp, drawn.asImageBitmap(), tint, mono?.asImageBitmap(), installed)
                .also { rendered[key] = it }
        }
        return AppEntry(
            key = key,
            label = renames[key] ?: info.label.toString(),
            original = info.label.toString(),
            pkg = info.componentName.packageName,
            component = info.componentName,
            user = user,
            icon = r.icon,
            tint = r.tint,
            isGame = info.applicationInfo.category == ApplicationInfo.CATEGORY_GAME,
            mono = r.mono,
            installed = r.installed,
            category = info.applicationInfo.category,
        )
    }

    /**
     * Uninstalled apps leave nothing behind: their home tiles, folder places, dock pins, pairs, tags,
     * renames and counts go. Only when Android says the package is gone for that user, so an SD card
     * being out or a paused work profile keeps everything. If the package is still there but its
     * launch activity changed (an update can do that), everything points at the new one instead.
     */
    private suspend fun forgetRemoved() {
        val live = apps.mapTo(HashSet()) { it.key }
        val refs = HashSet<String>().apply {
            home.forEach { item ->
                when (item.type) {
                    "app" -> item.app?.let(::add)
                    "folder" -> addAll(item.apps)
                }
            }
            addAll(pinned); addAll(edgeApps); addAll(recent); addAll(hidden); addAll(launches.keys); addAll(renames.keys)
            pairs.forEach { add(it.left); add(it.right) }
            tags.forEach { addAll(it.apps) }
        }
        val shortcuts = home.filter { it.type == "shortcut" }.map { it.pkg to it.userSerial }.toSet()
        val dead = refs - live
        if (dead.isEmpty() && shortcuts.isEmpty()) return

        // Is [pkg] still installed for the user with this serial? Unsure counts as yes.
        val installed = HashMap<Pair<String, Long>, Boolean>()
        suspend fun there(pkg: String, serial: Long): Boolean = installed.getOrPut(pkg to serial) {
            withContext(Dispatchers.IO) {
                val user = users.getUserForSerialNumber(serial) ?: return@withContext false
                try {
                    launcherApps.getApplicationInfo(pkg, 0, user) != null
                } catch (_: PackageManager.NameNotFoundException) {
                    false
                } catch (_: Exception) {
                    true
                }
            }
        }

        val removed = HashSet<String>()
        val moved = HashMap<String, String>()
        for (k in dead) {
            val pkg = k.substringBefore('/')
            val serial = k.substringAfterLast('#', "").toLongOrNull() ?: continue
            if (!there(pkg, serial)) removed += k
            else apps.firstOrNull { it.pkg == pkg && users.getSerialNumberForUser(it.user) == serial }?.let { moved[k] = it.key }
        }
        val goneShortcuts = shortcuts.filterTo(HashSet()) { (pkg, serial) -> !there(pkg, serial) }
        if (removed.isEmpty() && moved.isEmpty() && goneShortcuts.isEmpty()) return
        android.util.Log.i("Ninomae", "forgetting uninstalled $removed, moved $moved, shortcuts $goneShortcuts")

        fun fix(k: String): String? = if (k in removed) null else moved[k] ?: k
        fun fixAll(list: List<String>) = list.mapNotNull(::fix).distinct()

        // Home: tiles go, folders shrink (one app left turns back into that app, none and it goes).
        commit(home.mapNotNull { item ->
            when (item.type) {
                "app" -> item.app?.let(::fix)?.let { item.copy(app = it) }
                "shortcut" -> item.takeIf { (it.pkg to it.userSerial) !in goneShortcuts }
                "folder" -> {
                    val left = fixAll(item.apps)
                    when (left.size) {
                        0 -> null
                        1 -> item.copy(type = "app", app = left.first(), apps = emptyList(), name = "")
                        else -> item.copy(apps = left)
                    }
                }
                else -> item
            }
        })
        pinned = fixAll(pinned); saveList("pinned", pinned)
        edgeApps = fixAll(edgeApps); saveList("edgeApps", edgeApps)
        recent = fixAll(recent); saveList("recent", recent)
        hidden = hidden.mapNotNull(::fix).toSet(); saveList("hidden", hidden.toList())
        renames = renames.mapNotNull { (k, v) -> fix(k)?.let { it to v } }.toMap()
        launches = launches.entries.mapNotNull { (k, v) -> fix(k)?.let { it to v } }.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
        prefs.edit()
            .putString("renames", JSONObject(renames as Map<*, *>).toString())
            .putString("launches", JSONObject(launches as Map<*, *>).toString())
            .apply()
        // A pair missing a side can't open any more.
        val keptPairs = pairs.mapNotNull { p ->
            val l = fix(p.left); val r = fix(p.right)
            if (l == null || r == null || l == r) null else p.copy(left = l, right = r)
        }
        if (activePair != null && keptPairs.none { it.id == activePair }) setActive(null)
        pairs = keptPairs; savePairs()
        saveTags(tags.map { it.copy(apps = fixAll(it.apps)) })
    }

    /** Look for widgets whose app is gone: once at start, then after each uninstall. */
    private var widgetCheck = true

    /**
     * Uninstalling an app unbinds its widgets, which then only say "Widget unavailable". Those go:
     * from the home screens, from stacks (a stack left with one turns back into that widget), and
     * from the widgets page.
     */
    private fun forgetDeadWidgets() {
        // Before the widget host is set up every widget would look gone: try again on the next load.
        if (!AppWidgets.ready) { widgetCheck = true; return }
        fun dead(id: Int) = id != 0 && AppWidgets.info(id) == null
        fun deadPart(p: String) = p.startsWith("w:") && dead(p.drop(2).toIntOrNull() ?: 0)
        val gone = mutableListOf<Int>()
        val kept = home.mapNotNull { item ->
            when (item.type) {
                "widget" -> if (dead(item.widgetId)) { gone += item.widgetId; null } else item
                "stack" -> {
                    item.apps.filter(::deadPart).forEach { gone += it.drop(2).toInt() }
                    val left = item.apps.filterNot(::deadPart)
                    when {
                        left.size == item.apps.size -> item
                        left.isEmpty() -> null
                        left.size == 1 -> left.first().let { p ->
                            if (p.startsWith("w:")) item.copy(type = "widget", widgetId = p.drop(2).toInt(), apps = emptyList())
                            else item.copy(type = "nwidget", kind = p.drop(2), apps = emptyList())
                        }
                        else -> item.copy(apps = left)
                    }
                }
                else -> item
            }
        }
        val feedKept = feed.filterNot { it.type == "widget" && dead(it.widgetId).also { d -> if (d) gone += it.widgetId } }
        if (gone.isEmpty()) return
        android.util.Log.i("Ninomae", "forgetting widgets of uninstalled apps: $gone")
        gone.forEach { AppWidgets.delete(it) }
        commit(kept)
        feed = feedKept.mapIndexed { i, it -> it.copy(y = i) }
        saveFeed()
    }

    private fun firstRunDefaults() {
        if (!prefs.contains("edgeApps") && apps.isNotEmpty()) {
            // The edge sidebar starts with your most opened apps (or the first few A–Z on a new phone).
            val byUse = apps.filter { it.key !in hidden }.sortedByDescending { launches[it.key] ?: 0 }
            edgeApps = byUse.take(6).map { it.key }
            saveList("edgeApps", edgeApps)
        }
        if (prefs.contains("pinned") || apps.isEmpty()) return
        val pm = ctx.packageManager
        fun pkgFor(i: Intent) = pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        val pkgs = listOfNotNull(
            pkgFor(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))),
            pkgFor(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))),
            pkgFor(Intent(MediaStore.ACTION_IMAGE_CAPTURE)),
            "com.sec.android.gallery3d",
            "com.sec.android.app.myfiles",
            "com.android.settings",
        )
        pinned = pkgs.mapNotNull { byPkg[it]?.key }.distinct()
        saveList("pinned", pinned)
    }

    // ---------- dock, recents, pairs ----------

    fun noteLaunch(key: String) {
        recent = (listOf(key) + recent.filter { it != key }).take(16)
        launches = launches + (key to (launches[key] ?: 0) + 1)
        saveList("recent", recent)
        prefs.edit().putString("launches", JSONObject(launches as Map<*, *>).toString()).apply()
    }

    fun dockRecents(max: Int): List<AppEntry> =
        recent.asSequence().filter { it !in pinned }.mapNotNull { byKey[it] }.take(max).toList()

    fun isPinned(key: String) = key in pinned

    fun togglePin(key: String) {
        pinned = if (key in pinned) pinned - key else pinned + key
        saveList("pinned", pinned)
    }

    fun pinAt(key: String, index: Int) {
        val rest = pinned - key
        pinned = rest.toMutableList().apply { add(index.coerceIn(0, rest.size), key) }
        saveList("pinned", pinned)
    }

    fun savePair(p: AppPair) {
        pairs = if (pairs.any { it.id == p.id }) pairs.map { if (it.id == p.id) p else it } else pairs + p
        savePairs()
    }

    fun deletePair(id: String) {
        pairs = pairs.filter { it.id != id }
        if (activePair == id) setActive(null)
        savePairs()
    }

    fun setActive(id: String?) {
        activePair = id
        prefs.edit().putString("activePair", id).apply()
    }

    fun newPairId(): String = UUID.randomUUID().toString()
    fun newId(): String = UUID.randomUUID().toString()

    // ---------- settings ----------

    fun updateName(v: String) {
        name = v
        prefs.edit().putString("name", v).apply()
    }

    fun updateLabels(v: Boolean) { labels = v; prefs.edit().putBoolean("labels", v).apply() }
    init {
        // Once: a cover with our bar but swipe down still on Samsung's panel (the old default) gets the deck.
        if (!prefs.getBoolean("swipeFollowsBar", false)) {
            if (prefs.getBoolean("barCover", false) && swipeDown == 1) updateSwipeDown(true, 0)
            prefs.edit().putBoolean("swipeFollowsBar", true).apply()
        }
    }
    fun updateDots(v: Boolean) { dots = v; prefs.edit().putBoolean("dots", v).apply() }
    fun updateDockRecents(v: Boolean) { dockRecentsOn = v; prefs.edit().putBoolean("dockRecents", v).apply() }
    fun updateSwipeDown(cover: Boolean, v: Int) {
        if (cover) swipeDown = v else swipeDownInner = v
        prefs.edit().putInt(if (cover) "swipeDownCover" else "swipeDownInner2", v).apply()
    }
    fun updateBar(cover: Boolean, own: Boolean) {
        if (cover) barCover = own else barInner = own
        prefs.edit().putBoolean(if (cover) "barCover" else "barInner", own).apply()
        // With our bar, swipe down should open our deck; with Samsung's, Samsung's panel. "Off" stays off.
        if ((if (cover) swipeDown else swipeDownInner) != 2) updateSwipeDown(cover, if (own) 0 else 1)
    }

    fun updateGlance(calendar: Boolean, weather: Boolean) {
        glanceCalendar = calendar
        glanceWeather = weather
        prefs.edit().putBoolean("glanceCalendar", calendar).putBoolean("glanceWeather", weather).apply()
        if (!weather) Glance.forgetWeather()
        Glance.refresh(ctx, this)
    }

    // ---------- backup ----------

    /** Everything you've set up, as JSON. Widgets come back only on this phone (their ids are local). */
    fun exportJson(): String {
        val o = JSONObject().put("ninomaeHome", 1)
        // The Immich API key stays on the phone: a backup file is easy to share by mistake.
        prefs.all.filterKeys { it != "immichKey" }.forEach { (k, v) ->
            when (v) {
                is Boolean, is Int, is Long, is Float, is String -> o.put(k, JSONObject().put("t", v.javaClass.simpleName).put("v", v))
            }
        }
        return o.toString(2)
    }

    fun importJson(text: String): Boolean = runCatching {
        val o = JSONObject(text)
        require(o.has("ninomaeHome"))
        val e = prefs.edit().clear()
        o.keys().forEach { k ->
            val item = o.optJSONObject(k) ?: return@forEach
            when (item.getString("t")) {
                "Boolean" -> e.putBoolean(k, item.getBoolean("v"))
                "Integer" -> e.putInt(k, item.getInt("v"))
                "Long" -> e.putLong(k, item.getLong("v"))
                "Float" -> e.putFloat(k, item.getDouble("v").toFloat())
                "String" -> e.putString(k, item.getString("v"))
            }
        }
        e.commit()
        inst = null
        true
    }.getOrDefault(false)

    fun updateSearchCalendar(v: Boolean) { searchCalendar = v; prefs.edit().putBoolean("searchCalendar", v).apply() }
    fun updateSearchFiles(v: Boolean) { searchFiles = v; prefs.edit().putBoolean("searchFiles", v).apply() }
    fun updateSearchContacts(v: Boolean) { searchContacts = v; prefs.edit().putBoolean("searchContacts", v).apply() }
    fun updateLocked(v: Boolean) { locked = v; prefs.edit().putBoolean("locked", v).apply() }
    fun updateColourSource(v: Int) { colourSource = v; prefs.edit().putInt("colourSource", v).apply() }
    fun updateSwatch(v: Int) { swatch = v; prefs.edit().putInt("swatch", v).apply() }

    fun updateDoubleTapLock(v: Boolean) { doubleTapLock = v; prefs.edit().putBoolean("doubleTapLock", v).apply() }

    fun setHidden(key: String, hide: Boolean) {
        hidden = if (hide) hidden + key else hidden - key
        saveList("hidden", hidden.toList())
    }

    fun rename(key: String, label: String?) {
        renames = if (label.isNullOrBlank()) renames - key else renames + (key to label.trim())
        prefs.edit().putString("renames", JSONObject(renames as Map<*, *>).toString()).apply()
        apps = apps.map { if (it.key == key) it.copy(label = renames[key] ?: it.original) else it }
            .sortedBy { it.label.lowercase() }
    }

    fun saveHome() = prefs.edit().putString("homeItems", homeToJson(home)).apply()

    private fun homeDefaults() {
        if (prefs.contains("homeItems")) {
            // 0.3: the cover screen's untouched old defaults become the phone home (clock + player).
            if (!prefs.getBoolean("coverPhoneV3", false)) {
                val cover = home.filter { it.screen == 1 }
                val untouched = cover.isEmpty() || cover.map { it.kind }.sorted() == listOf("greeting", "jump")
                if (untouched) {
                    home = home.filter { it.screen != 1 } + listOf(
                        HomeItem(id = newId(), screen = 1, page = 0, x = 0, y = 0, w = 4, h = 1, type = "nwidget", kind = "clock"),
                        HomeItem(id = newId(), screen = 1, page = 0, x = 0, y = 1, w = 4, h = 1, type = "nwidget", kind = "player"),
                    )
                    saveHome()
                }
                prefs.edit().putBoolean("coverPhoneV3", true).apply()
            }
            return
        }
        home = listOf(
            HomeItem(id = newId(), screen = 0, page = 0, x = 4, y = 0, w = 4, h = 3, type = "nwidget", kind = "stack"),
            HomeItem(id = newId(), screen = 1, page = 0, x = 0, y = 0, w = 4, h = 1, type = "nwidget", kind = "clock"),
            HomeItem(id = newId(), screen = 1, page = 0, x = 0, y = 1, w = 4, h = 1, type = "nwidget", kind = "player"),
        )
        saveHome()
    }

    fun refreshAccess() {
        notifAccess = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        val a11y = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        a11yOn = SplitService.instance != null || a11y.contains(ctx.packageName)
        canWrite = Settings.System.canWrite(ctx)
        isDefaultHome = ctx.packageManager
            .resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == ctx.packageName
    }

    // ---------- storage ----------

    private fun list(k: String): List<String> =
        prefs.getString(k, null)?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()

    private fun saveList(k: String, v: List<String>) = prefs.edit().putString(k, v.joinToString("\n")).apply()

    private fun loadMap(k: String): Map<String, String> = runCatching {
        val o = JSONObject(prefs.getString(k, "{}")!!)
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrDefault(emptyMap())

    private fun loadCounts(): Map<String, Int> = runCatching {
        val o = JSONObject(prefs.getString("launches", "{}")!!)
        o.keys().asSequence().associateWith { o.getInt(it) }
    }.getOrDefault(emptyMap())

    private fun loadPairs(): List<AppPair> = runCatching {
        val a = JSONArray(prefs.getString("pairs", "[]")!!)
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            AppPair(o.getString("id"), o.getString("name"), o.getString("left"), o.getString("right"))
        }
    }.getOrDefault(emptyList())

    private fun savePairs() {
        val a = JSONArray()
        pairs.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("left", it.left).put("right", it.right)) }
        prefs.edit().putString("pairs", a.toString()).apply()
    }
}

/** Average colour of the icon's visible pixels, used to tint cards. */
fun averageColour(bmp: Bitmap): Color {
    val small = Bitmap.createScaledBitmap(bmp, 16, 16, true)
    var r = 0L; var g = 0L; var b = 0L; var n = 0L
    for (y in 0 until 16) for (x in 0 until 16) {
        val p = small.getPixel(x, y)
        if ((p ushr 24) < 200) continue
        r += (p shr 16) and 0xff; g += (p shr 8) and 0xff; b += p and 0xff; n++
    }
    if (n == 0L) return Color.Gray
    return Color((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
}
