package dev.ninomae.home

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter

/**
 * The Ninomae stack (W1a): one joined card on frosted glass. A greeting with the date and a weather
 * chip, the player in solid rose (like the widgets page), then your pairs and recent apps in a row.
 */
@Composable
fun Widgets(st: HomeState, ui: Ui, modifier: Modifier) {
    val media by Media.now.collectAsState()
    val jump = jumpItems(st)
    val big = Rad.normal
    val j = Rad.joined
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val greetBottom = if (media == null && jump.isEmpty()) big else j
        Greeting(st, corners(big, big, greetBottom, greetBottom), Modifier.clickable { ui.toggle(Layer.Deck) })
        media?.let { PlayerCard(it, corners(j, j, if (jump.isEmpty()) big else j, if (jump.isEmpty()) big else j)) }
        if (jump.isNotEmpty()) JumpBack(st, jump, corners(j, j, big, big))
    }
}

/** Good morning (and your name), the full date, and the weather as a chip on the right. No battery. */
@Composable
fun Greeting(st: HomeState, shape: Shape, modifier: Modifier = Modifier) {
    val pal = LocalPal.current
    val now = rememberNow()
    Row(
        Modifier.fillMaxWidth().height(64.dp).frosted(st, shape, wash = 0.34f, edge = 0.2f).then(modifier).padding(start = 18.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Title(greeting(now, st.name), size = 21.sp)
            Lbl(now.format(DateTimeFormatter.ofPattern("EEEE d MMMM")), size = 12.5.sp, color = pal.sub)
        }
        Glance.weather?.let { w ->
            Row(
                Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.10f)).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(w.glyph, size = 18.dp, color = pal.acc, fill = true)
                Spacer(Modifier.width(6.dp))
                Num(w.temp, size = 15.sp)
            }
        }
    }
}

/** The player in solid rose: cover, title and artist, a thin progress line, next and pause. */
@Composable
fun PlayerCard(m: MediaNow, shape: Shape) {
    val pal = LocalPal.current
    // Read only by the line's drawing, so the card itself doesn't redraw every second.
    val pos = rememberPosition(m)
    Row(
        Modifier.fillMaxWidth().height(92.dp).clip(shape).background(pal.acc).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(m.art, 68.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Title(m.title, size = 17.sp, color = pal.ink)
            Lbl(m.artist.ifBlank { m.app }, size = 12.5.sp, color = pal.ink.copy(alpha = 0.8f))
            if (m.duration > 0) {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.fillMaxWidth(0.85f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(pal.ink.copy(alpha = 0.2f))
                        .drawBehind {
                            val w = size.width * (pos.value.toFloat() / m.duration).coerceIn(0f, 1f)
                            drawRect(pal.ink.copy(alpha = 0.8f), size = androidx.compose.ui.geometry.Size(w, size.height))
                        },
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Glyph("skip_next", Modifier.clip(RoundedCornerShape(12.dp)).clickable { Media.next() }.padding(6.dp), size = 24.dp, color = pal.ink, fill = true)
        Spacer(Modifier.width(4.dp))
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(pal.ink).clickable { Media.toggle() },
            contentAlignment = Alignment.Center,
        ) { Glyph(if (m.playing) "pause" else "play_arrow", size = 22.dp, color = pal.acc, fill = true) }
    }
}

sealed interface Jump {
    data class P(val pair: AppPair) : Jump
    data class A(val app: AppEntry) : Jump
}

@Composable
fun jumpItems(st: HomeState): List<Jump> {
    val pairs = st.pairs.sortedByDescending { it.id == st.activePair }.take(2).map { Jump.P(it) }
    val apps = st.recent.mapNotNull { st.app(it) }.filter { it.key !in st.hidden }.take(5 - pairs.size).map { Jump.A(it) }
    return pairs + apps
}

/** Your pairs and recent apps, five in a row on glass, evenly spaced. */
@Composable
fun JumpBack(st: HomeState, items: List<Jump>, shape: Shape) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().height(84.dp).frosted(st, shape, wash = 0.34f, edge = 0.2f).padding(horizontal = 6.dp)) {
        items.forEach { item ->
            val (label, click) = when (item) {
                is Jump.P -> item.pair.name to { Actions.openPair(ctx, st, item.pair) }
                is Jump.A -> item.app.label to { Actions.open(ctx, st, item.app) }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(Rad.container)).pressScale().clickable { click() },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                when (item) {
                    is Jump.P -> PairIcon(st.app(item.pair.left), st.app(item.pair.right), 48.dp)
                    is Jump.A -> GlassIcon(item.app, 48.dp)
                }
                Spacer(Modifier.height(5.dp))
                Lbl(label, Modifier.padding(horizontal = 2.dp), size = 11.5.sp, color = pal.sub, align = TextAlign.Center)
            }
        }
        repeat(5 - items.size) { Spacer(Modifier.weight(1f)) }
    }
}
