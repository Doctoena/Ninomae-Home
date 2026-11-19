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
    val appContext: Context get() = ctx
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
        prefs.edit().putInt("wallSeed", w.seed).apply()
    }
    var dockRecentsOn by mutableStateOf(prefs.getBoolean("dockRecents", true))
        private set
    /** 0 sidebar, 1 Samsung's notification panel, 2 nothing. */
    var swipeDown by mutableIntStateOf(prefs.getInt("swipeDownCover", 1))
        private set
    /** Same choice for the inner screen. Cover: Samsung's panel by default; inner: the deck. */
    var swipeDownInner by mutableIntStateOf(prefs.getInt("swipeDownInner2", 0))
        private set
    var doubleTapLock by mutableStateOf(prefs.getBoolean("doubleTapLock", true))
        private set
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

    /** 0 the app's own, 1 themed (monochrome in your colours). */
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
                override fun onPackageRemoved(packageName: String?, user: UserHandle?) = load()
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

    private fun firstRunDefaults() {
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
    fun updateDots(v: Boolean) { dots = v; prefs.edit().putBoolean("dots", v).apply() }
    fun updateDockRecents(v: Boolean) { dockRecentsOn = v; prefs.edit().putBoolean("dockRecents", v).apply() }
    fun updateSwipeDown(cover: Boolean, v: Int) {
        if (cover) swipeDown = v else swipeDownInner = v
        prefs.edit().putInt(if (cover) "swipeDownCover" else "swipeDownInner2", v).apply()
    }
    fun updateBar(cover: Boolean, own: Boolean) {
        if (cover) barCover = own else barInner = own
        prefs.edit().putBoolean(if (cover) "barCover" else "barInner", own).apply()
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
        prefs.all.forEach { (k, v) ->
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
