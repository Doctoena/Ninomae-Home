@file:OptIn(ExperimentalTextApi::class)

package dev.ninomae.home

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.app.WallpaperManager
import android.os.Handler
import android.os.Looper
import androidx.core.graphics.ColorUtils
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The shell's colour layers, taken from the wallpaper's Material You scheme (same idea as matugen on the desktop). */
@Immutable
data class Pal(
    val l0: Color,
    val l1: Color,
    val l3: Color,
    val l4: Color,
    val l5: Color,
    val fg: Color,
    val sub: Color,
    val dim: Color,
    val line: Color,
    val acc: Color,
    val ink: Color,
    val sec: Color,
    val dark: Boolean,
)

val LocalPal = staticCompositionLocalOf<Pal> { error("No palette") }

private fun palOf(s: ColorScheme, dark: Boolean) = Pal(
    l0 = s.surface,
    l1 = s.surfaceContainerLow,
    l3 = s.surfaceContainer,
    l4 = s.surfaceContainerHigh,
    l5 = s.surfaceContainerHighest,
    fg = s.onSurface,
    sub = s.onSurfaceVariant,
    dim = s.outline,
    line = s.outlineVariant,
    acc = s.primary,
    ink = s.onPrimary,
    sec = s.secondaryContainer,
    dark = dark,
)

@Composable
fun NinomaeTheme(source: Int, swatch: Int, wallSeed: Int = 0, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    // 1: the wallpaper's colour when Android shares it, otherwise the swatch; 2: the swatch; 0: Samsung's palette.
    val wall = if (source == 1) rememberWallpaperSeed() else null
    // Android's wallpaper colour, else the one worked out from a wallpaper set here, else the swatch.
    val seed = when (source) { 1 -> wall ?: wallSeed.takeIf { it != 0 } ?: swatch; 2 -> swatch; else -> null }
    val pal = if (seed != null) remember(seed, dark) { palFromSeed(seed, dark) } else {
        val scheme = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        remember(scheme, dark) { palOf(scheme, dark) }
    }
    CompositionLocalProvider(LocalPal provides pal, content = content)
}

/** The wallpaper's most colourful main colour, refreshed when the wallpaper changes. */
@Composable
private fun rememberWallpaperSeed(): Int? {
    val ctx = LocalContext.current
    val wm = remember { WallpaperManager.getInstance(ctx) }
    var seed by remember { mutableStateOf(pickSeed(wm)) }
    DisposableEffect(Unit) {
        val l = WallpaperManager.OnColorsChangedListener { _, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) seed = pickSeed(wm)
        }
        wm.addOnColorsChangedListener(l, Handler(Looper.getMainLooper()))
        onDispose { wm.removeOnColorsChangedListener(l) }
    }
    return seed
}

/** Whether Android shares this wallpaper's colours with apps at all. */
fun wallpaperSeed(ctx: android.content.Context): Int? = pickSeed(WallpaperManager.getInstance(ctx))

/** Seeds to choose from when the wallpaper's own colours aren't available. Plum is the desktop shell's. */
val swatches = listOf(
    0xFF9B7BC0.toInt() to "Plum",
    0xFFB8574A.toInt() to "Oni",
    0xFFC4708F.toInt() to "Rose",
    0xFFC08A3E.toInt() to "Amber",
    0xFF3E9C93.toInt() to "Teal",
    0xFF5B7FC4.toInt() to "Blue",
)

private fun pickSeed(wm: WallpaperManager): Int? {
    val c = runCatching { wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: wm.getWallpaperColors(WallpaperManager.FLAG_LOCK) }
        .getOrNull() ?: return null
    return listOfNotNull(c.primaryColor, c.secondaryColor, c.tertiaryColor)
        .map { it.toArgb() }
        .maxByOrNull { val hsl = FloatArray(3); ColorUtils.colorToHSL(it, hsl); hsl[1] * (1f - kotlin.math.abs(hsl[2] - 0.5f)) }
}

/**
 * A palette in the shell's style from one colour: the hue kept, surfaces barely tinted,
 * a soft accent. Close to what matugen gives the desktop for the same wallpaper.
 */
private fun palFromSeed(seed: Int, dark: Boolean): Pal {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seed, hsl)
    val h = hsl[0]
    fun c(s: Float, l: Float) = Color(ColorUtils.HSLToColor(floatArrayOf(h, s, l)))
    return if (dark) Pal(
        l0 = c(0.10f, 0.07f), l1 = c(0.10f, 0.09f), l3 = c(0.11f, 0.12f), l4 = c(0.11f, 0.145f), l5 = c(0.10f, 0.18f),
        fg = c(0.20f, 0.92f), sub = c(0.10f, 0.74f), dim = c(0.06f, 0.52f), line = c(0.08f, 0.30f),
        acc = c(0.55f, 0.82f), ink = c(0.40f, 0.24f), sec = c(0.24f, 0.30f), dark = true,
    ) else Pal(
        l0 = c(0.30f, 0.97f), l1 = c(0.28f, 0.95f), l3 = c(0.26f, 0.92f), l4 = c(0.24f, 0.89f), l5 = c(0.22f, 0.86f),
        fg = c(0.20f, 0.11f), sub = c(0.10f, 0.32f), dim = c(0.06f, 0.50f), line = c(0.10f, 0.78f),
        acc = c(0.50f, 0.42f), ink = c(0.40f, 0.97f), sec = c(0.35f, 0.86f), dark = false,
    )
}

/** Corner radii by control size, as in the shell (Appearance.rounding). */
object Rad {
    val joined = 5.dp
    val chip = 8.dp
    val segment = 10.dp
    val container = 12.dp
    val normal = 17.dp
    val dock = 20.dp
}

/** The icon shape chosen in Look: 0 squircle, 1 circle, 2 rounded square, 3 square. */
val LocalIconShape = staticCompositionLocalOf { 0 }

/** That shape at [size] (the squircle is the one the launcher always had). */
fun iconShapeOf(kind: Int, size: Dp): androidx.compose.ui.graphics.Shape = when (kind) {
    1 -> androidx.compose.foundation.shape.CircleShape
    2 -> androidx.compose.foundation.shape.RoundedCornerShape(size * 0.22f)
    3 -> androidx.compose.foundation.shape.RoundedCornerShape(size * 0.12f)
    else -> androidx.compose.foundation.shape.RoundedCornerShape(size * 0.32f)
}

@Composable
fun iconShape(size: Dp) = iconShapeOf(LocalIconShape.current, size)

/** Rounded rect with its own corners: outer edges big, edges that touch a neighbour small. */
fun corners(tl: Dp, tr: Dp, br: Dp, bl: Dp) =
    RoundedCornerShape(topStart = tl, topEnd = tr, bottomEnd = br, bottomStart = bl)

fun mix(a: Color, b: Color, t: Float) = lerp(a, b, t)

private fun flex(w: Int) = Font(
    R.font.roboto_flex,
    FontWeight(w),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(w),
        FontVariation.Setting("wdth", 105f),
        FontVariation.Setting("GRAD", 50f),
    ),
)

val Sans = FontFamily(flex(400), flex(500), flex(600), flex(700))

val TitleFont = FontFamily(
    Font(R.font.gabarito, FontWeight.Black, variationSettings = FontVariation.Settings(FontVariation.weight(900))),
)

val NumFont = FontFamily(
    Font(R.font.rubik, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.rubik, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
)

private fun symbols(fill: Float) = FontFamily(
    Font(
        R.font.material_symbols_rounded,
        variationSettings = FontVariation.Settings(
            FontVariation.Setting("FILL", fill),
            FontVariation.weight(400),
        ),
    ),
)

val Sym = symbols(0f)
val SymFill = symbols(1f)
