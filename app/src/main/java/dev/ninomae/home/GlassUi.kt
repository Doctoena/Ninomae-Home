package dev.ninomae.home

import androidx.compose.foundation.layout.offset

import androidx.compose.ui.composed
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* The frosted-glass look shared by the app drawer and settings. */

/** A frosted wash over the blurred wallpaper: light in the dark theme, brighter in the light. */
@Composable
fun glass(a: Float): Color = if (LocalPal.current.dark) Color.White.copy(alpha = a) else Color.White.copy(alpha = (a * 4f).coerceAtMost(0.7f))

@Composable
fun glassEdge(): Color = if (LocalPal.current.dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.5f)

/**
 * The frosted wallpaper filling the screen, then a wash of the theme. [progress] is read only while
 * drawing, so fading it in and out doesn't recompose anything.
 */
@Composable
fun GlassBackdrop(st: HomeState, progress: () -> Float) {
    val pal = LocalPal.current
    val frosted = st.glass
    if (frosted != null) {
        Image(
            frosted, null,
            Modifier.fillMaxSize().graphicsLayer {
                // The glass eases in from a touch closer, like it's settling.
                val e = progress().coerceIn(0f, 1f)
                alpha = e
                val k = 1.05f - 0.05f * e
                scaleX = k; scaleY = k
            },
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.High,
        )
    }
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = progress().coerceIn(0f, 1f) }
            .background(pal.l0.copy(alpha = if (frosted != null) 0.34f else 0.84f)),
    )
}

@Composable
fun GlassButton(glyph: String, size: Dp = 38.dp, onClick: () -> Unit) {
    val pal = LocalPal.current
    Box(
        Modifier.size(size).clip(CircleShape).background(glass(if (size > 40.dp) 0.12f else 0.08f))
            .border(1.dp, glassEdge(), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Glyph(glyph, size = size * 0.5f, color = if (size > 40.dp) pal.fg else pal.sub) }
}

/** A round accent badge holding a symbol. */
@Composable
fun GlassBadge(glyph: String, size: Dp = 38.dp) {
    val pal = LocalPal.current
    Box(Modifier.size(size).clip(CircleShape).background(pal.acc.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Glyph(glyph, size = size * 0.52f, color = pal.acc)
    }
}

/** An app's icon on glass: themed icons get a frosted tile (no sheen line: the user didn't like it), others draw as they are. */
@Composable
fun GlassIcon(app: AppEntry, size: Dp) {
    val pal = LocalPal.current
    if (LocalThemedIcons.current && app.mono != null) {
        val shape = RoundedCornerShape(size * 0.34f)
        Box(
            Modifier.size(size).clip(shape).background(glass(0.12f)).border(1.dp, Color.White.copy(alpha = 0.16f), shape),
            contentAlignment = Alignment.Center,
        ) { Image(app.mono, app.label, Modifier.size(size * 0.86f), colorFilter = ColorFilter.tint(pal.acc)) }
    } else AppIcon(app, size)
}

/** Your home screen in miniature: the wallpaper, the clock in pieces, the dock. [w] is its width. */
@Composable
fun HomePreview(st: HomeState, w: Dp) {
    val pal = LocalPal.current
    val pic = st.wallPreview ?: st.glass
    val shape = RoundedCornerShape(w * 0.15f)
    Box(Modifier.size(w, w * 2f).clip(shape).background(pal.l0).border(1.dp, glassEdge(), shape)) {
        if (pic != null) Image(pic, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.High)
        Column(Modifier.align(Alignment.TopCenter).padding(top = w * 0.32f), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(w * 0.5f, w * 0.2f).clip(RoundedCornerShape(w * 0.05f)).background(pal.l1), contentAlignment = Alignment.Center) {
                val time = androidx.compose.runtime.remember { android.text.format.DateFormat.format("h:mm", System.currentTimeMillis()).toString() }
                Title(time, size = (w.value * 0.12f).sp)
            }
            Spacer(Modifier.height(w * 0.015f))
            Row(horizontalArrangement = Arrangement.spacedBy(w * 0.015f)) {
                Box(Modifier.size(w * 0.29f, w * 0.08f).clip(RoundedCornerShape(w * 0.03f)).background(pal.l1))
                Box(Modifier.size(w * 0.19f, w * 0.08f).clip(RoundedCornerShape(w * 0.03f)).background(pal.acc))
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = w * 0.09f).clip(RoundedCornerShape(w * 0.06f)).background(pal.l1)
                .padding(w * 0.03f),
            horizontalArrangement = Arrangement.spacedBy(w * 0.025f),
        ) {
            st.pinned.take(4).mapNotNull { st.app(it) }.forEach { GlassIcon(it, w * 0.12f) }
        }
    }
}

/** A small dot that says "look here". */
@Composable
fun Dot(modifier: Modifier = Modifier, size: Dp = 9.dp) {
    Box(modifier.size(size).clip(CircleShape).background(LocalPal.current.acc))
}


/**
 * A piece of frosted glass on the home screen: the frosted copy of the wallpaper drawn inside [shape],
 * lined up with where the wallpaper really is behind it, then a wash and a faint edge. Without a
 * frosted copy (wallpaper not picked in Ninomae) it's a plain dark piece.
 */
fun Modifier.frosted(st: HomeState, shape: androidx.compose.ui.graphics.Shape, wash: Float = 0.30f, edge: Float = 0.22f): Modifier =
    composed {
        val pal = LocalPal.current
        val view = androidx.compose.ui.platform.LocalView.current
        var origin by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(Offset.Zero) }
        val pic = st.glass
        this
            .onGloballyPositioned { origin = it.positionInRoot() }
            .clip(shape)
            .drawBehind {
                if (pic != null) {
                    val sw = view.width.toFloat()
                    val sh = view.height.toFloat()
                    val k = maxOf(sw / pic.width, sh / pic.height)
                    val dw = pic.width * k
                    val dh = pic.height * k
                    drawImage(
                        pic,
                        dstOffset = androidx.compose.ui.unit.IntOffset(((sw - dw) / 2 - origin.x).toInt(), ((sh - dh) / 2 - origin.y).toInt()),
                        dstSize = androidx.compose.ui.unit.IntSize(dw.toInt(), dh.toInt()),
                        filterQuality = FilterQuality.High,
                    )
                }
            }
            .background(if (pic != null) pal.l0.copy(alpha = wash) else pal.l1.copy(alpha = 0.88f))
            .border(1.dp, Color.White.copy(alpha = edge), shape)
    }

/** Unread notifications: a small rose bar just under an icon, the same everywhere (dock, home, folders, drawer). */
@Composable
fun androidx.compose.foundation.layout.BoxScope.NoteBar() {
    Box(
        Modifier.align(Alignment.BottomCenter).offset(y = 7.dp).size(16.dp, 4.dp)
            .clip(RoundedCornerShape(2.dp)).background(LocalPal.current.acc),
    )
}
