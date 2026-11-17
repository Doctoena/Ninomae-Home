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
        loadPack(ctx, pack)
        val res = packRes ?: return null
        val name = packMap["${cn.packageName}/${cn.className}"] ?: return null
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
}
