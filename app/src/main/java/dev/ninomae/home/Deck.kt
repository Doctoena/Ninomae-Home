package dev.ninomae.home

import android.accessibilityservice.AccessibilityService
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * C2 · Thumb deck. Notifications at the top; everything you press sits at the bottom under your thumb.
 * [p] is how far it's pulled down (0 closed, 1 open); pieces land one after another as it opens.
 */
@Composable
fun Deck(st: HomeState, ui: Ui, p: Float, modifier: Modifier) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val notes by NotifService.notes.collectAsState()
    val groups = remember(notes) { notes.groupBy { it.pkg }.values.toList() }
    val density = LocalDensity.current

    // Each piece trails the one before it, so the deck lands in order from the top.
    fun Modifier.landing(i: Int): Modifier = graphicsLayer {
        val lag = (i * 0.07f).coerceAtMost(0.5f)
        val local = ((p - lag) / (1f - lag)).coerceIn(0f, 1f)
        alpha = local
        translationY = -(1f - local) * with(density) { (60 + i * 18).dp.toPx() }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // ---------- notifications ----------
        Row(Modifier.fillMaxWidth().height(40.dp).landing(0), verticalAlignment = Alignment.CenterVertically) {
            Title("Notifications", Modifier.weight(1f).padding(start = 4.dp), size = 19.sp, color = Color.White)
            if (notes.isNotEmpty()) {
                Lbl(
                    "Clear all",
                    Modifier.clip(RoundedCornerShape(Rad.segment)).clickable { NotifService.clearAll() }.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = pal.acc, weight = FontWeight.SemiBold,
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().landing(1)) {
            if (!st.notifAccess) {
                Piece(Modifier.fillMaxWidth(), RoundedCornerShape(Rad.normal + 1.dp), edge = true) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Title("See notifications and music here", size = 15.sp)
                        Lbl("Allow notification access for Ninomae Home.", size = 13.sp, color = pal.sub, maxLines = 2)
                        AccentButton("Allow") { Actions.openListenerSettings(ctx) }
                    }
                }
            } else if (groups.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Glyph("notifications_off", size = 34.dp, color = Color.White.copy(alpha = 0.6f))
                    Spacer(Modifier.height(8.dp))
                    Lbl("All caught up", color = Color.White.copy(alpha = 0.75f))
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(groups, key = { it.first().pkg }) { g -> NoteGroup(st, ui, g) }
                }
            }
        }

        // ---------- controls, under the thumb ----------
        val media by Media.now.collectAsState()
        val big = 22.dp
        val j = Rad.joined
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            media?.let { DeckPlayer(it, corners(big, big, j, j), Modifier.landing(2)) }
            Piece(
                Modifier.fillMaxWidth().landing(3),
                if (media == null) corners(big, big, j, j) else RoundedCornerShape(j),
                edge = true,
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThickBar(Status.brightness, "light_mode", "Brightness", st.canWrite) { Actions.setBrightness(ctx, it) }
                    ThickBar(Status.volume, if (Status.volume < 0.01f) "volume_off" else "volume_up", "Media", true) { Actions.setVolume(ctx, it) }
                    if (!st.canWrite) {
                        Lbl(
                            "Allow changing system settings to use brightness",
                            Modifier.clickable { Actions.openWriteSettings(ctx) }.padding(start = 4.dp),
                            size = 12.sp, color = pal.acc,
                        )
                    }
                }
            }
            Piece(Modifier.fillMaxWidth().landing(4), RoundedCornerShape(j), edge = true) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Toggle("wifi", "Wi-Fi", Status.wifi) { Actions.wifi(ctx) }
                        Toggle("bluetooth", "Bluetooth", Status.bt) { Actions.bluetooth(ctx) }
                        Toggle("do_not_disturb_on", "Silent", Status.dnd) { Actions.toggleDnd(ctx) }
                        Toggle("screen_rotation", "Rotate", Status.rotate) { Actions.toggleRotate(ctx) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Toggle("flashlight_on", "Torch", Status.torch) { Actions.toggleTorch(ctx) }
                        Toggle("location_on", "Location", Status.location) { Actions.location(ctx) }
                        Toggle("screenshot_region", "Capture", false) {
                            ui.close(); Actions.global(ctx, AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, 550)
                        }
                        Toggle("lock", "Lock", false) {
                            ui.close(); Actions.global(ctx, AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, 250)
                        }
                    }
                }
            }
            DeckFooter(ui, corners(j, j, big, big), Modifier.landing(5))
        }
    }
}

@Composable
private fun DeckPlayer(m: MediaNow, shape: Shape, modifier: Modifier) {
    val pal = LocalPal.current
    var pos by remember { mutableLongStateOf(m.positionNow()) }
    LaunchedEffect(m) {
        while (true) {
            pos = m.positionNow()
            if (!m.playing) break
            delay(1000)
        }
    }
    val tint = m.tint?.let { mix(pal.l3, it, 0.26f) } ?: pal.l3
    Piece(modifier.fillMaxWidth().height(84.dp), shape, tint, edge = true) {
        Row(Modifier.fillMaxSize().padding(start = 14.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(m.art, 56.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Title(m.title, size = 16.sp)
                Lbl(
                    listOf(m.artist, if (m.duration > 0) "${mmss(pos)} / ${mmss(m.duration)}" else m.app).filter { it.isNotBlank() }.joinToString("  ·  "),
                    size = 12.sp, color = pal.sub,
                )
            }
            IconButton("skip_previous", 40.dp, pal.fg) { Media.prev() }
            Piece(Modifier.size(50.dp, 42.dp), RoundedCornerShape(14.dp), pal.acc, onClick = { Media.toggle() }) {
                Glyph(if (m.playing) "pause" else "play_arrow", Modifier.align(Alignment.Center), size = 24.dp, color = pal.ink, fill = true)
            }
            IconButton("skip_next", 40.dp, pal.fg) { Media.next() }
        }
        if (m.duration > 0) {
            Box(
                Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp)
                    .fillMaxWidth((pos.toFloat() / m.duration).coerceIn(0f, 1f)).height(3.dp)
                    .clip(CircleShape).background(pal.acc),
            )
        }
    }
}

/** OSD-style thick bar: filled to the value, icon and name inside, number at the end. Drag or tap anywhere. */
@Composable
private fun ThickBar(value: Float, glyph: String, label: String, enabled: Boolean, onChange: (Float) -> Unit) {
    val pal = LocalPal.current
    val v = value.coerceIn(0f, 1f)
    val shown by animateFloatAsState(v, spring(stiffness = 900f), label = "bar")
    Box(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(pal.l5)
            .then(if (enabled) Modifier.slideToSet(onChange) else Modifier),
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(shown).clip(RoundedCornerShape(14.dp)).background(if (enabled) pal.acc else pal.dim))
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Glyph(glyph, size = 20.dp, color = if (shown > 0.1f) pal.ink else pal.fg, fill = true)
            Spacer(Modifier.width(8.dp))
            Lbl(label, Modifier.weight(1f), size = 13.sp, color = if (shown > 0.42f) pal.ink else pal.fg, weight = FontWeight.SemiBold)
            Num("${(v * 100).roundToInt()}", size = 13.sp, color = if (shown > 0.94f) pal.ink else pal.sub)
        }
    }
}

private fun Modifier.slideToSet(onChange: (Float) -> Unit): Modifier = composed {
    val change by rememberUpdatedState(onChange)
    slideToSetRaw { change(it) }
}

private fun Modifier.slideToSetRaw(onChange: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent()
            val c = e.changes.firstOrNull() ?: continue
            if (c.pressed) {
                onChange((c.position.x / size.width).coerceIn(0f, 1f))
                c.consume()
            }
        }
    }
}

@Composable
private fun RowScope.Toggle(glyph: String, name: String, on: Boolean, onClick: () -> Unit) {
    val pal = LocalPal.current
    val view = LocalView.current
    val bg by androidx.compose.animation.animateColorAsState(if (on) pal.acc else pal.l5, label = "tg")
    Box(
        Modifier
            .weight(1f)
            .height(72.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .pressScale()
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Glyph(glyph, size = 22.dp, color = if (on) pal.ink else pal.fg, fill = on)
            Spacer(Modifier.height(4.dp))
            Lbl(name, size = 11.sp, color = if (on) pal.ink else pal.fg, weight = if (on) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
private fun DeckFooter(ui: Ui, shape: Shape, modifier: Modifier) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val now = rememberNow()
    Piece(modifier.fillMaxWidth().height(60.dp), shape, edge = true) {
        Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Title("${clockText(now)}  ·  ${now.format(DateTimeFormatter.ofPattern("EEE d MMM"))}", size = 16.sp)
                Lbl(
                    "${Status.battery}%" + if (Status.charging) "  ·  charging" else "",
                    size = 12.sp, color = pal.sub,
                )
            }
            FooterButton("tune") { ui.layer = Layer.Settings }
            FooterButton("settings") { ui.close(); Actions.androidSettings(ctx) }
            FooterButton("power_settings_new") { ui.close(); Actions.global(ctx, AccessibilityService.GLOBAL_ACTION_POWER_DIALOG) }
        }
    }
}

@Composable
private fun FooterButton(glyph: String, onClick: () -> Unit) {
    val pal = LocalPal.current
    Box(
        Modifier.padding(start = 6.dp).size(42.dp).clip(RoundedCornerShape(13.dp)).background(pal.l5).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Glyph(glyph, size = 20.dp) }
}

/** One app's notifications: the newest on top, the rest peeking behind until opened. Swipe sideways to clear. */
@Composable
private fun NoteGroup(st: HomeState, ui: Ui, group: List<Note>) {
    var open by remember(group.first().pkg) { mutableStateOf(false) }
    val many = group.size > 1
    SwipeAway(onGone = { group.forEach { NotifService.dismiss(it) } }) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box {
                if (many && !open) {
                    val pal = LocalPal.current
                    Box(Modifier.matchParentSize().padding(horizontal = 14.dp).offset { IntOffset(0, 14) }.clip(RoundedCornerShape(20.dp)).background(pal.l4.copy(alpha = 0.6f)))
                    Box(Modifier.matchParentSize().padding(horizontal = 7.dp).offset { IntOffset(0, 7) }.clip(RoundedCornerShape(20.dp)).background(pal.l4.copy(alpha = 0.85f)))
                }
                NoteCard(st, ui, group.first(), count = if (many && !open) group.size else 0, open = open) { open = !open }
            }
            if (many && open) group.drop(1).forEach { NoteCard(st, ui, it, 0, false, null) }
            if (many && !open) Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun NoteCard(st: HomeState, ui: Ui, n: Note, count: Int, open: Boolean, onExpand: (() -> Unit)?) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val app = st.byPkg[n.pkg]
    Piece(
        Modifier.fillMaxWidth(),
        RoundedCornerShape(20.dp),
        pal.l3,
        edge = true,
        onClick = { ui.close(); NotifService.open(ctx, n) },
    ) {
        Row(Modifier.padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (app == null && n.icon != null) androidx.compose.foundation.Image(n.icon, null, Modifier.size(36.dp)) else AppIcon(app, 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Lbl(
                    "${n.app}  ·  ${ago(n.time)}" + if (count > 1) "  ·  $count new" else "",
                    size = 12.sp, color = pal.sub,
                )
                Lbl(n.title, weight = FontWeight.SemiBold)
                if (n.text.isNotBlank()) Lbl(n.text, size = 13.sp, color = pal.sub, maxLines = 2)
                if (n.actions.isNotEmpty() && onExpand != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        n.actions.take(3).forEach { (title, intent) ->
                            PlainButton(title, height = 30.dp) { NotifService.sendPending(ctx, intent) }
                        }
                    }
                }
            }
            if (onExpand != null && (count > 1 || open)) {
                Box(
                    Modifier.size(30.dp).clip(RoundedCornerShape(Rad.segment)).background(pal.l5).clickable(onClick = onExpand),
                    contentAlignment = Alignment.Center,
                ) { Glyph(if (open) "expand_less" else "expand_more", size = 18.dp) }
            }
        }
    }
}

/** Swipe sideways to throw it away; let go early and it springs back. */
@Composable
fun SwipeAway(onGone: () -> Unit, content: @Composable () -> Unit) {
    val gone by rememberUpdatedState(onGone)
    val x = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var w by remember { mutableStateOf(1f) }
    Box(
        Modifier
            .graphicsLayer {
                translationX = x.value
                alpha = 1f - (abs(x.value) / w).coerceIn(0f, 1f) * 0.8f
            }
            .pointerInput(Unit) {
                w = size.width.toFloat()
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (abs(x.value) > w * 0.35f) {
                                x.animateTo(if (x.value > 0) w * 1.2f else -w * 1.2f, spring(stiffness = 500f))
                                gone()
                            } else {
                                x.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 600f))
                            }
                        }
                    },
                ) { c, dx ->
                    c.consume()
                    scope.launch { x.snapTo(x.value + dx) }
                }
            },
    ) { content() }
}

/** Squeeze a little while pressed. */
fun Modifier.pressScale(amount: Float = 0.94f): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (pressed) amount else 1f, spring(dampingRatio = 0.6f, stiffness = 900f), label = "press")
    this
        .graphicsLayer { scaleX = s; scaleY = s }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    pressed = e.changes.any { it.pressed }
                }
            }
        }
}
