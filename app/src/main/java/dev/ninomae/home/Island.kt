package dev.ninomae.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/*
 * The cover screen's island: a black pill around the camera, so the hole disappears into it.
 * (V1) At rest it holds the time and the battery as a small ring. While music plays: the art with a
 * progress ring and the time, then the battery and pause. A new notification opens it for a few
 * seconds; plugging in flashes the charge; tapping it while music plays opens a player tinted by
 * the art. Wi-Fi and the bell sit in black circles in the corners (Bar.kt).
 */

private enum class IslandMode { Rest, Playing, Charging, Note, Player }

val IslandBlack = Color(0xFF080606)

/** Where the camera is across the screen (px from the left), and how wide, from what Android reports. */
@Composable
private fun rememberCamera(): Pair<Float, Float> {
    val view = LocalView.current
    val density = LocalDensity.current
    return remember(view.width) {
        val hole = (view.rootWindowInsets?.displayCutout ?: view.display?.cutout)?.boundingRects.orEmpty()
            .filter { it.width() > 0 && it.height() > 0 }
            .minByOrNull { it.top }
        if (hole != null) hole.exactCenterX() to hole.width().toFloat()
        else view.width / 2f to with(density) { 22.dp.toPx() }
    }
}

@Composable
fun Island(st: HomeState, ui: Ui, modifier: Modifier) {
    val density = LocalDensity.current
    val media by Media.now.collectAsState()
    val notes by NotifService.notes.collectAsState()
    val (camX, camW) = rememberCamera()
    val camGap = with(density) { camW.toDp() } + 16.dp

    // A new notification shows for 3 s. The ones already there when the launcher starts don't.
    var seen by remember { mutableStateOf<Set<String>?>(null) }
    var flashNote by remember { mutableStateOf<Note?>(null) }
    LaunchedEffect(notes) {
        val keys = notes.map { it.key }.toSet()
        val before = seen
        seen = keys
        if (before == null) return@LaunchedEffect
        val fresh = notes.filter { it.key !in before && System.currentTimeMillis() - it.time < 15_000 }.maxByOrNull { it.time }
        if (fresh != null) { flashNote = fresh; delay(3000); flashNote = null }
    }
    // Plugging in flashes the charge for 2 s.
    var wasCharging by remember { mutableStateOf(Status.charging) }
    var flashCharge by remember { mutableStateOf(false) }
    LaunchedEffect(Status.charging) {
        val now = Status.charging
        val plugged = now && !wasCharging
        wasCharging = now
        if (plugged) { flashCharge = true; delay(2000); flashCharge = false }
    }
    // Tapped while playing: the player, until tapped again or left alone for 8 s.
    var player by remember { mutableStateOf(false) }
    LaunchedEffect(player, media?.playing) { if (player) { delay(8000); player = false } }
    if (media == null) player = false

    val mode = when {
        player && media != null -> IslandMode.Player
        flashNote != null -> IslandMode.Note
        flashCharge -> IslandMode.Charging
        media != null -> IslandMode.Playing
        else -> IslandMode.Rest
    }
    val big = mode == IslandMode.Player || mode == IslandMode.Note
    val radius by animateDpAsState(if (big) 30.dp else 20.dp, spring(0.8f, 500f), label = "island")

    // Centre on the camera; a wide island just stays on screen.
    Box(
        modifier.fillMaxWidth().layout { m, c ->
            val margin = 12.dp.roundToPx()
            val p = m.measure(c.copy(minWidth = 0, maxWidth = (c.maxWidth - 2 * margin).coerceAtLeast(0)))
            val x = (camX - p.width / 2f).roundToInt().coerceIn(margin, (c.maxWidth - p.width - margin).coerceAtLeast(margin))
            layout(c.maxWidth, p.height) { p.place(x, 0) }
        },
    ) {
        AnimatedContent(
            mode,
            Modifier.clip(RoundedCornerShape(radius)).background(IslandBlack),
            transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform(clip = true) },
            label = "island",
        ) { m ->
            when (m) {
                IslandMode.Rest -> Small(camGap, 56.dp, onClick = { ui.toggle(Layer.Deck) },
                    left = { Title(clockText(rememberNow()), size = 16.sp) },
                    right = { BatteryRing() })
                IslandMode.Charging -> Small(camGap, 70.dp, onClick = { ui.toggle(Layer.Deck) },
                    left = { Glyph("bolt", size = 18.dp, color = LocalPal.current.acc, fill = true); Num("${Status.battery}%", size = 14.sp) },
                    right = { Lbl("charging", size = 12.sp, color = LocalPal.current.acc, weight = FontWeight.SemiBold) })
                IslandMode.Playing -> media?.let { md ->
                    Small(camGap, 84.dp, onClick = { player = true },
                        left = { ArtRing(md); Spacer(Modifier.width(8.dp)); Title(clockText(rememberNow()), size = 16.sp) },
                        right = { BatteryRing(); Spacer(Modifier.width(10.dp)); PauseDot(md) })
                }
                IslandMode.Note -> flashNote?.let { n -> NoteCard(st, ui, n, camGap) { flashNote = null } }
                IslandMode.Player -> media?.let { md -> PlayerCard(md, camGap) { player = false } }
            }
        }
    }
}

/** The pill at rest: [left] ends just before the camera, [right] starts just after, both [side] wide. */
@Composable
private fun Small(
    camGap: Dp, side: Dp, onClick: () -> Unit,
    left: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
    right: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(Modifier.height(40.dp).clickable(onClick = onClick).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.width(side), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically, content = left)
        Spacer(Modifier.width(camGap))
        Row(Modifier.width(side), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically, content = right)
    }
}

/** The album art in a circle with a thin rose ring for how far into the song it is (drawn, so it costs no recomposition). */
@Composable
private fun ArtRing(m: MediaNow) {
    val pal = LocalPal.current
    val pos = rememberPosition(m)
    Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
        Cover(m.art, 23.dp, Modifier.clip(CircleShape))
        Canvas(Modifier.size(30.dp)) {
            val w = 2.2.dp.toPx()
            val box = Size(size.width - w, size.height - w)
            val tl = Offset(w / 2, w / 2)
            drawArc(Color.White.copy(alpha = 0.22f), 0f, 360f, false, tl, box, style = Stroke(w))
            if (m.duration > 0) drawArc(pal.acc, -90f, 360f * (pos.value.toFloat() / m.duration).coerceIn(0f, 1f), false, tl, box, style = Stroke(w))
        }
    }
}

/** The battery as a thin ring, filled to the charge (rose; the empty part faint), the number inside. */
@Composable
private fun BatteryRing() {
    val pal = LocalPal.current
    val level = Status.battery
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(24.dp)) {
            val w = 2.4.dp.toPx()
            val box = Size(size.width - w, size.height - w)
            val tl = Offset(w / 2, w / 2)
            drawArc(Color.White.copy(alpha = 0.22f), 0f, 360f, false, tl, box, style = Stroke(w))
            drawArc(pal.acc, -90f, 360f * (level / 100f).coerceIn(0f, 1f), false, tl, box, style = Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        Num("$level", size = 9.5.sp)
    }
}

@Composable
private fun PauseDot(m: MediaNow) {
    val pal = LocalPal.current
    Box(Modifier.size(28.dp).clip(CircleShape).background(pal.acc).clickable { Media.toggle() }, contentAlignment = Alignment.Center) {
        Glyph(if (m.playing) "pause" else "play_arrow", size = 16.dp, color = pal.ink, fill = true)
    }
}

/** The top row of an open island: [left] and [right] on either side of the camera. */
@Composable
private fun TopRow(camGap: Dp, left: @Composable () -> Unit, right: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { left() }
        Spacer(Modifier.width(camGap))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { right() }
    }
}

@Composable
private fun NoteCard(st: HomeState, ui: Ui, n: Note, camGap: Dp, onDone: () -> Unit) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val app = st.byPkg[n.pkg]
    Column(
        Modifier.fillMaxWidth().clickable { onDone(); NotifService.open(ctx, n) }.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp),
    ) {
        TopRow(camGap, left = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (app == null && n.icon != null) androidx.compose.foundation.Image(n.icon, null, Modifier.size(18.dp)) else AppIcon(app, 18.dp)
                Spacer(Modifier.width(8.dp))
                Lbl(n.app, size = 12.sp, color = pal.sub, weight = FontWeight.SemiBold)
            }
        }, right = { Lbl("now", size = 12.sp, color = pal.sub) })
        Spacer(Modifier.height(4.dp))
        Lbl(n.title, size = 15.sp, weight = FontWeight.SemiBold)
        if (n.text.isNotBlank()) Lbl(n.text, size = 13.5.sp, color = pal.sub, maxLines = 2)
    }
}

/** The open player: tinted by a blur of the album art, the art big, a seek bar with times, the controls centred. */
@Composable
private fun PlayerCard(m: MediaNow, camGap: Dp, onClose: () -> Unit) {
    val pal = LocalPal.current
    val pos = rememberPosition(m)
    var seekTo by remember(m) { mutableStateOf(-1L) }
    Box {
        m.art?.let { art ->
            androidx.compose.foundation.Image(
                art, null,
                Modifier.matchParentSize().blur(26.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Rectangle),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            Box(Modifier.matchParentSize().background(IslandBlack.copy(alpha = 0.55f)))
        }
        Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 16.dp, top = 4.dp, bottom = 14.dp)) {
            TopRow(
                camGap,
                left = { Lbl(m.app, Modifier.clickable(onClick = onClose), size = 11.5.sp, color = pal.acc, weight = FontWeight.Bold) },
                right = { Num(clockText(rememberNow()), Modifier.clickable(onClick = onClose), size = 12.5.sp, color = pal.sub) },
            )
            Spacer(Modifier.height(6.dp))
            Row {
                Cover(m.art, 76.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Lbl(m.title, size = 16.sp, weight = FontWeight.Black, family = TitleFont)
                    Lbl(m.artist, size = 13.sp, color = pal.sub, weight = FontWeight.Medium)
                    if (m.duration > 0) {
                        Spacer(Modifier.height(12.dp))
                        Canvas(
                            Modifier.fillMaxWidth().height(14.dp).slideToSet { v -> val t = (v * m.duration).toLong(); seekTo = t; Media.seek(t) },
                        ) {
                            val y = size.height / 2
                            val h = 4.dp.toPx()
                            val f = ((if (seekTo >= 0) seekTo else pos.value).toFloat() / m.duration).coerceIn(0f, 1f)
                            val r = androidx.compose.ui.geometry.CornerRadius(h / 2)
                            drawRoundRect(Color.White.copy(alpha = 0.2f), Offset(0f, y - h / 2), Size(size.width, h), r)
                            drawRoundRect(pal.acc, Offset(0f, y - h / 2), Size(size.width * f, h), r)
                        }
                        Row {
                            PositionNum(pos, seekTo)
                            Spacer(Modifier.weight(1f))
                            Num(mmss(m.duration), size = 10.5.sp, color = pal.sub)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton("skip_previous", 44.dp, pal.fg) { Media.prev() }
                Spacer(Modifier.width(18.dp))
                Box(
                    Modifier.size(56.dp, 38.dp).clip(RoundedCornerShape(13.dp)).background(pal.acc).clickable { Media.toggle() },
                    contentAlignment = Alignment.Center,
                ) { Glyph(if (m.playing) "pause" else "play_arrow", size = 22.dp, color = pal.ink, fill = true) }
                Spacer(Modifier.width(18.dp))
                IconButton("skip_next", 44.dp, pal.fg) { Media.next() }
            }
        }
    }
}

@Composable
private fun PositionNum(pos: androidx.compose.runtime.State<Long>, seekTo: Long) =
    Num(mmss(if (seekTo >= 0) seekTo else pos.value), size = 10.5.sp, color = LocalPal.current.sub)
