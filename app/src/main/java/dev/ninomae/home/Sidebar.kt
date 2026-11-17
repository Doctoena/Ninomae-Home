package dev.ninomae.home

import android.accessibilityservice.AccessibilityService
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter

/** One joined stack, like the shell's sidebar: greeting, toggles, strip, player + sliders, notifications. */
@Composable
fun Sidebar(st: HomeState, ui: Ui, modifier: Modifier) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val notes by NotifService.notes.collectAsState()
    val media by Media.now.collectAsState()
    val now = rememberNow()
    val big = Rad.normal
    val j = Rad.joined
    val js = RoundedCornerShape(j)

    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // greeting
        Piece(Modifier.fillMaxWidth().height(70.dp), corners(big, big, j, j), edge = true) {
            Row(Modifier.fillMaxSize().padding(start = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(st, 40.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Title(greeting(now, st.name), size = 16.sp)
                    Lbl("${now.format(DateTimeFormatter.ofPattern("EEE d MMM"))}  ·  ${uptime()}", size = 12.sp, color = pal.sub)
                }
                IconButton("tune") { ui.layer = Layer.Settings }
                IconButton("settings") { ui.close(); Actions.androidSettings(ctx) }
                IconButton("power_settings_new") { ui.close(); Actions.global(ctx, AccessibilityService.GLOBAL_ACTION_POWER_DIALOG) }
            }
        }

        // toggles
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Tile("wifi", "Wi-Fi", if (Status.wifi) "Connected" else "Off", Status.wifi, js, Modifier.weight(1f)) { Actions.wifi(ctx) }
            Tile("bluetooth", "Bluetooth", if (Status.bt) "On" else "Off", Status.bt, js, Modifier.weight(1f)) { Actions.bluetooth(ctx) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Tile("do_not_disturb_on", "Do not disturb", if (Status.dnd) "On" else "Off", Status.dnd, js, Modifier.weight(1f)) { Actions.toggleDnd(ctx) }
            Tile("screen_rotation", "Auto-rotate", if (Status.rotate) "On" else "Off", Status.rotate, js, Modifier.weight(1f)) { Actions.toggleRotate(ctx) }
        }

        // strip
        Piece(Modifier.fillMaxWidth().height(46.dp), js, edge = true) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                StripButton("flashlight_on", Status.torch) { Actions.toggleTorch(ctx) }
                Hair()
                StripButton("screenshot_region", false) { ui.close(); Actions.global(ctx, AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, 450) }
                Hair()
                StripButton("lock", false) { ui.close(); Actions.global(ctx, AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, 200) }
                Hair()
                StripButton("wallpaper", false) { ui.close(); Actions.wallpaper(ctx) }
                Hair()
                StripButton("expand_more", false) { ui.close(); Actions.global(ctx, AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS) }
            }
        }

        // player + sliders
        val m = media
        val tint = m?.tint?.let { mix(pal.l3, it, 0.22f) } ?: pal.l3
        Piece(Modifier.fillMaxWidth(), js, tint, edge = true) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (m != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Cover(m.art, 54.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Title(m.title, size = 16.sp)
                            Lbl(listOf(m.artist, m.app).filter { it.isNotBlank() }.joinToString("  ·  "), size = 12.sp, color = pal.sub)
                        }
                        IconButton("skip_previous", 30.dp, pal.fg) { Media.prev() }
                        Piece(Modifier.width(32.dp).height(32.dp), RoundedCornerShape(Rad.segment), pal.acc, onClick = { Media.toggle() }) {
                            Glyph(if (m.playing) "pause" else "play_arrow", Modifier.align(Alignment.Center), size = 20.dp, color = pal.ink, fill = true)
                        }
                        IconButton("skip_next", 30.dp, pal.fg) { Media.next() }
                    }
                    Spacer(Modifier.height(2.dp))
                }
                SliderRow("volume_up", Status.volume, true) { Actions.setVolume(ctx, it) }
                SliderRow("brightness_6", Status.brightness, st.canWrite) { Actions.setBrightness(ctx, it) }
                if (!st.canWrite) {
                    Lbl(
                        "Allow changing system settings to use brightness",
                        Modifier.clickable { Actions.openWriteSettings(ctx) },
                        size = 12.sp,
                        color = pal.acc,
                    )
                }
            }
        }

        // notifications
        if (!st.notifAccess) {
            Piece(Modifier.fillMaxWidth(), corners(j, j, big, big), edge = true) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Title("Notifications and the player", size = 14.sp)
                    Lbl("Allow notification access to see them here.", size = 13.sp, color = pal.sub, maxLines = 2)
                    AccentButton("Allow") { Actions.openListenerSettings(ctx) }
                }
            }
        } else {
            Piece(Modifier.fillMaxWidth().height(38.dp), js, edge = true) {
                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Title("Notifications", Modifier.weight(1f), size = 14.sp)
                    if (notes.isNotEmpty()) {
                        Num("${notes.size}", size = 13.sp, color = pal.sub)
                        Spacer(Modifier.width(12.dp))
                        Lbl("Clear", Modifier.clickable { NotifService.clearAll() }, size = 13.sp, color = pal.acc, weight = FontWeight.SemiBold)
                    }
                }
            }
            if (notes.isEmpty()) {
                Piece(Modifier.fillMaxWidth().height(64.dp), corners(j, j, big, big), edge = true) {
                    Lbl("All caught up", Modifier.align(Alignment.Center), color = pal.sub, align = TextAlign.Center)
                }
            }
            val shown = notes.take(10)
            shown.forEachIndexed { i, n ->
                val last = i == shown.lastIndex
                NoteCard(st, n, featured = i == 0, shape = corners(j, j, if (last) big else j, if (last) big else j)) { ui.close() }
            }
        }
    }
}

@Composable
private fun Tile(glyph: String, name: String, state: String, on: Boolean, shape: Shape, modifier: Modifier, onClick: () -> Unit) {
    val pal = LocalPal.current
    Piece(modifier.height(58.dp), shape, edge = true, onClick = onClick) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SqIcon(glyph, on)
            Spacer(Modifier.width(10.dp))
            Column {
                Lbl(name, size = 13.sp, color = if (on) pal.acc else pal.fg, weight = FontWeight.SemiBold)
                Lbl(state, size = 12.sp, color = pal.sub)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StripButton(glyph: String, on: Boolean, onClick: () -> Unit) {
    val pal = LocalPal.current
    Box(Modifier.weight(1f).fillMaxHeight().clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Glyph(glyph, size = 20.dp, color = if (on) pal.acc else pal.fg, fill = on)
    }
}

@Composable
private fun SliderRow(glyph: String, value: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    val pal = LocalPal.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Glyph(glyph, size = 19.dp)
        Spacer(Modifier.width(10.dp))
        SlimSlider(value, Modifier.weight(1f), enabled, onChange)
        Spacer(Modifier.width(10.dp))
        Num("${(value * 100).toInt()}", Modifier.width(26.dp), size = 12.sp, color = pal.sub)
    }
}

@Composable
private fun NoteCard(st: HomeState, n: Note, featured: Boolean, shape: Shape, onOpen: () -> Unit) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val app = st.byPkg[n.pkg]
    val bg = if (featured && app != null) mix(pal.l3, app.tint, 0.16f) else pal.l3
    Piece(Modifier.fillMaxWidth(), shape, bg, edge = true, onClick = { onOpen(); NotifService.open(ctx, n) }) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)) {
            AppIcon(app, 34.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Lbl("${n.app}  ·  ${ago(n.time)}", size = 12.sp, color = pal.sub)
                Lbl(n.title, size = 14.sp, weight = FontWeight.SemiBold)
                if (n.text.isNotBlank()) Lbl(n.text, size = 13.sp, color = pal.sub, maxLines = if (featured) 3 else 1)
                if (featured && n.actions.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        n.actions.take(3).forEach { (title, intent) ->
                            PlainButton(title, height = 28.dp) { NotifService.sendPending(ctx, intent) }
                        }
                    }
                }
            }
            IconButton("close", 30.dp, pal.dim) { NotifService.dismiss(n) }
        }
    }
}
