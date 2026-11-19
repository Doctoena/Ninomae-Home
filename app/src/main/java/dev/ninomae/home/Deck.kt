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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.foundation.border
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * O2c · The deck: a glass rail of toggles down the right edge, a player tinted by its album art
 * (with brightness and volume inside it), then notifications grouped by app.
 * [p] is how far it's pulled down (0 closed, 1 open); pieces land one after another as it opens.
 */
@Composable
fun Deck(st: HomeState, ui: Ui, p: Float, compact: Boolean, modifier: Modifier) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val notes by NotifService.notes.collectAsState()
    val groups = remember(notes) { notes.groupBy { it.pkg }.values.toList() }
    val media by Media.now.collectAsState()
    val density = LocalDensity.current

    // Each piece trails the one before it, so the deck lands in order from the top.
    fun Modifier.landing(i: Int): Modifier = graphicsLayer {
        val lag = (i * 0.07f).coerceAtMost(0.5f)
        val local = ((p - lag) / (1f - lag)).coerceIn(0f, 1f)
        alpha = local
        translationY = -(1f - local) * with(density) { (60 + i * 18).dp.toPx() }
    }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // ---------- the player, with brightness and volume inside it ----------
            PlayerCard(st, media, compact, Modifier.landing(1))
            if (!st.canWrite) {
                Lbl(
                    "Allow changing system settings to use brightness",
                    Modifier.landing(1).clickable { Actions.openWriteSettings(ctx) }.padding(start = 6.dp),
                    size = 12.sp, color = pal.acc,
                )
            }

            // ---------- notifications, grouped by app ----------
            Row(Modifier.fillMaxWidth().padding(top = 12.dp).height(34.dp).landing(2), verticalAlignment = Alignment.CenterVertically) {
                Title("Notifications", Modifier.weight(1f).padding(start = 6.dp), size = 19.sp, color = Color.White)
                if (notes.isNotEmpty()) {
                    Lbl(
                        "Clear all",
                        Modifier.clip(RoundedCornerShape(Rad.segment)).clickable { NotifService.clearAll() }.padding(horizontal = 8.dp, vertical = 6.dp),
                        size = 13.5.sp, color = pal.acc, weight = FontWeight.SemiBold,
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().landing(3)) {
                when {
                    !st.notifAccess -> Column(
                        Modifier.fillMaxWidth().frosted(st, RoundedCornerShape(22.dp), wash = 0.34f, edge = 0.2f).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Title("See notifications and music here", size = 15.sp)
                        Lbl("Allow notification access for Ninomae Home.", size = 13.sp, color = pal.sub, maxLines = 2)
                        AccentButton("Allow") { Actions.openListenerSettings(ctx) }
                    }
                    groups.isEmpty() -> Row(
                        Modifier.fillMaxWidth().height(56.dp).frosted(st, RoundedCornerShape(22.dp), wash = 0.34f, edge = 0.2f).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Glyph("notifications_off", size = 20.dp, color = pal.acc)
                        Spacer(Modifier.width(12.dp))
                        Lbl("All caught up", color = pal.sub)
                    }
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(groups, key = { it.first().pkg }) { g -> NoteGroup(st, ui, g) }
                    }
                }
            }
        }
        Rail(st, ui, Modifier.landing(0))
    }
}

/** Brightness and volume as thick bars; inside the player when something plays, else on their own glass. */
@Composable
private fun Sliders(st: HomeState, track: Color) {
    val ctx = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { ThickBar(Status.brightness, "light_mode", "Brightness", st.canWrite, track) { Actions.setBrightness(ctx, it) } }
        Box(Modifier.weight(1f)) {
            ThickBar(Status.volume, if (Status.volume < 0.01f) "volume_off" else "volume_up", "Media", true, track) { Actions.setVolume(ctx, it) }
        }
    }
}

/** The player, tinted by a blur of its own album art. Seek by dragging the progress bar. */
@Composable
private fun PlayerCard(st: HomeState, m: MediaNow?, compact: Boolean, modifier: Modifier) {
    val pal = LocalPal.current
    val shape = RoundedCornerShape(26.dp)
    if (m == null) {
        Box(modifier.fillMaxWidth().frosted(st, shape, wash = 0.34f, edge = 0.2f).padding(10.dp)) { Sliders(st, Color.White.copy(alpha = 0.10f)) }
        return
    }
    val art = m.art
    Box(
        modifier.fillMaxWidth().then(
            if (art != null) Modifier.clip(shape) else Modifier.frosted(st, shape, wash = 0.22f, edge = 0.2f).background(pal.acc.copy(alpha = 0.10f)),
        ),
    ) {
        if (art != null) {
            androidx.compose.foundation.Image(
                art, null,
                Modifier.matchParentSize().blur(36.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Rectangle),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            Box(Modifier.matchParentSize().background(pal.l0.copy(alpha = 0.5f)).border(1.dp, Color.White.copy(alpha = 0.2f), shape))
        }
        Column(Modifier.padding(14.dp)) {
            val artSize = if (compact) 96.dp else 200.dp
            Row {
                Cover(art, artSize)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f).height(artSize), verticalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Lbl("NOW PLAYING · ${m.app.uppercase()}", size = 11.sp, color = pal.acc, weight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Lbl(m.title, size = if (compact) 20.sp else 30.sp, weight = FontWeight.Black, family = TitleFont, maxLines = if (compact) 1 else 2)
                        Lbl(m.artist, size = if (compact) 13.sp else 16.sp, color = pal.sub, weight = FontWeight.Medium)
                    }
                    if (!compact) {
                        Column {
                            Progress(m)
                            Spacer(Modifier.height(14.dp))
                            Controls(m)
                        }
                    }
                }
            }
            if (compact) {
                Spacer(Modifier.height(12.dp))
                Progress(m)
                Spacer(Modifier.height(10.dp))
                Controls(m)
            }
            Spacer(Modifier.height(14.dp))
            Sliders(st, Color.White.copy(alpha = 0.12f))
        }
    }
}

/** Where the song is, ticking once a second while it plays (only this bar redraws). Drag to seek. */
@Composable
private fun Progress(m: MediaNow) {
    val pal = LocalPal.current
    if (m.duration <= 0) return
    var seekTo by remember(m) { mutableLongStateOf(-1L) }
    val ticking by rememberPosition(m)
    val pos = if (seekTo >= 0) seekTo else ticking
    val f = (pos.toFloat() / m.duration).coerceIn(0f, 1f)
    Column {
        Box(
            Modifier.fillMaxWidth().height(18.dp).slideToSet { v -> val t = (v * m.duration).toLong(); seekTo = t; Media.seek(t) },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)))
            Box(Modifier.fillMaxWidth(f).height(5.dp).clip(CircleShape).background(pal.acc))
        }
        Row {
            Num(mmss(pos), size = 11.5.sp, color = pal.sub)
            Spacer(Modifier.weight(1f))
            Num(mmss(m.duration), size = 11.5.sp, color = pal.sub)
        }
    }
}

@Composable
private fun Controls(m: MediaNow) {
    val pal = LocalPal.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        IconButton("skip_previous", 44.dp, pal.fg) { Media.prev() }
        Box(
            Modifier.size(72.dp, 52.dp).clip(RoundedCornerShape(18.dp)).background(pal.acc).pressScale().clickable { Media.toggle() },
            contentAlignment = Alignment.Center,
        ) { Glyph(if (m.playing) "pause" else "play_arrow", size = 30.dp, color = pal.ink, fill = true) }
        IconButton("skip_next", 44.dp, pal.fg) { Media.next() }
    }
}

/** OSD-style thick bar: filled to the value, icon and name inside, number at the end. Drag or tap anywhere. */
@Composable
private fun ThickBar(value: Float, glyph: String, label: String, enabled: Boolean, track: Color, onChange: (Float) -> Unit) {
    val pal = LocalPal.current
    val v = value.coerceIn(0f, 1f)
    val shown by animateFloatAsState(v, spring(stiffness = 900f), label = "bar")
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(track)
            .then(if (enabled) Modifier.slideToSet(onChange) else Modifier),
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(shown).clip(shape).background(if (enabled) pal.acc else pal.sub.copy(alpha = 0.4f)))
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Glyph(glyph, size = 20.dp, color = if (shown > 0.1f) pal.ink else pal.fg, fill = true)
            Spacer(Modifier.width(8.dp))
            Lbl(label, Modifier.weight(1f), size = 13.5.sp, color = if (shown > 0.42f) pal.ink else pal.fg, weight = FontWeight.SemiBold)
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

/** The rail: time and battery, the toggles in groups, then launcher settings, Android settings and power. */
@Composable
private fun Rail(st: HomeState, ui: Ui, modifier: Modifier) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val now = rememberNow()
    Column(
        modifier.width(64.dp).fillMaxHeight().frosted(st, RoundedCornerShape(26.dp), wash = 0.4f, edge = 0.2f).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Title(clockText(now), size = 15.sp)
        Num("${Status.battery}%", size = 11.sp, color = pal.sub)
        Spacer(Modifier.height(10.dp))
        val groups = listOf(
            listOf(
                Triple("wifi", Status.wifi) { Actions.wifi(ctx) },
                Triple("bluetooth", Status.bt) { Actions.bluetooth(ctx) },
                Triple("location_on", Status.location) { Actions.location(ctx) },
            ),
            listOf(
                Triple("do_not_disturb_on", Status.dnd) { Actions.toggleDnd(ctx) },
                Triple("screen_rotation", Status.rotate) { Actions.toggleRotate(ctx) },
                Triple("flashlight_on", Status.torch) { Actions.toggleTorch(ctx) },
            ),
            listOf(
                Triple("screenshot_region", false) { ui.close(); Actions.global(ctx, AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, 550) },
                Triple("lock", false) { ui.close(); Actions.global(ctx, AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, 250) },
            ),
        )
        groups.forEachIndexed { i, g ->
            if (i > 0) RailLine()
            g.forEach { (glyph, on, go) -> RailToggle(glyph, on, go) }
        }
        Spacer(Modifier.weight(1f))
        RailLine()
        RailButton("tune") { ui.layer = Layer.Settings }
        RailButton("settings") { ui.close(); Actions.androidSettings(ctx) }
        RailButton("power_settings_new") { ui.close(); Actions.global(ctx, AccessibilityService.GLOBAL_ACTION_POWER_DIALOG) }
    }
}

@Composable
private fun RailLine() = Box(Modifier.padding(vertical = 6.dp).size(28.dp, 1.dp).background(Color.White.copy(alpha = 0.18f)))

@Composable
private fun RailToggle(glyph: String, on: Boolean, onClick: () -> Unit) {
    val pal = LocalPal.current
    val view = LocalView.current
    val bg by androidx.compose.animation.animateColorAsState(if (on) pal.acc else Color.Transparent, label = "tg")
    Box(
        Modifier.padding(vertical = 3.dp).size(48.dp).clip(RoundedCornerShape(16.dp)).background(bg).pressScale()
            .clickable { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); onClick() },
        contentAlignment = Alignment.Center,
    ) { Glyph(glyph, size = 22.dp, color = if (on) pal.ink else pal.fg, fill = on) }
}

@Composable
private fun RailButton(glyph: String, onClick: () -> Unit) {
    val pal = LocalPal.current
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Glyph(glyph, size = 21.dp, color = pal.acc) }
}

/**
 * One app's notifications. A single one is a card; several get a joined header (icon, app, count)
 * with a row each under it. Swipe sideways to clear the lot.
 */
@Composable
private fun NoteGroup(st: HomeState, ui: Ui, group: List<Note>) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val big = 22.dp
    val j = Rad.joined
    SwipeAway(onGone = { group.forEach { NotifService.dismiss(it) } }) {
        if (group.size == 1) {
            NoteRow(st, ui, group.first(), RoundedCornerShape(big), header = true)
        } else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val n = group.first()
            val app = st.byPkg[n.pkg]
            Row(
                Modifier.fillMaxWidth().height(42.dp).frosted(st, corners(big, big, j, j), wash = 0.4f, edge = 0.2f).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (app == null && n.icon != null) androidx.compose.foundation.Image(n.icon, null, Modifier.size(24.dp)) else AppIcon(app, 24.dp)
                Spacer(Modifier.width(10.dp))
                Lbl(n.app, size = 13.sp, weight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.height(18.dp).clip(RoundedCornerShape(9.dp)).background(pal.acc).padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
                    Num("${group.size}", size = 11.sp, color = pal.ink)
                }
                Spacer(Modifier.weight(1f))
                Lbl(ago(n.time), size = 11.5.sp, color = pal.sub)
            }
            group.forEachIndexed { i, note ->
                NoteRow(st, ui, note, corners(j, j, if (i == group.lastIndex) big else j, if (i == group.lastIndex) big else j), header = false)
            }
        }
    }
}

/** A notification on glass: with its app's icon and name ([header]) when it stands alone, else just who and what. */
@Composable
private fun NoteRow(st: HomeState, ui: Ui, n: Note, shape: Shape, header: Boolean) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val app = st.byPkg[n.pkg]
    Row(
        Modifier.fillMaxWidth().frosted(st, shape, wash = 0.34f, edge = 0.2f).clickable { ui.close(); NotifService.open(ctx, n) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (header) {
            if (app == null && n.icon != null) androidx.compose.foundation.Image(n.icon, null, Modifier.size(38.dp)) else AppIcon(app, 38.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            if (header) Row {
                Lbl(n.app, Modifier.weight(1f), size = 11.5.sp, color = pal.sub, weight = FontWeight.SemiBold)
                Lbl(ago(n.time), size = 11.5.sp, color = pal.sub)
            }
            Lbl(n.title, size = 14.sp, weight = FontWeight.SemiBold)
            if (n.text.isNotBlank()) Lbl(n.text, size = 13.5.sp, color = pal.sub, maxLines = 2)
            if (n.actions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    n.actions.take(3).forEach { (title, intent) -> PlainButton(title, height = 30.dp) { NotifService.sendPending(ctx, intent) } }
                }
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
