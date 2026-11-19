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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import java.time.format.DateTimeFormatter
import androidx.compose.ui.unit.Dp
/**
 * The bar that replaces Android's status bar on the home screen. Inner screen (B2a): frosted glass,
 * split where the camera is so the hole sits in the gap: pairs | player | Wi-Fi and the bell, then
 * the battery and clock after the camera; one piece if there's no camera along the top.
 * Cover screen: just the corners (Wi-Fi, the bell), around the island (Island.kt).
 */
@Composable
fun Bar(st: HomeState, ui: Ui, compact: Boolean, modifier: Modifier) {
    val view = LocalView.current
    val density = LocalDensity.current
    var bar by remember { mutableStateOf(Rect.Zero) }
    // The camera hole, in the same (window) coordinates as boundsInRoot.
    val hole = remember(bar) {
        val reported = (view.rootWindowInsets?.displayCutout ?: view.display?.cutout)?.boundingRects.orEmpty()
            .filter { it.width() > 0 && it.height() > 0 }
            .map { Rect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }
        reported.ifEmpty { listOfNotNull(knownCamera(view.rootView.width.toFloat(), density.density, compact)) }
            // Any hole the bar runs over (the inner screen's camera floats a few dp below the edge).
            .firstOrNull { bar != Rect.Zero && it.left > bar.left && it.right < bar.right && it.top < bar.bottom && it.bottom > bar.top }
    }
    val gap = 8.dp
    // Where the left piece stops and the right one starts, in dp from the bar's left edge.
    val split: Pair<Dp, Dp>? = hole?.let { h ->
        with(density) { ((h.left - bar.left).toDp() - gap) to ((h.right - bar.left).toDp() + gap) }
    }
    val big = 16.dp
    val small = 8.dp

    if (compact) {
        // Cover screen: the island (drawn over the home screen, see Island.kt) holds the middle and the battery;
        // Wi-Fi and the bell sit in the corners.
        val pal = LocalPal.current
        val notes by NotifService.notes.collectAsState()
        Box(modifier.height(40.dp)) {
            CornerDot(Modifier.align(Alignment.CenterStart), onClick = { ui.toggle(Layer.Deck) }) {
                Glyph(if (Status.wifi) "wifi" else "wifi_off", size = 17.dp, color = if (Status.wifi) pal.fg else pal.sub)
            }
            CornerDot(Modifier.align(Alignment.CenterEnd), onClick = { ui.toggle(Layer.Deck) }) {
                Glyph("notifications", size = 17.dp, fill = true)
                if (notes.isNotEmpty()) Box(Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 8.dp).size(7.dp).clip(CircleShape).background(pal.acc))
            }
        }
        return
    }
    Row(modifier.height(40.dp).onGloballyPositioned { bar = it.boundsInRoot() }) {
        if (split == null) {
            Piece(st, Modifier.weight(1f), RoundedCornerShape(big)) {
                Pairs(st, ui, false); Line(); Player(ui, Modifier.weight(1f)); Line(); Status(ui); Line(); ClockText(true)
            }
        } else {
            Piece(st, Modifier.width(split.first), corners(big, small, small, big)) {
                Pairs(st, ui, false); Line(); Player(ui, Modifier.weight(1f)); Line(); Status(ui, battery = false)
            }
            Spacer(Modifier.width(split.second - split.first))
            Piece(st, Modifier.weight(1f), corners(small, big, big, small)) {
                // The battery rides with the clock after the camera, so both pieces are about as full.
                Spacer(Modifier.weight(1f)); BatteryChip(ui); ClockText(false); Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * Cameras a phone doesn't report. Samsung hides the inner screen's camera from apps (the cutout is null),
 * so it's listed here from the phone's own cutout spec: x from the screen's middle, in dp.
 */
private fun knownCamera(widthPx: Float, density: Float, compact: Boolean): Rect? {
    val model = android.os.Build.MODEL ?: return null
    val (dx, top, size) = when {
        model.startsWith("SM-F966") && !compact -> Triple(190.857f, 6.857f, 26.667f) // Galaxy Z Fold 7, inner screen
        else -> return null
    }
    val cx = widthPx / 2 + dx * density
    val r = size * density / 2
    return Rect(cx - r, top * density, cx + r, top * density + 2 * r)
}

/** A small black circle in a corner of the cover bar, the island's colour, holding one icon. */
@Composable
private fun CornerDot(modifier: Modifier, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    Box(modifier.size(36.dp).clip(CircleShape).background(IslandBlack).clickable(onClick = onClick), contentAlignment = Alignment.Center, content = content)
}

@Composable
private fun Piece(st: HomeState, modifier: Modifier, shape: Shape, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier.fillMaxHeight().frosted(st, shape, wash = 0.32f, edge = 0.2f).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun Line() = Box(Modifier.padding(horizontal = 8.dp).size(1.dp, 20.dp).background(Color.White.copy(alpha = 0.18f)))

/** Saved pairs as chips (the one that's open in rose), then ＋ for a new one. */
@Composable
private fun Pairs(st: HomeState, ui: Ui, compact: Boolean) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        st.pairs.take(if (compact) 2 else 4).forEach { p ->
            var rect by remember { mutableStateOf(Rect.Zero) }
            val active = st.activePair == p.id
            Row(
                Modifier
                    .height(28.dp)
                    .onGloballyPositioned { rect = it.boundsInRoot() }
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) pal.acc else Color.White.copy(alpha = 0.10f))
                    .press(onLong = { ui.showPairMenu(p, rect, above = false) }) { Actions.openPair(ctx, st, p) }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                AppIcon(st.app(p.left), 15.dp)
                AppIcon(st.app(p.right), 15.dp)
                if (!compact) {
                    Spacer(Modifier.width(3.dp))
                    Lbl(p.name, Modifier.widthIn(max = 90.dp), size = 13.sp, weight = FontWeight.SemiBold, color = if (active) pal.ink else pal.fg)
                }
            }
        }
        Row(
            Modifier.height(28.dp).clip(RoundedCornerShape(10.dp)).clickable { ui.newPair() }.padding(horizontal = 6.dp),
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

/** What's playing: cover, title · artist, and a rose pause. Tap it for the deck. Empty space when nothing plays. */
@Composable
private fun Player(ui: Ui, modifier: Modifier) {
    val media by Media.now.collectAsState()
    val pal = LocalPal.current
    val m = media
    // Centred in the space between the pairs and the status, the pause right after the name.
    Row(
        modifier.fillMaxHeight().clickable(enabled = m != null) { ui.toggle(Layer.Deck) },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (m == null) return@Row
        Cover(m.art, 26.dp)
        Spacer(Modifier.width(8.dp))
        Row(Modifier.weight(1f, fill = false), verticalAlignment = Alignment.CenterVertically) {
            Lbl(m.title, Modifier.weight(1f, fill = false), size = 13.sp, weight = FontWeight.SemiBold)
            if (m.artist.isNotBlank()) Lbl("  ·  ${m.artist}", Modifier.weight(1f, fill = false), size = 13.sp, color = pal.sub)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(28.dp, 26.dp).clip(RoundedCornerShape(9.dp)).background(pal.acc).clickable { Media.toggle() },
            contentAlignment = Alignment.Center,
        ) { Glyph(if (m.playing) "pause" else "play_arrow", size = 17.dp, color = pal.ink, fill = true) }
    }
}

/** Wi-Fi, Silent when on, the battery, and the bell (a rose dot when there's something). Tap for the deck. */
@Composable
private fun Status(ui: Ui, battery: Boolean = true) {
    val pal = LocalPal.current
    val notes by NotifService.notes.collectAsState()
    Row(
        Modifier.fillMaxHeight().clip(RoundedCornerShape(10.dp)).clickable { ui.toggle(Layer.Deck) }.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Glyph(if (Status.wifi) "wifi" else "wifi_off", size = 18.dp, color = if (Status.wifi) pal.fg else pal.sub)
        if (Status.dnd) Glyph("do_not_disturb_on", size = 17.dp, color = pal.acc, fill = true)
        if (battery) {
            Battery()
            Num("${Status.battery}", size = 13.sp)
        }
        Spacer(Modifier.width(4.dp))
        Box {
            Glyph("notifications", size = 19.dp, fill = notes.isNotEmpty())
            if (notes.isNotEmpty()) Box(Modifier.align(Alignment.TopEnd).offset(2.dp, (-1).dp).size(7.dp).clip(CircleShape).background(pal.acc))
        }
    }
}

@Composable
private fun BatteryChip(ui: Ui) {
    Row(
        Modifier.fillMaxHeight().clip(RoundedCornerShape(10.dp)).clickable { ui.toggle(Layer.Deck) }.padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Battery()
        Num("${Status.battery}", size = 13.sp)
    }
}

/** The time in bold, and (with [date]) the day after it, "Wed 18" when [short]. Tap for Clock. */
@Composable
private fun ClockText(date: Boolean, short: Boolean = false) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val now = rememberNow()
    Row(
        Modifier.fillMaxHeight().clip(RoundedCornerShape(10.dp)).clickable { Actions.clock(ctx) }.padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Title(clockText(now), size = 16.sp)
        if (date) {
            Spacer(Modifier.width(8.dp))
            Lbl(now.format(DateTimeFormatter.ofPattern(if (short) "EEE d" else "EEE d MMM")), size = 12.5.sp, color = pal.sub, weight = FontWeight.Medium)
        }
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
