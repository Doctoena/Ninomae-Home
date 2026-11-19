package dev.ninomae.home

import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import android.os.SystemClock
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsTopHeight
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
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

enum class Layer { None, Deck, Apps, Menu, PairMenu, PairMaker, Settings, Widgets, Folder, Rename }

/** Which overlay is open, what's being held or dragged, and where the grid and dock sit. */
class Ui {
    var layer by mutableStateOf(Layer.None)
    var editing by mutableStateOf(false)
    /** The home item picked in edit mode: it gets handles, and the bottom bar becomes its card. */
    var selected by mutableStateOf<String?>(null)
    /** The widget picker was opened from the widgets page: what's picked goes there. */
    var pickForFeed by mutableStateOf(false)

    /** How far the control deck is pulled down and the app sheet raised (0..1); they follow the finger. */
    var deck by mutableFloatStateOf(0f)
    var sheet by mutableFloatStateOf(0f)
    /** How far settings has faded in (0..1). */
    var settings by mutableFloatStateOf(0f)
    var gesturing by mutableStateOf(false)
    var screenHpx by mutableFloatStateOf(2000f)
    var screenWdp by mutableFloatStateOf(400f)
    /** Set by the activity: ask Android for permissions, and bumped when the answer comes back. */
    var askPermissions: (Array<String>) -> Unit = {}
    var permissionTick by mutableIntStateOf(0)
    var backup: () -> Unit = {}
    var pickWallpaper: () -> Unit = {}
    var restore: () -> Unit = {}

    /** Which widget each stack is showing (for taking one out). */
    val stackPage = androidx.compose.runtime.mutableStateMapOf<String, Int>()

    /** Let go of the deck: a flick or past a third opens it, otherwise it slides back. */
    fun settleDeck(velocityY: Float) {
        val open = velocityY > 1400f || (velocityY > -1400f && deck > 0.33f)
        layer = if (open) Layer.Deck else if (layer == Layer.Deck) Layer.None else layer
        gesturing = false
    }

    fun settleSheet(velocityY: Float) {
        val open = velocityY < -1400f || (velocityY < 1400f && sheet > 0.33f)
        layer = if (open) Layer.Apps else if (layer == Layer.Apps) Layer.None else layer
        gesturing = false
    }

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
        menuParent = Layer.None
    }

    /** What a hold menu was opened over (the drawer or a folder), so it can close back to it. */
    var menuParent by mutableStateOf(Layer.None)
    private val menuOpen get() = layer == Layer.Menu || layer == Layer.PairMenu
    /** The drawer is up: on its own, or under a menu opened from it. */
    val inApps get() = layer == Layer.Apps || (menuOpen && menuParent == Layer.Apps)
    /** A folder is open: on its own, or under a menu opened from it. */
    val inFolder get() = layer == Layer.Folder || (menuOpen && menuParent == Layer.Folder)

    /** Back or a tap outside: a menu over the drawer or a folder goes back to it, anything else closes. */
    fun back() {
        if (menuOpen && menuParent != Layer.None) {
            layer = menuParent
            menuParent = Layer.None
        } else close()
    }

    fun toggle(l: Layer) {
        layer = if (layer == l) Layer.None else l
    }

    fun beginDrag(at: Offset) {
        drag = hold
        dragPos = at
        layer = Layer.None
        menuParent = Layer.None
    }

    fun showMenu(app: AppEntry, rect: Rect, above: Boolean, fromDock: Boolean, homeId: String? = null, folderId: String? = null) {
        menuApp = app
        anchor = rect
        this.above = above
        menuFromDock = fromDock
        menuHomeId = homeId
        menuFolderId = folderId
        menuParent = if (layer == Layer.Apps || layer == Layer.Folder) layer else Layer.None
        layer = Layer.Menu
    }

    fun showPairMenu(p: AppPair, rect: Rect, above: Boolean) {
        pair = p
        anchor = rect
        this.above = above
        menuParent = if (layer == Layer.Apps || layer == Layer.Folder) layer else Layer.None
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
        val density = LocalDensity.current
        val screenHpx = with(density) { screenH.toPx() }
        SideEffect { ui.screenHpx = screenHpx; ui.screenWdp = screenW.value }
        val ctxHome = LocalContext.current
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(10 * 60_000L)
                Glance.refresh(ctxHome, st)
            }
        }

        // The deck and sheet follow the finger; otherwise they spring to wherever the layer says.
        LaunchedEffect(ui.layer, ui.gesturing) {
            if (!ui.gesturing) animate(ui.deck, if (ui.layer == Layer.Deck) 1f else 0f, animationSpec = spring(0.84f, 380f)) { v, _ -> ui.deck = v }
        }
        LaunchedEffect(ui.layer) {
            animate(ui.settings, if (ui.layer == Layer.Settings) 1f else 0f, animationSpec = spring(1f, 380f)) { v, _ -> ui.settings = v }
        }
        LaunchedEffect(ui.layer, ui.gesturing) {
            if (!ui.gesturing) animate(ui.sheet, if (ui.inApps) 1f else 0f, animationSpec = spring(1f, 380f)) { v, _ -> ui.sheet = v }
        }

        // Ninomae's bar or Samsung's status bar, chosen per screen in settings.
        val ownBar = if (compact) st.barCover else st.barInner
        val activity = LocalContext.current as? MainActivity
        LaunchedEffect(ownBar, compact) { activity?.applyStatusBar() }

        val open = ui.layer != Layer.None
        val floating = ui.layer == Layer.Menu || ui.layer == Layer.PairMenu
        val panel = open && !floating && ui.layer != Layer.Deck && ui.layer != Layer.Apps && ui.layer != Layer.Settings && ui.layer != Layer.Widgets
        val panelDim by animateFloatAsState(if (ui.inFolder) 0.22f else if (panel) 0.4f else if (floating) 0.18f else 0f, label = "dim")
        val dim = maxOf(panelDim, ui.deck * 0.72f, ui.sheet * 0.12f)
        // With the frosted wallpaper, the sheet hides the desktop outright, so it only needs blurring under the deck.
        val behind = maxOf(ui.deck, if (st.glass == null) ui.sheet else 0f)
        val insets = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .windowInsetsPadding(WindowInsets.navigationBars)

        // ---------- desktop (eases back a touch while something is over it) ----------
        Column(
            insets
                .graphicsLayer { val k = 1f - 0.05f * behind - 0.06f * maxOf(ui.sheet, ui.settings); scaleX = k; scaleY = k; alpha = (1f - 0.35f * ui.deck - (if (st.glass == null) 0.8f else 1f) * maxOf(ui.sheet, ui.settings)).coerceAtLeast(0f) }
                .then(if (behind > 0.01f) Modifier.blur((18 * behind).dp, BlurredEdgeTreatment.Unbounded) else Modifier),
        ) {
            if (ownBar) Bar(st, ui, compact, Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp))
            else Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars).padding(bottom = 4.dp))
            HomeGrid(st, ui, compact, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 4.dp))
            Box(Modifier.fillMaxWidth().padding(bottom = 12.dp, top = 8.dp), contentAlignment = Alignment.Center) {
                if (ui.editing) EditBar(st, ui) else Dock(st, ui, compact, Modifier)
            }
        }

        // ---------- dim + tap outside to close ----------
        if (open || dim > 0.001f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dim))
                    .then(if (open) Modifier.pointerInput(Unit) { detectTapGestures { ui.back() } } else Modifier),
            )
        }

        // ---------- control deck: pulled down from the top ----------
        if (ui.deck > 0.001f || ui.layer == Layer.Deck) {
            Box(insets) {
                Deck(
                    st, ui, ui.deck, compact,
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.statusBars).only(WindowInsetsSides.Top))
                        .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 10.dp)
                        .deckDrag(ui),
                )
            }
        }

        // ---------- all apps: a sheet from the bottom ----------
        // ---------- all apps: full screen, on frosted glass ----------
        if (ui.sheet > 0.001f || ui.inApps) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))) {
                Sheet(st, ui, compact, Modifier.fillMaxSize())
            }
        }

        // ---------- settings: full screen, on the same glass ----------
        if (ui.settings > 0.001f || ui.layer == Layer.Settings) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))) {
                SettingsPanel(st, ui, compact, Modifier.fillMaxSize())
            }
        }

        // ---------- widget picker: full screen, on the same glass ----------
        val widgetsP by animateFloatAsState(if (ui.layer == Layer.Widgets) 1f else 0f, spring(1f, 380f), label = "widgets")
        if (widgetsP > 0.001f) WidgetPicker(st, ui, { widgetsP }, Modifier.fillMaxSize())

        // ---------- panels ----------
        Box(insets) {
            Centered(ui.layer == Layer.PairMaker) {
                PairMaker(st, ui, compact, Modifier.width(minOf(screenW - 16.dp, 560.dp)).heightIn(max = screenH - 120.dp))
            }
            Centered(ui.layer == Layer.Rename) {
                RenamePanel(st, ui, Modifier.width(minOf(screenW - 16.dp, 380.dp)))
            }
        }

        // ---------- an open folder: glass grown out of its icon ----------
        val folderP by animateFloatAsState(if (ui.inFolder) 1f else 0f, spring(0.82f, 520f), label = "folder")
        if (folderP > 0.001f) FolderPopup(st, ui, { folderP }, Modifier.fillMaxSize())

        // ---------- menus, grown out of what was held ----------
        if (ui.layer == Layer.Menu) {
            Anchored(ui.anchor, ui.above, if (ui.menuFromDock) 4f else 10f) { GrowIn(ui.above) { AppMenu(st, ui, compact) } }
        }
        if (ui.layer == Layer.PairMenu) {
            Anchored(ui.anchor, ui.above, 10f) { GrowIn(ui.above) { PairMenu(st, ui) } }
        }

        // ---------- dragging ----------
        if (ui.drag != null) {
            RemoveZone(ui, Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).padding(top = if (ownBar) 40.dp else 4.dp))
            Ghost(st, ui)
        }
    }
}

/** Pops in from the side it's attached to: a quick spring in scale and fade. */
@Composable
fun GrowIn(fromBottom: Boolean, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 520f)) }
    Box(
        Modifier.graphicsLayer {
            alpha = a.value.coerceIn(0f, 1f)
            val k = 0.86f + 0.14f * a.value
            scaleX = k
            scaleY = k
            transformOrigin = TransformOrigin(0.2f, if (fromBottom) 1f else 0f)
        },
    ) { content() }
}

/** Drag the open deck up to put it away. */
private fun Modifier.deckDrag(ui: Ui): Modifier = pointerInput(Unit) {
    val vt = VelocityTracker()
    detectVerticalDragGestures(
        onDragStart = { vt.resetTracking(); ui.gesturing = true },
        onDragEnd = { ui.settleDeck(vt.calculateVelocity().y) },
        onDragCancel = { ui.settleDeck(0f) },
    ) { c, dy ->
        vt.addPosition(c.uptimeMillis, c.position)
        ui.deck = (ui.deck + dy / (ui.screenHpx * 0.5f)).coerceIn(0f, 1f)
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
/**
 * Edit mode's bottom bar (E2b). Nothing picked: a glass capsule (widgets, wallpaper, settings) and a
 * round ✓, with a hint above. Something picked: the bar becomes that item's card and actions.
 * It keeps the dock's height and grows upward over the grid, so nothing on the page moves.
 */
@Composable
private fun EditBar(st: HomeState, ui: Ui) {
    val item = ui.selected?.let { id -> st.home.firstOrNull { it.id == id } }
    Box(Modifier.fillMaxWidth().height(66.dp), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.wrapContentHeight(Alignment.Bottom, unbounded = true)) {
            androidx.compose.animation.AnimatedContent(
                item?.id,
                transitionSpec = {
                    (androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.94f)) togetherWith
                        (androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.94f))
                },
                contentAlignment = Alignment.BottomCenter,
                label = "editbar",
            ) { id ->
                val it = id?.let { i -> st.home.firstOrNull { h -> h.id == i } }
                if (it != null) PickedCard(st, ui, it) else EditTools(st, ui)
            }
        }
    }
}

@Composable
private fun EditTools(st: HomeState, ui: Ui) {
    val pal = LocalPal.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.frosted(st, RoundedCornerShape(12.dp), wash = 0.4f, edge = 0.18f).padding(horizontal = 14.dp, vertical = 7.dp),
        ) { Lbl("Tap to change · hold to move", size = 12.sp) }
        Spacer(Modifier.height(34.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                // The dock's glass and squircle shape, not a solid capsule.
                Modifier.height(62.dp).frosted(st, RoundedCornerShape(22.dp), wash = 0.26f, edge = 0.2f).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EditTool("widgets", "Widgets") { ui.layer = Layer.Widgets }
                EditTool("wallpaper", "Wallpaper") { ui.pickWallpaper() }
                EditTool("tune", "Settings") { ui.layer = Layer.Settings }
            }
            Box(
                Modifier.size(62.dp).clip(RoundedCornerShape(20.dp)).background(pal.acc).pressScale().clickable { ui.editing = false },
                contentAlignment = Alignment.Center,
            ) { Glyph("check", size = 28.dp, color = pal.ink) }
        }
    }
}

@Composable
private fun EditTool(glyph: String, label: String, onClick: () -> Unit) {
    Column(
        Modifier.width(78.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Glyph(glyph, size = 22.dp)
        Spacer(Modifier.height(3.dp))
        Lbl(label, size = 11.sp)
    }
}

/** The picked item's card: what it is, its size, and what you can do with it. */
@Composable
private fun PickedCard(st: HomeState, ui: Ui, item: HomeItem) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val app = if (item.type == "app") st.app(item.app) else null
    val name = when (item.type) {
        "app" -> app?.label ?: "App"
        "folder" -> item.name.ifBlank { "Folder" }
        "shortcut" -> item.name
        "nwidget" -> ninomaeWidgets.firstOrNull { it.first == item.kind }?.second ?: "Widget"
        "stack" -> "Widget stack"
        else -> AppWidgets.info(item.widgetId)?.loadLabel(ctx.packageManager) ?: "Widget"
    }
    val glyph = when (item.type) {
        "folder" -> "folder"
        "shortcut" -> "shortcut"
        "stack" -> "layers"
        "nwidget" -> when (item.kind) { "clock" -> "schedule"; "calendar" -> "calendar_month"; "player" -> "music_note"; "greeting" -> "waving_hand"; "jump" -> "history"; else -> "widgets" }
        else -> "widgets"
    }
    val about = if (item.resizable) "${item.w} × ${item.h} · drag the edges to resize" else "Hold to move it"
    Column(
        Modifier.width(380.dp).frosted(st, RoundedCornerShape(26.dp), wash = 0.4f, edge = 0.2f).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (app != null) GlassIcon(app, 38.dp) else Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(pal.acc.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) { Glyph(glyph, size = 21.dp, color = pal.acc) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Lbl(name, size = 15.sp, weight = FontWeight.Medium)
                Lbl(about, size = 11.sp, color = pal.sub)
            }
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(pal.acc).clickable { ui.selected = null },
                contentAlignment = Alignment.Center,
            ) { Glyph("check", size = 22.dp, color = pal.ink) }
        }
        Spacer(Modifier.height(12.dp))
        val actions = buildList<Triple<String, String, () -> Unit>> {
            if (app != null) add(Triple("edit", "Rename") { ui.selected = null; ui.rename(app.key) })
            if (item.type == "folder") add(Triple("folder_open", "Open") { ui.selected = null; ui.openFolder(item.id) })
            if (item.type == "stack") add(Triple("layers_clear", "Unstack") {
                val i = (ui.stackPage[item.id] ?: 0).coerceIn(0, item.apps.lastIndex)
                st.unstack(item.id, item.apps[i])
            })
            if (item.isWidget) add(Triple("add", "Another widget") { ui.selected = null; ui.layer = Layer.Widgets })
            add(Triple("delete", "Remove") { ui.selected = null; st.removeItem(item.id) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            actions.forEachIndexed { i, (g, t, go) ->
                val l = if (i == 0) 20.dp else 6.dp
                val r = if (i == actions.lastIndex) 20.dp else 6.dp
                val danger = g == "delete"
                Row(
                    Modifier.height(40.dp).clip(corners(l, r, r, l)).background(glass(0.08f)).clickable(onClick = go).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(g, size = 17.dp, color = if (danger) Color(0xFFE8A39B) else pal.acc)
                    Spacer(Modifier.width(5.dp))
                    Lbl(t, size = 12.sp)
                }
            }
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
    val span = item?.takeIf { it.resizable }
    val big = span != null
    val wDp = if (span != null) (ui.cellWdp * span.w).dp else 60.dp
    val hDp = if (span != null) (ui.cellHdp * span.h).dp else 60.dp
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

/** One minute clock for everything on screen. It ticks on the minute, and again on resume (timers stop in deep sleep). */
object Clock {
    var now by mutableStateOf(LocalDateTime.now())
        private set
    private var job: kotlinx.coroutines.Job? = null

    fun tick() { now = LocalDateTime.now() }

    fun start() {
        if (job?.isActive == true) return
        job = kotlinx.coroutines.MainScope().launch {
            while (true) {
                tick()
                delay(60_000 - System.currentTimeMillis() % 60_000 + 50)
            }
        }
    }

    fun stop() { job?.cancel(); job = null }
}

fun rememberNow(): LocalDateTime = Clock.now

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
