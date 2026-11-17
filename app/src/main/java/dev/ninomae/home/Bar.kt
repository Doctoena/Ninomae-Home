package dev.ninomae.home

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Replaces Android's status bar on the home screen: pairs left, player middle, status and clock right. */
@Composable
fun Bar(st: HomeState, ui: Ui, compact: Boolean, modifier: Modifier) {
    Box(modifier.height(36.dp)) {
        PairsPiece(st, ui, compact, Modifier.align(Alignment.CenterStart))
        if (!compact) PlayerPiece(ui, Modifier.align(Alignment.Center))
        StatusPieces(st, ui, Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun PairsPiece(st: HomeState, ui: Ui, compact: Boolean, modifier: Modifier) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    Piece(modifier.height(36.dp), RoundedCornerShape(Rad.container)) {
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
                if (st.pairs.isEmpty()) {
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
    Piece(modifier.height(36.dp).width(236.dp).clickable { ui.toggle(Layer.Sidebar) }) {
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
private fun StatusPieces(st: HomeState, ui: Ui, modifier: Modifier) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val notes by NotifService.notes.collectAsState()
    val now = rememberNow()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Piece(
            Modifier.height(36.dp).clickable { ui.toggle(Layer.Sidebar) },
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
                Glyph(batteryGlyph(), Modifier.rotate(90f), size = 18.dp, color = if (Status.battery <= 15 && !Status.charging) pal.acc else pal.fg)
                Num("${Status.battery}", size = 13.sp)
            }
        }
        Piece(
            Modifier.size(36.dp).clickable { ui.toggle(Layer.Sidebar) },
            RoundedCornerShape(Rad.joined),
        ) {
            Glyph("notifications", Modifier.align(Alignment.Center), size = 19.dp, fill = notes.isNotEmpty())
            if (notes.isNotEmpty()) {
                Box(Modifier.align(Alignment.Center).offset(6.dp, (-6).dp).size(7.dp).clip(CircleShape).background(pal.acc))
            }
        }
        Piece(
            Modifier.height(36.dp).clickable { Actions.clock(ctx) },
            corners(Rad.joined, Rad.container, Rad.container, Rad.joined),
        ) {
            Num(clockText(now), Modifier.align(Alignment.Center).padding(horizontal = 12.dp), size = 15.sp)
        }
    }
}

fun batteryGlyph(): String = when {
    Status.charging -> "battery_charging_full"
    Status.battery >= 95 -> "battery_full"
    else -> "battery_${(Status.battery / 15).coerceIn(0, 6)}_bar"
}
