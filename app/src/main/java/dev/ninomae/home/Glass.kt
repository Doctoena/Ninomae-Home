package dev.ninomae.home

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Frosted glass needs the wallpaper behind it, but Samsung turns off Android's window blur and
 * Android won't let apps read the wallpaper. So the wallpaper is set from here, and a small
 * blurred copy is kept for the glass to draw.
 */
object Glass {
    private fun file(ctx: Context) = File(ctx.filesDir, "glass.png")
    private fun previewFile(ctx: Context) = File(ctx.filesDir, "wall.png")
    /** The wallpaper small and sharp, so the glass can be frosted again at any strength. */
    private fun baseFile(ctx: Context) = File(ctx.filesDir, "glass_base.png")
    /** A light frost needs the sharp copy, which only wallpapers picked since it was added have. */
    fun canLighten(ctx: Context) = baseFile(ctx).exists()

    /** Wallpapers set before the sharp copy was kept: their first frosted copy, to frost more from. */
    private fun mediumFile(ctx: Context) = File(ctx.filesDir, "glass_medium.png")

    fun load(ctx: Context): ImageBitmap? =
        file(ctx).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }

    /** A small sharp copy, for the previews in settings. */
    fun loadPreview(ctx: Context): ImageBitmap? =
        previewFile(ctx).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }

    class Set(val frosted: ImageBitmap, val preview: ImageBitmap, val seed: Int, val id: Int)

    /** Android's id for the home screen wallpaper; it changes whenever any app sets a new one (-1 if unknown). */
    fun currentId(ctx: Context): Int =
        runCatching { WallpaperManager.getInstance(ctx).getWallpaperId(WallpaperManager.FLAG_SYSTEM) }.getOrDefault(-1)

    /** The wallpaper changed somewhere else: the frosted copy and preview are of the old one, so they go. */
    fun forget(ctx: Context) {
        file(ctx).delete()
        previewFile(ctx).delete()
        baseFile(ctx).delete()
        mediumFile(ctx).delete()
    }

    /**
     * Frost the glass again at [level] (0 light, 1 medium, 2 heavy) and save it. From the sharp copy when
     * there is one; otherwise from the first frosted copy, which can only be frosted more.
     */
    suspend fun refrost(ctx: Context, level: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val base = baseFile(ctx)
            val out = if (base.exists()) frost(BitmapFactory.decodeFile(base.path), level) else {
                val medium = mediumFile(ctx)
                if (!medium.exists()) file(ctx).takeIf { it.exists() }?.copyTo(medium) ?: return@runCatching null
                val src = BitmapFactory.decodeFile(medium.path)
                if (level >= 2) frost(src, 2, extra = true) else src
            }
            file(ctx).outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
            out.asImageBitmap()
        }.getOrNull()
    }

    /** Sets [uri] as the home screen wallpaper; returns its blurred copy, a small sharp one and its main colour. */
    suspend fun setWallpaper(ctx: Context, uri: Uri, level: Int = 1): Set? = withContext(Dispatchers.IO) {
        runCatching {
            val src = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > 4096) {
                    val k = 4096f / longest
                    d.setTargetSize((info.size.width * k).toInt(), (info.size.height * k).toInt())
                }
            }
            val id = WallpaperManager.getInstance(ctx).setBitmap(src, null, true, WallpaperManager.FLAG_SYSTEM)
            val small = Bitmap.createScaledBitmap(src, 200, (src.height * 200f / src.width).toInt().coerceAtLeast(1), true)
            baseFile(ctx).outputStream().use { small.compress(Bitmap.CompressFormat.PNG, 100, it) }
            mediumFile(ctx).delete()
            val frosted = frost(small, level)
            file(ctx).outputStream().use { frosted.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val pw = 480
            val preview = Bitmap.createScaledBitmap(src, pw, (src.height * pw.toFloat() / src.width).toInt().coerceAtLeast(1), true)
            previewFile(ctx).outputStream().use { preview.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            Set(frosted.asImageBitmap(), preview.asImageBitmap(), seedOf(preview), id)
        }.getOrNull()
    }

    /**
     * The picture's main colour, for "Colours: Wallpaper" (Samsung won't hand Android's version over):
     * the hue with the most colourful pixels, ignoring near-greys, blacks and whites.
     */
    fun seedOf(b: Bitmap): Int {
        val small = Bitmap.createScaledBitmap(b, 64, (b.height * 64f / b.width).toInt().coerceAtLeast(1), true)
        val px = IntArray(small.width * small.height)
        small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
        val weight = FloatArray(24)
        val sums = Array(24) { FloatArray(3) }
        val hsv = FloatArray(3)
        for (c in px) {
            android.graphics.Color.colorToHSV(c, hsv)
            if (hsv[1] < 0.22f || hsv[2] < 0.18f || hsv[2] > 0.97f) continue
            val bin = ((hsv[0] / 15f).toInt()).coerceIn(0, 23)
            val w = hsv[1] * hsv[2]
            weight[bin] += w
            sums[bin][0] += w * ((c shr 16) and 255); sums[bin][1] += w * ((c shr 8) and 255); sums[bin][2] += w * (c and 255)
        }
        val best = weight.indices.maxByOrNull { weight[it] } ?: return 0xFF9B7BC0.toInt()
        if (weight[best] <= 0f) return 0xFF9B7BC0.toInt()
        val w = weight[best]
        return android.graphics.Color.rgb((sums[best][0] / w).toInt(), (sums[best][1] / w).toInt(), (sums[best][2] / w).toInt())
    }

    /** Shrinks the picture and box-blurs it three times (close to a gaussian); drawn big, it reads as frosted. */
    private fun frost(src: Bitmap, level: Int = 1, extra: Boolean = false): Bitmap {
        val w = 200
        val h = (src.height * w.toFloat() / src.width).toInt().coerceAtLeast(1)
        val small = if (src.width == w) src else Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        // Light, medium (the original) and heavy; [extra] adds to a copy that's already medium.
        val r = if (extra) 4 else when (level) { 0 -> 2; 2 -> 7; else -> 4 }
        repeat(3) {
            boxPass(px, w, h, r, horizontal = true)
            boxPass(px, w, h, r, horizontal = false)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun boxPass(px: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val out = IntArray(len)
        for (line in 0 until lines) {
            fun at(i: Int): Int {
                val c = i.coerceIn(0, len - 1)
                return if (horizontal) px[line * w + c] else px[c * w + line]
            }
            var rs = 0; var gs = 0; var bs = 0
            for (i in -r..r) { val c = at(i); rs += (c shr 16) and 255; gs += (c shr 8) and 255; bs += c and 255 }
            val n = 2 * r + 1
            for (i in 0 until len) {
                out[i] = (0xFF shl 24) or ((rs / n) shl 16) or ((gs / n) shl 8) or (bs / n)
                val add = at(i + r + 1); val drop = at(i - r)
                rs += ((add shr 16) and 255) - ((drop shr 16) and 255)
                gs += ((add shr 8) and 255) - ((drop shr 8) and 255)
                bs += (add and 255) - (drop and 255)
            }
            for (i in 0 until len) if (horizontal) px[line * w + i] = out[i] else px[i * w + line] = out[i]
        }
    }
}
