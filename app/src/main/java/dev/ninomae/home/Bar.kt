package dev.ninomae.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Replaces Android's status bar on the home screen: pairs left, player middle, status and clock right.
 * It sits right at the top edge; only a piece that would run into the camera hole steps sideways around it.
 */
@Composable
fun Bar(st: HomeState, ui: Ui, compact: Boolean, modifier: Modifier) {
    val view = LocalView.current
    val gap = with(LocalDensity.current) { 8.dp.toPx() }
    var bar by remember { mutableStateOf(Rect.Zero) }
    var left by remember { mutableStateOf(Rect.Zero) }
    var mid by remember { mutableStateOf(Rect.Zero) }
    var right by remember { mutableStateOf(Rect.Zero) }
    // Camera holes along the top edge, in the same (window) coordinates as boundsInRoot.
    val holes = remember(bar) {
        view.rootWindowInsets?.displayCutout?.boundingRects.orEmpty()
            .filter { it.top <= 0 && it.width() > 0 && it.height() > 0 }
            .map { Rect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }
    }
    fun hit(r: Rect) = holes.firstOrNull { r != Rect.Zero && it.left < r.right + gap && it.right > r.left - gap && it.top < r.bottom }
    // Push each group just clear of a hole it would touch.
    val leftShift = hit(left)?.let { it.right + gap - left.left } ?: 0f
    val rightShift = hit(right)?.let { it.left - gap - right.right } ?: 0f
    val midShift = hit(mid)?.let { h -> if (mid.center.x < h.center.x) h.left - gap - mid.right else h.right + gap - mid.left } ?: 0f

    if (compact) {
        // Cover screen: the camera sits in the middle, so it's clock + pairs | camera | status, like a phone.
        Box(modifier.height(36.dp)) {
            Row(Modifier.align(Alignment.CenterStart), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ClockPiece(corners(Rad.container, Rad.joined, Rad.joined, Rad.container))
                PairsPiece(st, ui, true, Modifier, corners(Rad.joined, Rad.container, Rad.container, Rad.joined))
            }
            StatusPieces(st, ui, Modifier.align(Alignment.CenterEnd), withClock = false)
        }
        return
    }
    Box(modifier.height(36.dp).onGloballyPositioned { bar = it.boundsInRoot() }) {
        PairsPiece(
            st, ui, compact,
            Modifier.align(Alignment.CenterStart)
                .onGloballyPositioned { left = it.boundsInRoot() }
                .offset { IntOffset(leftShift.roundToInt(), 0) },
        )
        if (!compact) PlayerPiece(
            ui,
            Modifier.align(Alignment.Center)
                .onGloballyPositioned { mid = it.boundsInRoot() }
                .offset { IntOffset(midShift.roundToInt(), 0) },
        )
        StatusPieces(
            st, ui,
            Modifier.align(Alignment.CenterEnd)
                .onGloballyPositioned { right = it.boundsInRoot() }
                .offset { IntOffset(rightShift.roundToInt(), 0) },
        )
    }
}

@Composable
private fun PairsPiece(st: HomeState, ui: Ui, compact: Boolean, modifier: Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(Rad.container)) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    Piece(modifier.height(36.dp), shape) {
        Row(
            Modifier.fillMaxHeight().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val shown = if (compact) st.pairs.take(3) else st.pairs.take(5)
            shown.forEach { p ->
                var rect by remember { mutableStateOf(Rect.Zero) }
                val active = st.activePair == p.id
                Row(
                    Modifier
                        .height(28.dp)
                        .onGloballyPositioned { rect = it.boundsInRoot() }
                        .clip(RoundedCornerShape(Rad.segment))
                        .background(if (active) pal.acc else pal.l3)
                        .press(onLong = { ui.showPairMenu(p, rect, above = false) }) { Actions.openPair(ctx, st, p) }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    AppIcon(st.app(p.left), 15.dp)
                    AppIcon(st.app(p.right), 15.dp)
                    if (!compact) {
                        Spacer(Modifier.width(3.dp))
                        Lbl(
                            p.name,
                            Modifier.widthIn(max = 90.dp),
                            size = 13.sp,
                            weight = FontWeight.SemiBold,
                            color = if (active) pal.ink else pal.fg,
                        )
                    }
                }
            }
            Row(
                Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(Rad.segment))
                    .clickable { ui.newPair() }
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph("add", size = 18.dp, color = pal.sub)
                if (st.pairs.isEmpty() && !compact) {
                    Spacer(Modifier.width(4.dp))
                    Lbl("New pair", size = 13.sp, color = pal.sub)
                }
            }
        }
    }
}

@Composable
private fun PlayerPiece(ui: Ui, modifier: Modifier) {
    val media by Media.now.collectAsState()
    val m = media ?: return
    val pal = LocalPal.current
    Piece(modifier.height(36.dp).width(236.dp).clickable { ui.toggle(Layer.Deck) }) {
        Row(
            Modifier.fillMaxHeight().padding(start = 5.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(m.art, 26.dp)
            Spacer(Modifier.width(8.dp))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Lbl(m.title, Modifier.weight(1f, fill = false), size = 13.sp, weight = FontWeight.SemiBold)
                if (m.artist.isNotBlank()) Lbl("  ·  ${m.artist}", Modifier.weight(1f, fill = false), size = 13.sp, color = pal.sub)
            }
            IconButton(if (m.playing) "pause" else "play_arrow", size = 30.dp, color = pal.fg) { Media.toggle() }
        }
    }
}

@Composable
private fun StatusPieces(st: HomeState, ui: Ui, modifier: Modifier, withClock: Boolean = true) {
    val pal = LocalPal.current
    val notes by NotifService.notes.collectAsState()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Piece(
            Modifier.height(36.dp).clickable { ui.toggle(Layer.Deck) },
            corners(Rad.container, Rad.joined, Rad.joined, Rad.container),
        ) {
            Row(
                Modifier.fillMaxHeight().padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Glyph(if (Status.wifi) "wifi" else "wifi_off", size = 18.dp, color = if (Status.wifi) pal.fg else pal.dim)
                if (Status.bt) Glyph("bluetooth", size = 18.dp)
                if (Status.dnd) Glyph("do_not_disturb_on", size = 17.dp, color = pal.acc, fill = true)
                Battery()
                Num("${Status.battery}", size = 13.sp)
            }
        }
        Piece(
            Modifier.size(36.dp).clickable { ui.toggle(Layer.Deck) },
            if (withClock) RoundedCornerShape(Rad.joined) else corners(Rad.joined, Rad.container, Rad.container, Rad.joined),
        ) {
            Glyph("notifications", Modifier.align(Alignment.Center), size = 19.dp, fill = notes.isNotEmpty())
            if (notes.isNotEmpty()) {
                Box(Modifier.align(Alignment.Center).offset(6.dp, (-6).dp).size(7.dp).clip(CircleShape).background(pal.acc))
            }
        }
        if (withClock) ClockPiece(corners(Rad.joined, Rad.container, Rad.container, Rad.joined))
    }
}

@Composable
private fun ClockPiece(shape: androidx.compose.ui.graphics.Shape) {
    val ctx = LocalContext.current
    val now = rememberNow()
    Piece(Modifier.height(36.dp).clickable { Actions.clock(ctx) }, shape) {
        Num(clockText(now), Modifier.align(Alignment.Center).padding(horizontal = 12.dp), size = 15.sp)
    }
}

/** A small horizontal battery like the shell's: outline, fill by level, a bolt when charging. */
@Composable
fun Battery() {
    val pal = LocalPal.current
    val low = Status.battery <= 15 && !Status.charging
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(22.dp, 12.dp).border(1.5.dp, pal.fg.copy(alpha = 0.8f), RoundedCornerShape(3.5.dp)).padding(2.dp),
        ) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth((Status.battery / 100f).coerceIn(0.06f, 1f))
                    .clip(RoundedCornerShape(1.5.dp)).background(if (low) pal.acc else pal.fg),
            )
            if (Status.charging) Glyph("bolt", Modifier.align(Alignment.Center), size = 12.dp, color = pal.l3, fill = true)
        }
        Box(Modifier.padding(start = 1.dp).size(2.dp, 5.dp).clip(RoundedCornerShape(1.dp)).background(pal.fg.copy(alpha = 0.8f)))
    }
}
