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
fun NinomaeTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    val pal = remember(scheme, dark) { palOf(scheme, dark) }
    CompositionLocalProvider(LocalPal provides pal, content = content)
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
