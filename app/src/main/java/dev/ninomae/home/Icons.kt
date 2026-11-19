package dev.ninomae.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** An icon pack someone installed (Nova / ADW format). */
data class IconPack(val pkg: String, val label: String)

/**
 * Where app icons come from: the app itself, an icon pack, plus the app's monochrome layer
 * for themed icons (Android 13+). Lookups are cached; changing pack clears them.
 */
object Icons {
    private val packActions = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.teslacoilsw.launcher.THEME",
        "com.gau.go.launcherex.theme",
        "app.lawnchair.icons.THEMED_ICON",
    )

    fun packs(ctx: Context): List<IconPack> {
        val pm = ctx.packageManager
        return packActions.flatMap { a ->
            runCatching { pm.queryIntentActivities(Intent(a), PackageManager.GET_META_DATA) }.getOrDefault(emptyList())
        }.map { it.activityInfo.packageName }.distinct().map { pkg ->
            IconPack(pkg, runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg))
        }.sortedBy { it.label.lowercase() }
    }

    private var loadedPack: String? = null
    private var packRes: Resources? = null
    private var packMap: Map<String, String> = emptyMap()

    /** component "pkg/cls" -> drawable name, from the pack's appfilter.xml. */
    @Synchronized
    private fun loadPack(ctx: Context, pkg: String?) {
        if (pkg == loadedPack) return
        loadedPack = pkg
        packRes = null
        packMap = emptyMap()
        if (pkg == null) return
        runCatching {
            val res = ctx.packageManager.getResourcesForApplication(pkg)
            val parser: XmlPullParser = res.getIdentifier("appfilter", "xml", pkg).takeIf { it != 0 }?.let { res.getXml(it) }
                ?: XmlPullParserFactory.newInstance().newPullParser().also {
                    it.setInput(ctx.createPackageContext(pkg, 0).assets.open("appfilter.xml"), "UTF-8")
                }
            val map = HashMap<String, String>()
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "item") {
                    val comp = parser.getAttributeValue(null, "component")
                    val draw = parser.getAttributeValue(null, "drawable")
                    if (comp != null && draw != null && comp.startsWith("ComponentInfo{")) {
                        map[comp.removePrefix("ComponentInfo{").removeSuffix("}")] = draw
                    }
                }
                parser.next()
            }
            packRes = res
            packMap = map
        }
    }

    /** The pack's drawable for this app, if it has one. */
    fun fromPack(ctx: Context, pack: String?, cn: ComponentName): Drawable? {
        val (res, map) = synchronized(this) { loadPack(ctx, pack); packRes to packMap }
        if (res == null) return null
        val name = map["${cn.packageName}/${cn.className}"] ?: return null
        val id = res.getIdentifier(name, "drawable", pack)
        if (id == 0) return null
        return runCatching { res.getDrawableForDensity(id, ctx.resources.displayMetrics.densityDpi, null) }.getOrNull()
    }

    /** The icon straight from the app's resources, before any system theming flattens it. */
    fun rawIcon(ctx: Context, info: android.content.pm.LauncherActivityInfo): Drawable? = runCatching {
        val res = ctx.packageManager.getResourcesForApplication(info.applicationInfo)
        val id = info.activityInfo.iconResource.takeIf { it != 0 } ?: info.applicationInfo.icon
        if (id == 0) null else res.getDrawableForDensity(id, ctx.resources.displayMetrics.densityDpi, null)
    }.getOrNull()

    /** The app's monochrome layer as an alpha mask, for themed icons. Null if the app doesn't ship one. */
    fun monochrome(icon: Drawable, px: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < 33 || icon !is AdaptiveIconDrawable) return null
        val mono = icon.monochrome ?: return null
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ALPHA_8)
        // Adaptive layers are 108 dp with a 72 dp visible middle, so draw them 1.5× and centred.
        val pad = px / 4
        mono.setBounds(-pad, -pad, px + pad, px + pad)
        mono.draw(Canvas(bmp))
        return bmp
    }

    /**
     * A monochrome layer made up for apps that don't ship one (Uber, banking apps, older apps), so
     * themed icons cover everything. An adaptive icon gives its foreground (the logo); anything
     * else, or a foreground that fills the whole icon, is read against its own background colour.
     */
    fun autoMonochrome(icon: Drawable, px: Int): Bitmap? = runCatching {
        val src = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(src)
        val fg = (icon as? AdaptiveIconDrawable)?.foreground
        if (fg != null) {
            val pad = px / 4
            fg.setBounds(-pad, -pad, px + pad, px + pad)
            fg.draw(canvas)
            if (coverage(src) < 0.7f) return@runCatching alphaMask(src)
            src.eraseColor(0)
        }
        icon.setBounds(0, 0, px, px)
        icon.draw(canvas)
        contrastMask(src)
    }.getOrNull()

    private fun coverage(b: Bitmap): Float {
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        return px.count { (it ushr 24) > 128 }.toFloat() / px.size
    }

    private fun alphaMask(b: Bitmap): Bitmap = b.extractAlpha()

    /** The logo as whatever differs from the icon's background colour (sampled on a ring inside its edge). */
    private fun contrastMask(b: Bitmap): Bitmap {
        val w = b.width
        val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        var r = 0L; var g = 0L; var bl = 0L; var n = 0
        val inset = (w * 0.2f).toInt()
        for (i in inset until w - inset step 2) {
            for (p in intArrayOf(px[inset * w + i], px[(h - 1 - inset) * w + i], px[i * w + inset], px[i * w + w - 1 - inset])) {
                if ((p ushr 24) < 200) continue
                r += (p shr 16) and 255; g += (p shr 8) and 255; bl += p and 255; n++
            }
        }
        if (n == 0) return b.extractAlpha()
        val br = (r / n).toInt(); val bg = (g / n).toInt(); val bb = (bl / n).toInt()
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        val alphas = IntArray(w * h)
        for (i in px.indices) {
            val p = px[i]
            val a = (p ushr 24) / 255f
            val d = (kotlin.math.abs(((p shr 16) and 255) - br) + kotlin.math.abs(((p shr 8) and 255) - bg) + kotlin.math.abs((p and 255) - bb)) / 765f
            val v = ((d - 0.06f) / 0.22f).coerceIn(0f, 1f) * a
            alphas[i] = ((v * 255).toInt() shl 24)
        }
        val argb = Bitmap.createBitmap(alphas, w, h, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(argb, 0f, 0f, null)
        return out
    }
}
