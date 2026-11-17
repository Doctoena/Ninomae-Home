package dev.ninomae.home

import android.os.SystemClock
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

enum class Layer { None, Sidebar, Drawer, Menu, PairMenu, PairMaker, Settings }

/** Which overlay is open and what it's about. */
class Ui {
    var layer by mutableStateOf(Layer.None)
    var menuApp by mutableStateOf<AppEntry?>(null)
    var menuFromDock by mutableStateOf(false)
    var anchor by mutableStateOf(Rect.Zero)
    var above by mutableStateOf(true)
    var pair by mutableStateOf<AppPair?>(null)
    var seedLeft by mutableStateOf<String?>(null)
    /** Bumped on each open so the pair maker starts fresh. */
    var openCount by mutableStateOf(0)

    fun close() {
        layer = Layer.None
    }

    fun toggle(l: Layer) {
        layer = if (layer == l) Layer.None else l
    }

    fun showMenu(app: AppEntry, rect: Rect, above: Boolean, fromDock: Boolean) {
        menuApp = app
        anchor = rect
        this.above = above
        menuFromDock = fromDock
        layer = Layer.Menu
    }

    fun showPairMenu(p: AppPair, rect: Rect, above: Boolean) {
        pair = p
        anchor = rect
        this.above = above
        layer = Layer.PairMenu
    }

    fun newPair(leftKey: String? = null) {
        pair = null
        seedLeft = leftKey
        openCount++
        layer = Layer.PairMaker
    }

    fun editPair(p: AppPair) {
        pair = p
        seedLeft = null
        openCount++
        layer = Layer.PairMaker
    }
}

@Composable
fun Home(st: HomeState, ui: Ui) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 600.dp
        val screenH = maxHeight
        val screenW = maxWidth
        val open = ui.layer != Layer.None
        val floating = ui.layer == Layer.Menu || ui.layer == Layer.PairMenu
        val dim by animateFloatAsState(if (open && !floating) 0.38f else 0f, label = "dim")

        // ---------- desktop ----------
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            Bar(st, ui, compact, Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(8.dp))
            if (st.widgetSide != 0 || compact) {
                Widgets(
                    st, ui,
                    Modifier
                        .align(if (st.widgetSide == 1 || compact) Alignment.TopStart else Alignment.TopEnd)
                        .padding(start = 8.dp, end = 8.dp, top = 52.dp)
                        .then(if (compact) Modifier.fillMaxWidth() else Modifier.width(330.dp)),
                )
            }
            Dock(st, ui, compact, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
        }

        // ---------- dim + tap outside to close ----------
        if (open) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dim))
                    .pointerInput(Unit) { detectTapGestures { ui.close() } },
            )
        }

        // ---------- overlays ----------
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            AnimatedVisibility(
                ui.layer == Layer.Sidebar,
                Modifier.align(Alignment.TopEnd).padding(top = 52.dp, end = 8.dp, start = 8.dp),
                enter = fadeIn() + slideInVertically { -it / 10 } + scaleIn(initialScale = 0.97f, transformOrigin = TransformOrigin(1f, 0f)),
                exit = fadeOut() + slideOutVertically { -it / 14 },
            ) {
                Sidebar(st, ui, Modifier.then(if (compact) Modifier.fillMaxWidth() else Modifier.width(340.dp)).heightIn(max = screenH - 80.dp))
            }
            AnimatedVisibility(
                ui.layer == Layer.Drawer,
                Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp, start = 8.dp, end = 8.dp),
                enter = fadeIn() + slideInVertically { it / 12 } + scaleIn(initialScale = 0.97f, transformOrigin = TransformOrigin(0.5f, 1f)),
                exit = fadeOut() + slideOutVertically { it / 16 },
            ) {
                Drawer(st, ui, compact, Modifier.width(if (compact) screenW else minOf(screenW - 16.dp, 620.dp)).heightIn(max = screenH - 150.dp))
            }
            AnimatedVisibility(
                ui.layer == Layer.PairMaker,
                Modifier.align(Alignment.Center).padding(horizontal = 8.dp),
                enter = fadeIn() + scaleIn(initialScale = 0.95f),
                exit = fadeOut() + scaleOut(targetScale = 0.97f),
            ) {
                PairMaker(st, ui, compact, Modifier.width(minOf(screenW - 16.dp, 560.dp)).heightIn(max = screenH - 120.dp))
            }
            AnimatedVisibility(
                ui.layer == Layer.Settings,
                Modifier.align(Alignment.Center).padding(horizontal = 8.dp),
                enter = fadeIn() + scaleIn(initialScale = 0.95f),
                exit = fadeOut() + scaleOut(targetScale = 0.97f),
            ) {
                SettingsPanel(st, ui, Modifier.width(minOf(screenW - 16.dp, 540.dp)).heightIn(max = screenH - 120.dp))
            }
        }

        // ---------- menus pinned to what was held ----------
        if (ui.layer == Layer.Menu) {
            val gap = if (ui.menuFromDock) 4.dp else 8.dp
            Anchored(ui.anchor, ui.above, gap.value) { AppMenu(st, ui) }
        }
        if (ui.layer == Layer.PairMenu) {
            Anchored(ui.anchor, ui.above, 8f) { PairMenu(st, ui) }
        }
    }
}

/** Places the menu above or below [anchor] (root coordinates), kept on screen. */
@Composable
fun Anchored(anchor: Rect, above: Boolean, gapDp: Float, content: @Composable () -> Unit) {
    Layout(content, Modifier.fillMaxSize()) { measurables, c ->
        val p = measurables.first().measure(Constraints(maxWidth = c.maxWidth, maxHeight = c.maxHeight))
        val m = 8.dp.roundToPx()
        val gap = (gapDp * density).roundToInt()
        layout(c.maxWidth, c.maxHeight) {
            val x = anchor.left.roundToInt().coerceIn(m, (c.maxWidth - p.width - m).coerceAtLeast(m))
            val y = if (above) anchor.top.roundToInt() - gap - p.height else anchor.bottom.roundToInt() + gap
            p.place(x, y.coerceIn(m, (c.maxHeight - p.height - m).coerceAtLeast(m)))
        }
    }
}

// ---------- time and text helpers ----------

@Composable
fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000 - System.currentTimeMillis() % 60_000 + 50)
        }
    }
    return now
}

@Composable
fun clockText(now: LocalDateTime): String {
    val ctx = LocalContext.current
    val fmt = if (DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm"
    return now.format(DateTimeFormatter.ofPattern(fmt))
}

fun greeting(now: LocalDateTime, name: String): String {
    val part = when (now.hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
    return if (name.isBlank()) part else "$part, $name"
}

fun uptime(): String {
    val m = SystemClock.elapsedRealtime() / 60_000
    return if (m < 60) "up ${m}m" else if (m < 60 * 48) "up ${m / 60}h" else "up ${m / 1440}d"
}

fun ago(t: Long): String {
    val m = (System.currentTimeMillis() - t) / 60_000
    return when {
        m < 1 -> "now"
        m < 60 -> "${m}m"
        m < 60 * 24 -> "${m / 60}h"
        else -> "${m / 1440}d"
    }
}

fun mmss(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
