package dev.ninomae.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Glyph(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    color: Color = LocalPal.current.fg,
    fill: Boolean = false,
) {
    val sp = with(LocalDensity.current) { size.toSp() }
    BasicText(
        name,
        modifier,
        style = TextStyle(
            color = color,
            fontSize = sp,
            lineHeight = sp,
            fontFamily = if (fill) SymFill else Sym,
            textAlign = TextAlign.Center,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
        ),
        maxLines = 1,
        softWrap = false,
    )
}

@Composable
fun Lbl(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 14.sp,
    color: Color = LocalPal.current.fg,
    weight: FontWeight = FontWeight.Normal,
    family: FontFamily = Sans,
    maxLines: Int = 1,
    align: TextAlign = TextAlign.Start,
) {
    BasicText(
        text,
        modifier,
        style = TextStyle(color = color, fontSize = size, fontWeight = weight, fontFamily = family, textAlign = align),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun Title(text: String, modifier: Modifier = Modifier, size: TextUnit = 18.sp, color: Color = LocalPal.current.fg) =
    Lbl(text, modifier, size, color, FontWeight.Black, TitleFont)

@Composable
fun Num(text: String, modifier: Modifier = Modifier, size: TextUnit = 14.sp, color: Color = LocalPal.current.fg) =
    Lbl(text, modifier, size, color, FontWeight.Normal, NumFont)

/** One block of the shell's joined language: a neutral surface with its own corners. */
@Composable
fun Piece(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Rad.container),
    color: Color = LocalPal.current.l3,
    edge: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val pal = LocalPal.current
    Box(
        modifier
            .clip(shape)
            .background(color)
            .then(if (edge) Modifier.border(1.dp, pal.fg.copy(alpha = 0.10f), shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.press(onLong: (() -> Unit)? = null, onTap: () -> Unit): Modifier =
    combinedClickable(onClick = onTap, onLongClick = onLong)

/** Themed icons on or off, read wherever icons are drawn. */
val LocalThemedIcons = androidx.compose.runtime.staticCompositionLocalOf { false }

@Composable
fun AppIcon(app: AppEntry?, size: Dp, modifier: Modifier = Modifier) {
    val pal = LocalPal.current
    when {
        app == null -> Box(modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(pal.l5))
        LocalThemedIcons.current && app.mono != null -> Box(
            modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(mix(pal.l4, pal.acc, 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Image(app.mono, app.label, Modifier.size(size * 0.86f), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(pal.acc))
        }
        else -> Image(app.icon, app.label, modifier.size(size))
    }
}

@Composable
fun PairIcon(a: AppEntry?, b: AppEntry?, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(LocalPal.current.l5)) {
        AppIcon(a, size * 0.52f, Modifier.offset(size * 0.1f, size * 0.1f))
        AppIcon(b, size * 0.52f, Modifier.offset(size * 0.38f, size * 0.38f))
    }
}

@Composable
fun Cover(art: ImageBitmap?, size: Dp, modifier: Modifier = Modifier) {
    val pal = LocalPal.current
    val shape = RoundedCornerShape(size * 0.22f)
    if (art != null) {
        Image(art, null, modifier.size(size).clip(shape), contentScale = ContentScale.Crop)
    } else {
        Box(modifier.size(size).clip(shape).background(pal.l5), contentAlignment = Alignment.Center) {
            Glyph("music_note", size = size * 0.5f, color = pal.sub)
        }
    }
}

@Composable
fun AccentButton(text: String, enabled: Boolean = true, height: Dp = 34.dp, onClick: () -> Unit) {
    val pal = LocalPal.current
    Box(
        Modifier
            .height(height)
            .clip(RoundedCornerShape(Rad.segment))
            .background(pal.acc)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Lbl(text, color = pal.ink, weight = FontWeight.SemiBold)
    }
}

@Composable
fun PlainButton(text: String, glyph: String? = null, height: Dp = 34.dp, color: Color? = null, onClick: () -> Unit) {
    val pal = LocalPal.current
    Row(
        Modifier
            .height(height)
            .clip(RoundedCornerShape(Rad.segment))
            .background(color ?: pal.l5)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            Glyph(glyph, size = 18.dp)
            Spacer(Modifier.width(6.dp))
        }
        Lbl(text, size = 13.sp)
    }
}

@Composable
fun IconButton(name: String, size: Dp = 32.dp, color: Color = LocalPal.current.sub, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(Rad.segment)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(name, size = size * 0.62f, color = color)
    }
}

@Composable
fun MiniSwitch(on: Boolean, onClick: () -> Unit) {
    val pal = LocalPal.current
    Box(
        Modifier.size(36.dp, 20.dp).clip(CircleShape).background(if (on) pal.acc else pal.l5).clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .align(if (on) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(2.dp)
                .size(16.dp)
                .clip(CircleShape)
                .background(if (on) pal.ink else pal.sub),
        )
    }
}

/** Where [m] is in its song, updated once a second while it plays. */
@Composable
fun rememberPosition(m: MediaNow): androidx.compose.runtime.State<Long> =
    androidx.compose.runtime.produceState(m.positionNow(), m) {
        while (true) {
            value = m.positionNow()
            if (!m.playing) break
            kotlinx.coroutines.delay(1000)
        }
    }
