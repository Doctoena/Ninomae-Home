package dev.ninomae.home

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter

/** Desktop stack: greeting, player, jump back in, joined like the shell's sidebar. */
@Composable
fun Widgets(st: HomeState, ui: Ui, modifier: Modifier) {
    val media by Media.now.collectAsState()
    val jump = jumpItems(st)
    val big = Rad.normal
    val j = Rad.joined
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val greetBottom = if (media == null && jump.isEmpty()) big else j
        Greeting(st, corners(big, big, greetBottom, greetBottom), Modifier.clickable { ui.toggle(Layer.Sidebar) })
        media?.let { PlayerCard(it, corners(j, j, if (jump.isEmpty()) big else j, if (jump.isEmpty()) big else j)) }
        if (jump.isNotEmpty()) JumpBack(st, jump, corners(j, j, big, big))
    }
}

@Composable
fun Avatar(st: HomeState, size: Dp) {
    val pal = LocalPal.current
    Box(Modifier.size(size).clip(CircleShape).background(pal.sec), contentAlignment = Alignment.Center) {
        val initial = st.name.trim().firstOrNull()?.uppercase()
        if (initial != null) Title(initial, size = (size.value * 0.42f).sp, color = pal.acc)
        else Glyph("person", size = size * 0.55f, color = pal.acc, fill = true)
    }
}

@Composable
fun Greeting(st: HomeState, shape: Shape, modifier: Modifier = Modifier) {
    val pal = LocalPal.current
    val now = rememberNow()
    Piece(modifier.fillMaxWidth().height(74.dp), shape) {
        Row(Modifier.fillMaxHeight().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(st, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Title(greeting(now, st.name), size = 19.sp)
                Lbl(
                    "${now.format(DateTimeFormatter.ofPattern("EEE d MMM"))}  ·  ${Status.battery}%  ·  ${uptime()}",
                    size = 13.sp,
                    color = pal.sub,
                )
            }
        }
    }
}

@Composable
fun PlayerCard(m: MediaNow, shape: Shape) {
    val pal = LocalPal.current
    var pos by remember { mutableLongStateOf(m.positionNow()) }
    LaunchedEffect(m) {
        while (true) {
            pos = m.positionNow()
            if (!m.playing) break
            delay(1000)
        }
    }
    val tint = m.tint?.let { mix(pal.l3, it, 0.24f) } ?: pal.l3
    Piece(Modifier.fillMaxWidth().height(98.dp), shape, tint) {
        Row(Modifier.fillMaxHeight().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(m.art, 68.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Title(m.title, size = 17.sp)
                Lbl(listOf(m.artist, m.app).filter { it.isNotBlank() }.joinToString("  ·  "), size = 12.sp, color = pal.sub)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton("skip_previous", 30.dp, pal.fg) { Media.prev() }
                    Box(
                        Modifier.size(32.dp).clip(RoundedCornerShape(Rad.segment)).background(pal.acc).clickable { Media.toggle() },
                        contentAlignment = Alignment.Center,
                    ) { Glyph(if (m.playing) "pause" else "play_arrow", size = 20.dp, color = pal.ink, fill = true) }
                    IconButton("skip_next", 30.dp, pal.fg) { Media.next() }
                    Spacer(Modifier.weight(1f))
                    if (m.duration > 0) Num("${mmss(pos)} / ${mmss(m.duration)}", size = 11.sp, color = pal.sub)
                }
            }
        }
        if (m.duration > 0) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 17.dp)
                    .fillMaxWidth((pos.toFloat() / m.duration).coerceIn(0f, 1f))
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(pal.acc),
            )
        }
    }
}

sealed interface Jump {
    data class P(val pair: AppPair) : Jump
    data class A(val app: AppEntry) : Jump
}

@Composable
fun jumpItems(st: HomeState): List<Jump> {
    val pairs = st.pairs.sortedByDescending { it.id == st.activePair }.take(2).map { Jump.P(it) }
    val apps = st.recent.mapNotNull { st.app(it) }.take(4 - pairs.size).map { Jump.A(it) }
    return pairs + apps
}

@Composable
fun JumpBack(st: HomeState, items: List<Jump>, shape: Shape) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    Piece(Modifier.fillMaxWidth(), shape) {
        Column(Modifier.padding(14.dp)) {
            Title("Jump back in", size = 14.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items.forEach { item ->
                    val (label, click) = when (item) {
                        is Jump.P -> item.pair.name to { Actions.openPair(ctx, st, item.pair) }
                        is Jump.A -> item.app.label to { Actions.open(ctx, st, item.app) }
                    }
                    Column(
                        Modifier.width(58.dp).clip(RoundedCornerShape(Rad.container)).clickable { click() }.padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        when (item) {
                            is Jump.P -> PairIcon(st.app(item.pair.left), st.app(item.pair.right), 48.dp)
                            is Jump.A -> AppIcon(item.app, 48.dp)
                        }
                        Spacer(Modifier.height(5.dp))
                        Lbl(label, size = 12.sp, color = pal.sub, align = TextAlign.Center)
                    }
                }
            }
        }
    }
}
