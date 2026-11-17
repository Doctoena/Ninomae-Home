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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class AppEntry(
    val key: String,
    val label: String,
    val pkg: String,
    val component: ComponentName,
    val user: UserHandle,
    val icon: ImageBitmap,
    val tint: Color,
    val isGame: Boolean,
)

data class AppPair(val id: String, val name: String, val left: String, val right: String)

/** Everything the home screen shows, kept in Compose state and saved to prefs. */
class HomeState private constructor(private val ctx: Context) {
    companion object {
        @Volatile private var inst: HomeState? = null
        fun get(c: Context): HomeState = inst ?: synchronized(this) {
            inst ?: HomeState(c.applicationContext).also { inst = it }
        }
    }

    private val prefs = ctx.getSharedPreferences("home", Context.MODE_PRIVATE)
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
    /** 0 off, 1 left, 2 right. Right keeps a portrait's face clear on most wallpapers. */
    var widgetSide by mutableIntStateOf(prefs.getInt("widgetSide", 2))
        private set

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
        scope.launch {
            val list = withContext(Dispatchers.IO) {
                val px = (64 * ctx.resources.displayMetrics.density).toInt()
                launcherApps.profiles.flatMap { user ->
                    launcherApps.getActivityList(null, user)
                        .filter { it.componentName.packageName != ctx.packageName }
                        .map { entry(it, user, px) }
                }.sortedBy { it.label.lowercase() }
            }
            apps = list
            firstRunDefaults()
        }
    }

    private fun entry(info: LauncherActivityInfo, user: UserHandle, px: Int): AppEntry {
        val bmp = info.getBadgedIcon(0).toBitmap(px, px, Bitmap.Config.ARGB_8888)
        val serial = users.getSerialNumberForUser(user)
        return AppEntry(
            key = "${info.componentName.flattenToShortString()}#$serial",
            label = info.label.toString(),
            pkg = info.componentName.packageName,
            component = info.componentName,
            user = user,
            icon = bmp.asImageBitmap(),
            tint = averageColour(bmp),
            isGame = info.applicationInfo.category == ApplicationInfo.CATEGORY_GAME,
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

    // ---------- settings ----------

    fun updateName(v: String) {
        name = v
        prefs.edit().putString("name", v).apply()
    }

    fun updateWidgetSide(v: Int) {
        widgetSide = v
        prefs.edit().putInt("widgetSide", v).apply()
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
