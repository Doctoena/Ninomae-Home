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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

enum class Layer { None, Sidebar, Drawer, Menu, PairMenu, PairMaker, Settings, Widgets, Folder, Rename }

/** Which overlay is open, what's being held or dragged, and where the grid and dock sit. */
class Ui {
    var layer by mutableStateOf(Layer.None)
    var editing by mutableStateOf(false)

    var menuApp by mutableStateOf<AppEntry?>(null)
    var menuFromDock by mutableStateOf(false)
    var menuHomeId by mutableStateOf<String?>(null)
    var menuFolderId by mutableStateOf<String?>(null)
    var anchor by mutableStateOf(Rect.Zero)
    var above by mutableStateOf(true)
    var pair by mutableStateOf<AppPair?>(null)
    var seedLeft by mutableStateOf<String?>(null)
    var openCount by mutableIntStateOf(0)
    var folderId by mutableStateOf<String?>(null)
    var renameKey by mutableStateOf<String?>(null)

    // drag
    var hold by mutableStateOf<Hold?>(null)
    var drag by mutableStateOf<Hold?>(null)
    var dragPos by mutableStateOf(Offset.Zero)

    // geometry, written by the grid and dock
    var screen by mutableIntStateOf(0)
    var page by mutableIntStateOf(0)
    var jumpToPage by mutableStateOf<Int?>(null)
    var gridRect by mutableStateOf(Rect.Zero)
    var cellW by mutableFloatStateOf(0f)
    var cellH by mutableFloatStateOf(0f)
    var cellWdp by mutableFloatStateOf(90f)
    var cellHdp by mutableFloatStateOf(130f)
    var dockRect by mutableStateOf(Rect.Zero)
    var pinRect by mutableStateOf(Rect.Zero)
    var dockSlotPx by mutableFloatStateOf(1f)
    var dockPadPx by mutableFloatStateOf(0f)

    fun close() {
        layer = Layer.None
    }

    fun toggle(l: Layer) {
        layer = if (layer == l) Layer.None else l
    }

    fun beginDrag(at: Offset) {
        drag = hold
        dragPos = at
        layer = Layer.None
    }

    fun showMenu(app: AppEntry, rect: Rect, above: Boolean, fromDock: Boolean, homeId: String? = null, folderId: String? = null) {
        menuApp = app
        anchor = rect
        this.above = above
        menuFromDock = fromDock
        menuHomeId = homeId
        menuFolderId = folderId
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

    fun openFolder(id: String) {
        folderId = id
        layer = Layer.Folder
    }

    fun rename(key: String) {
        renameKey = key
        openCount++
        layer = Layer.Rename
    }
}

@Composable
fun Home(st: HomeState, ui: Ui) {
    BoxWithConstraints(Modifier.fillMaxSize().dragTracker(st, ui)) {
        val compact = maxWidth < 600.dp
        val screenH = maxHeight
        val screenW = maxWidth
        val open = ui.layer != Layer.None
        val floating = ui.layer == Layer.Menu || ui.layer == Layer.PairMenu
        val dim by animateFloatAsState(if (open && !floating) 0.38f else 0f, label = "dim")
        val insets = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .windowInsetsPadding(WindowInsets.navigationBars)

        // ---------- desktop ----------
        Column(insets) {
            Bar(st, ui, compact, Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp))
            HomeGrid(st, ui, compact, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 4.dp))
            Box(Modifier.fillMaxWidth().padding(bottom = 12.dp, top = 2.dp), contentAlignment = Alignment.Center) {
                if (ui.editing) EditBar(ui) else Dock(st, ui, compact, Modifier)
            }
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
        Box(insets) {
            AnimatedVisibility(
                ui.layer == Layer.Sidebar,
                Modifier.align(Alignment.TopEnd).padding(top = 48.dp, end = 8.dp, start = 8.dp),
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
                Drawer(st, ui, compact, Modifier.width(if (compact) screenW else minOf(screenW - 16.dp, 640.dp)).heightIn(max = screenH - 150.dp))
            }
            Centered(ui.layer == Layer.PairMaker) {
                PairMaker(st, ui, compact, Modifier.width(minOf(screenW - 16.dp, 560.dp)).heightIn(max = screenH - 120.dp))
            }
            Centered(ui.layer == Layer.Settings) {
                SettingsPanel(st, ui, Modifier.width(minOf(screenW - 16.dp, 560.dp)).heightIn(max = screenH - 120.dp))
            }
            Centered(ui.layer == Layer.Widgets) {
                WidgetPicker(st, ui, Modifier.width(minOf(screenW - 16.dp, 640.dp)).heightIn(max = screenH - 120.dp))
            }
            Centered(ui.layer == Layer.Folder) {
                FolderPanel(st, ui, Modifier.width(minOf(screenW - 16.dp, 460.dp)).heightIn(max = screenH - 160.dp))
            }
            Centered(ui.layer == Layer.Rename) {
                RenamePanel(st, ui, Modifier.width(minOf(screenW - 16.dp, 380.dp)))
            }
        }

        // ---------- menus pinned to what was held ----------
        if (ui.layer == Layer.Menu) {
            Anchored(ui.anchor, ui.above, if (ui.menuFromDock) 4f else 8f) { AppMenu(st, ui) }
        }
        if (ui.layer == Layer.PairMenu) {
            Anchored(ui.anchor, ui.above, 8f) { PairMenu(st, ui) }
        }

        // ---------- dragging ----------
        if (ui.drag != null) {
            RemoveZone(ui, Modifier.align(Alignment.TopCenter).padding(top = 40.dp))
            Ghost(st, ui)
        }
    }
}

@Composable
private fun BoxScope.Centered(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible,
        Modifier.align(Alignment.Center).padding(horizontal = 8.dp),
        enter = fadeIn() + scaleIn(initialScale = 0.95f),
        exit = fadeOut() + scaleOut(targetScale = 0.97f),
    ) { content() }
}

/** Edit mode swaps the dock for these. */
@Composable
private fun EditBar(ui: Ui) {
    val ctx = LocalContext.current
    val big = Rad.dock
    val j = Rad.joined
    Row(Modifier.height(66.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        EditTool("widgets", "Widgets", corners(big, j, j, big)) { ui.layer = Layer.Widgets }
        EditTool("wallpaper", "Wallpaper", RoundedCornerShape(j)) { Actions.wallpaper(ctx) }
        EditTool("tune", "Settings", RoundedCornerShape(j)) { ui.layer = Layer.Settings }
        EditTool("check", "Done", corners(j, big, big, j), accent = true) { ui.editing = false }
    }
}

@Composable
private fun EditTool(glyph: String, label: String, shape: Shape, accent: Boolean = false, onClick: () -> Unit) {
    val pal = LocalPal.current
    Piece(Modifier.width(92.dp).fillMaxHeight(), shape, if (accent) pal.acc else pal.l3, edge = true, onClick = onClick) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Glyph(glyph, size = 22.dp, color = if (accent) pal.ink else pal.fg)
            Spacer(Modifier.height(3.dp))
            Lbl(label, size = 12.sp, color = if (accent) pal.ink else pal.fg, weight = if (accent) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
private fun RemoveZone(ui: Ui, modifier: Modifier) {
    val pal = LocalPal.current
    val src = ui.drag?.src ?: return
    val text = when {
        src.item != null -> "Remove"
        src.folderId != null -> "Take out of folder"
        src.fromDock -> "Unpin"
        else -> return
    }
    val over = overRemoveZone(ui)
    Piece(
        modifier.padding(top = 8.dp).height(40.dp),
        RoundedCornerShape(Rad.container),
        if (over) pal.acc else pal.l4,
        edge = true,
    ) {
        Row(Modifier.fillMaxHeight().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Glyph(if (src.item != null) "delete" else "remove_circle", size = 19.dp, color = if (over) pal.ink else pal.fg)
            Spacer(Modifier.width(8.dp))
            Lbl(text, color = if (over) pal.ink else pal.fg, weight = FontWeight.SemiBold)
        }
    }
}

/** What's under the finger while dragging. */
@Composable
private fun Ghost(st: HomeState, ui: Ui) {
    val pal = LocalPal.current
    val density = LocalDensity.current
    val src = ui.drag?.src ?: return
    val item = src.item
    val big = item != null && item.resizable
    val wDp = if (big) (ui.cellWdp * item!!.w).dp else 60.dp
    val hDp = if (big) (ui.cellHdp * item!!.h).dp else 60.dp
    val wPx = with(density) { wDp.toPx() }
    val hPx = with(density) { hDp.toPx() }
    Box(
        Modifier
            .offset { IntOffset((ui.dragPos.x - wPx / 2).roundToInt(), (ui.dragPos.y - hPx / 2).roundToInt()) }
            .size(wDp, hDp)
            .scale(1.08f),
        contentAlignment = Alignment.Center,
    ) {
        when {
            big -> Piece(Modifier.fillMaxSize(), RoundedCornerShape(Rad.normal), pal.l3.copy(alpha = 0.85f), edge = true) {
                Glyph("open_with", Modifier.align(Alignment.Center), size = 28.dp, color = pal.acc)
            }
            item?.type == "folder" -> FolderIcon(st, item, 58.dp)
            item?.type == "shortcut" -> AppIcon(st.byPkg[item.pkg], 58.dp)
            else -> AppIcon(st.app(src.appKey ?: item?.app), 58.dp)
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
