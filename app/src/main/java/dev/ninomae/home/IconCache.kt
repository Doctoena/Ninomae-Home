package dev.ninomae.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import java.io.File

/**
 * Drawn app icons kept on disk, so a cold start shows the home screen straight away instead of
 * redrawing every icon and working out every themed mask. An entry is good while its stamp
 * (the app's install path, the icon pack and the size) still matches.
 */
object IconCache {
    /** How many icons have been drawn from scratch (not from the cache), for the load log line. */
    val drawn = java.util.concurrent.atomic.AtomicInteger()

    class Entry(val stamp: String, val icon: Bitmap, val tint: Int, val mono: Bitmap?, val installed: Long)

    private fun dir(ctx: Context) = File(ctx.cacheDir, "icons").apply { mkdirs() }
    private fun name(key: String) = key.replace(Regex("[^A-Za-z0-9._-]"), "_")

    fun read(ctx: Context, key: String, stamp: String): Entry? = runCatching {
        val base = File(dir(ctx), name(key))
        val meta = File("$base.txt").takeIf { it.exists() }?.readLines() ?: return null
        if (meta.size < 4 || meta[0] != stamp) return null
        val icon = BitmapFactory.decodeFile("$base.png") ?: return null
        // The themed mask is stored as a PNG's alpha; it goes back to an alpha-only bitmap.
        val mono = if (meta[3] == "1") BitmapFactory.decodeFile("$base.mono.png")?.extractAlpha() ?: return null else null
        Entry(stamp, icon, meta[1].toInt(), mono, meta[2].toLong())
    }.getOrNull()

    fun write(ctx: Context, key: String, e: Entry) {
        runCatching {
            val base = File(dir(ctx), name(key))
            File("$base.png").outputStream().use { e.icon.compress(Bitmap.CompressFormat.PNG, 100, it) }
            e.mono?.let { m ->
                val argb = Bitmap.createBitmap(m.width, m.height, Bitmap.Config.ARGB_8888)
                Canvas(argb).drawBitmap(m, 0f, 0f, Paint())
                File("$base.mono.png").outputStream().use { argb.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            // Written last: an entry only counts once its pictures are all there.
            File("$base.txt").writeText("${e.stamp}\n${e.tint}\n${e.installed}\n${if (e.mono != null) 1 else 0}\n")
        }
    }

    /** Forget apps that are gone. */
    fun prune(ctx: Context, keys: Set<String>) {
        val keep = keys.map { name(it) }.toSet()
        dir(ctx).listFiles()?.forEach { f ->
            val stem = f.name.removeSuffix(".txt").removeSuffix(".mono.png").removeSuffix(".png")
            if (stem !in keep) f.delete()
        }
    }
}
