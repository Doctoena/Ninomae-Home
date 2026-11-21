package dev.ninomae.home

import androidx.compose.foundation.gestures.detectDragGestures
import android.accessibilityservice.AccessibilityService
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter

/** Pages of apps, folders and widgets between the bar and the dock. */
@Composable
fun HomeGrid(st: HomeState, ui: Ui, compact: Boolean, modifier: Modifier) {
    val screen = if (compact) 1 else 0
    val cols = gridCols(screen)
    val rows = gridRows(screen)
    val dragging = ui.drag != null
    // The widgets page sits left of the first home page (not while editing), so home pages shift by [off].
    val off = if (st.feedOn && !ui.editing) 1 else 0
    val pages = off + st.pageCount(screen) + if (dragging || ui.editing) 1 else 0
    val pager = rememberPagerState(initialPage = off, pageCount = { pages })
    val density = LocalDensity.current
    val notes by NotifService.notes.collectAsState()
    val badged = remember(notes) { notes.map { it.pkg }.toSet() }

    SideEffect {
        ui.screen = screen
        st.lastScreen = screen
    }
    // Folding or unfolding: each screen goes back to the page it was on (its own layout); on the widgets page,
    // which both screens share, you stay there.
    val pageOn = remember { mutableMapOf<Int, Int>() }
    var shownScreen by remember { mutableIntStateOf(screen) }
    LaunchedEffect(screen) {
        if (shownScreen == screen) return@LaunchedEffect
        val onFeed = off == 1 && pager.currentPage == 0
        shownScreen = screen
        pager.scrollToPage((if (onFeed) 0 else pageOn[screen] ?: off).coerceIn(0, pages - 1))
    }
    LaunchedEffect(pager.currentPage, off) {
        if (shownScreen == screen) pageOn[screen] = pager.currentPage
        ui.page = pager.currentPage - off
        ui.selected = null
    }
    // Turning the widgets page on or off, or entering edit mode, keeps you on the same home page.
    var lastOff by remember { mutableIntStateOf(off) }
    LaunchedEffect(off) {
        if (off != lastOff) { pager.scrollToPage((pager.currentPage + off - lastOff).coerceAtLeast(0)); lastOff = off }
    }
    LaunchedEffect(ui.editing) { if (!ui.editing) ui.selected = null }
    // The wallpaper stays centred on every page (the user doesn't want it spanning across pages).
    val view = androidx.compose.ui.platform.LocalView.current
    val ctx = LocalContext.current
    LaunchedEffect(view.windowToken) {
        val wm = android.app.WallpaperManager.getInstance(ctx)
        runCatching { wm.setWallpaperOffsetSteps(0f, 0f) }
        view.windowToken?.let { runCatching { wm.setWallpaperOffsets(it, 0.5f, 0.5f) } }
    }
    LaunchedEffect(ui.jumpToPage) {
        ui.jumpToPage?.let { pager.animateScrollToPage((it + off).coerceIn(0, pages - 1)) }
        ui.jumpToPage = null
    }
    // Hold a drag at the left or right edge to turn the page.
    LaunchedEffect(dragging) {
        var dwell = 0
        val edge = with(density) { 32.dp.toPx() }
        while (ui.drag != null) {
            delay(100)
            val p = ui.dragPos
            val r = ui.gridRect
            val dir = when {
                p.y !in r.top..r.bottom -> 0
                p.x < r.left + edge -> -1
                p.x > r.right - edge -> 1
                else -> 0
            }
            if (dir == 0) dwell = 0 else if (++dwell >= 6) {
                dwell = 0
                val t = pager.currentPage + dir
                if (t in 0 until pager.pageCount) pager.animateScrollToPage(t)
            }
        }
    }

    Column(modifier) {
        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { ui.gridRect = it.boundsInRoot() },
        ) {
            val cw = maxWidth / cols
            val ch = maxHeight / rows
            SideEffect {
                ui.cellW = with(density) { cw.toPx() }
                ui.cellH = with(density) { ch.toPx() }
                ui.cellWdp = cw.value
                ui.cellHdp = ch.value
            }
            HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !dragging, beyondViewportPageCount = 1) { pagerPage ->
                if (pagerPage < off) { FeedPage(st, ui, compact); return@HorizontalPager }
                val page = pagerPage - off
                Box(Modifier.fillMaxSize().desktopGestures(st, ui)) {
                    if (ui.editing) EditDots(cols, rows, cw, ch)
                    st.home.filter { it.screen == screen && it.page == page }.forEach { item ->
                        key(item.id) { ItemView(st, ui, item, cw, ch, badged) }
                    }
                    if (dragging && ui.page == page) DropHint(st, ui, cw, ch)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.Center) {
            if (pages > 1) PageDots(pages, pager.currentPage)
        }
    }
}

/** Empty desktop: swipe up for all apps, swipe down for the sidebar, double-tap to lock, hold to edit. */
private fun Modifier.desktopGestures(st: HomeState, ui: Ui): Modifier = composed {
    val ctx = LocalContext.current
    this
        .pointerInput(Unit) {
            detectTapGestures(
                onDoubleTap = { if (st.doubleTapLock && !ui.editing) Actions.global(ctx, AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) },
                onLongPress = {
                    if (st.locked) android.widget.Toast.makeText(ctx, "The home screen is locked. Unlock it in settings.", android.widget.Toast.LENGTH_SHORT).show()
                    else ui.editing = true
                },
                onTap = { if (ui.editing) { if (ui.selected != null) ui.selected = null else ui.editing = false } },
            )
        }
        .pointerInput(Unit) {
            // Swipe down pulls the deck, swipe up raises the sheet, both under the finger.
            val vt = androidx.compose.ui.input.pointer.util.VelocityTracker()
            var mode = 0
            detectVerticalDragGestures(
                onDragStart = { vt.resetTracking(); mode = 0 },
                onDragEnd = {
                    val v = vt.calculateVelocity().y
                    when (mode) {
                        1 -> ui.settleDeck(v)
                        -1 -> ui.settleSheet(v)
                        2 -> Actions.global(ctx, AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                    }
                    mode = 0
                },
                onDragCancel = {
                    when (mode) { 1 -> ui.settleDeck(0f); -1 -> ui.settleSheet(0f) }
                    mode = 0
                },
            ) { c, dy ->
                vt.addPosition(c.uptimeMillis, c.position)
                if (mode == 0) {
                    mode = if (dy < 0) -1 else when (if (ui.screen == 1) st.swipeDown else st.swipeDownInner) { 0 -> 1; 1 -> 2; else -> 3 }
                    if (mode == 1 || mode == -1) ui.gesturing = true
                }
                val travel = ui.screenHpx * 0.5f
                when (mode) {
                    1 -> ui.deck = (ui.deck + dy / travel).coerceIn(0f, 1f)
                    -1 -> ui.sheet = (ui.sheet - dy / travel).coerceIn(0f, 1f)
                }
            }
        }
}

@Composable
private fun ItemView(st: HomeState, ui: Ui, item: HomeItem, cw: Dp, ch: Dp, badged: Set<String>) {
    val ctx = LocalContext.current
    val beingDragged = ui.drag?.src?.item?.id == item.id
    val held = ui.layer == Layer.Menu && ui.menuHomeId == item.id
    val lift by androidx.compose.animation.core.animateFloatAsState(if (held) 1f else 0f, androidx.compose.animation.core.spring(0.6f, 500f), label = "lift")
    var rect by remember { mutableStateOf(Rect.Zero) }
    var iconRect by remember { mutableStateOf<Rect?>(null) }
    Box(
        Modifier
            .offset(cw * item.x, ch * item.y)
            .width(cw * item.w)
            .height(ch * item.h)
            .alpha(if (beingDragged) 0f else 1f)
            .graphicsLayer { val k = 1f + 0.08f * lift; scaleX = k; scaleY = k }
            .padding(4.dp)
            .onGloballyPositioned { rect = it.boundsInRoot() },
    ) {
        val hold: (Offset) -> Unit = { pos -> ui.hold = Hold(DragSrc(item = item), pos) }
        when (item.type) {
            "app" -> {
                val app = st.app(item.app)
                if (lift > 0.01f) Box(
                    Modifier.align(Alignment.TopCenter).padding(top = (ch - 92.dp) / 2).size(72.dp)
                        .graphicsLayer { alpha = lift }.clip(RoundedCornerShape(24.dp))
                        .background(LocalPal.current.acc.copy(alpha = 0.3f)),
                )
                HomeIcon(
                    st, app?.label ?: "Missing", app != null && st.dots && app.pkg in badged,
                    Modifier.pressScale().holdable(
                        onTap = { if (!ui.editing && app != null) Actions.open(ctx, st, app) },
                        onHold = { pos ->
                            hold(pos)
                            if (!ui.editing && app != null) ui.showMenu(app, rect, above = rect.center.y > ui.gridRect.center.y, fromDock = false, homeId = item.id)
                        },
                    ),
                ) { AppIcon(app, 56.dp) }
            }
            "folder" -> if (item.w >= 2 && item.h >= 2) BigFolder(st, ui, item, badged, hold) else HomeIcon(
                st, item.name.ifBlank { "Folder" },
                st.dots && item.apps.any { st.app(it)?.pkg in badged },
                Modifier.holdable(onTap = { if (!ui.editing) ui.openFolder(item.id, iconRect) }, onHold = hold),
            ) {
                // While it's open, the folder layer draws it lifted over the dim, in this same spot.
                Box(
                    Modifier.onGloballyPositioned { iconRect = it.boundsInRoot() }
                        .alpha(if (ui.inFolder && ui.folderId == item.id && ui.folderIcon != null) 0f else 1f),
                ) { FolderIcon(st, item, 56.dp) }
            }
            "shortcut" -> {
                val info = remember(item.shortcutId) { Shortcuts.find(ctx, item.pkg, item.shortcutId, item.userSerial) }
                val icon = remember(info) { info?.let { Shortcuts.icon(ctx, it) } }
                HomeIcon(
                    st, item.name, false,
                    Modifier.holdable(onTap = { if (!ui.editing) Shortcuts.start(ctx, item.pkg, item.shortcutId, item.userSerial) }, onHold = hold),
                ) {
                    Box {
                        if (icon != null) androidx.compose.foundation.Image(icon, item.name, Modifier.size(56.dp))
                        else AppIcon(st.byPkg[item.pkg], 56.dp)
                        AppIcon(st.byPkg[item.pkg], 20.dp, Modifier.align(Alignment.BottomEnd))
                    }
                }
            }
            "widget" -> AndroidWidget(item.widgetId, cw.value * item.w - 8, ch.value * item.h - 8, Modifier.clip(RoundedCornerShape(Rad.normal)))
            "nwidget" -> NinomaeWidget(st, ui, item.kind)
            "stack" -> WidgetStack(st, ui, item, cw.value * item.w - 8, ch.value * item.h - 8)
        }
        if (ui.editing) EditLayer(st, ui, item, cw, ch, hold)
    }
}

@Composable
private fun HomeIcon(st: HomeState, label: String, badge: Boolean, modifier: Modifier, icon: @Composable () -> Unit) {
    val pal = LocalPal.current
    Column(
        modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            icon()
            if (badge) NoteBar()
        }
        if (st.labels) {
            Spacer(Modifier.height(10.dp))
            BasicText(
                label,
                Modifier.padding(horizontal = 2.dp),
                style = TextStyle(
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1.5f), 6f),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun FolderIcon(st: HomeState, item: HomeItem, size: Dp) {
    val pal = LocalPal.current
    val shape = iconShape(size)
    val apps = item.apps.take(4).map { st.app(it) }
    // Two apps side by side, bigger; three or four in a 2 × 2, all centred in a glass square.
    val cell = if (apps.size <= 2) size * 0.4f else size * 0.33f
    val gap = size * 0.06f
    // Frosted like the dock: the blurred wallpaper behind it reads clearly against the sharp one around it.
    Box(
        Modifier.size(size).frosted(st, shape, wash = 0.34f, edge = 0.26f),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            apps.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { a -> if (a != null) GlassIcon(a, cell) else AppIcon(null, cell) }
                }
            }
        }
    }
}

/**
 * Edit mode on one item (E2b): a faint outline until you tap it; tapped, it lights up with handles on
 * its edges to drag to a new size, and the bottom bar turns into its card. Hold still drags it.
 */
@Composable
private fun EditLayer(st: HomeState, ui: Ui, item: HomeItem, cw: Dp, ch: Dp, hold: (Offset) -> Unit) {
    val pal = LocalPal.current
    val sel = ui.selected == item.id
    val other = ui.selected != null && !sel
    val shape = RoundedCornerShape(Rad.normal + 4.dp)
    Box(
        Modifier.fillMaxSize()
            .then(
                if (sel) Modifier.border(2.dp, pal.acc, shape).clip(shape).background(pal.acc.copy(alpha = 0.08f))
                else Modifier.border(1.5.dp, Color.White.copy(alpha = if (other) 0.15f else 0.38f), shape),
            )
            .holdable(onTap = { ui.selected = if (sel) null else item.id }, onHold = hold),
    )
    if (sel && item.resizable) {
        val density = LocalDensity.current
        val cwPx = with(density) { cw.toPx() }
        val chPx = with(density) { ch.toPx() }
        Box(Modifier.fillMaxSize()) {
            EdgeHandle(Modifier.align(Alignment.TopCenter), Alignment.TopCenter, false, chPx) { d -> st.reshape(item.id, 0, d, 0, -d) }
            EdgeHandle(Modifier.align(Alignment.BottomCenter), Alignment.BottomCenter, false, chPx) { d -> st.reshape(item.id, 0, 0, 0, d) }
            EdgeHandle(Modifier.align(Alignment.CenterStart), Alignment.CenterStart, true, cwPx) { d -> st.reshape(item.id, d, 0, -d, 0) }
            EdgeHandle(Modifier.align(Alignment.CenterEnd), Alignment.CenterEnd, true, cwPx) { d -> st.reshape(item.id, 0, 0, d, 0) }
        }
    }
}

/**
 * A glowing bar on one edge of the picked item. Dragging it steps the size a cell at a time
 * ([onStep] gets +1 or -1 along its axis, + meaning right or down).
 */
@Composable
private fun EdgeHandle(modifier: Modifier, side: Alignment, vertical: Boolean, cellPx: Float, onStep: (Int) -> Unit) {
    val pal = LocalPal.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(
        modifier
            // The touch area stays inside the item (touches outside a layout never reach it).
            .size(if (vertical) 36.dp else 72.dp, if (vertical) 72.dp else 36.dp)
            .pointerInput(cellPx) {
                var acc = 0f
                detectDragGestures(onDragStart = { acc = 0f }) { c, d ->
                    c.consume()
                    acc += if (vertical) d.x else d.y
                    val step = cellPx * 0.6f
                    while (acc > step) { acc -= cellPx; onStep(1); haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove) }
                    while (acc < -step) { acc += cellPx; onStep(-1); haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove) }
                }
            },
        contentAlignment = side,
    ) {
        Box(
            Modifier.offset(
                x = if (side == Alignment.CenterStart) (-7).dp else if (side == Alignment.CenterEnd) 7.dp else 0.dp,
                y = if (side == Alignment.TopCenter) (-7).dp else if (side == Alignment.BottomCenter) 7.dp else 0.dp,
            ).size(if (vertical) 14.dp else 42.dp, if (vertical) 42.dp else 14.dp).clip(RoundedCornerShape(7.dp))
                .background(pal.acc.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.size(if (vertical) 6.dp else 34.dp, if (vertical) 34.dp else 6.dp).clip(RoundedCornerShape(3.dp)).background(pal.acc)) }
    }
}

/** Edit mode's grid: small dots where cells meet, instead of boxes over everything. */
@Composable
private fun EditDots(cols: Int, rows: Int, cw: Dp, ch: Dp) {
    for (x in 1 until cols) for (y in 1 until rows) {
        Box(Modifier.offset(cw * x - 2.dp, ch * y - 2.dp).size(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.35f)))
    }
}

@Composable
private fun DropHint(st: HomeState, ui: Ui, cw: Dp, ch: Dp) {
    val pal = LocalPal.current
    val src = ui.drag?.src ?: return
    val w = src.item?.w ?: 1
    val h = src.item?.h ?: 1
    val under = cellUnderFinger(ui) ?: return
    val key = src.appKey ?: src.item?.takeIf { it.type == "app" }?.app
    val target = st.itemAt(ui.screen, ui.page, under.first, under.second, ignore = src.item?.id)
    val stacking = src.item?.isWidget == true && target?.isWidget == true
    if (target != null && (stacking || (key != null && (target.type == "folder" || target.type == "app")))) {
        Box(
            Modifier.offset(cw * target.x, ch * target.y).size(cw * target.w, ch * target.h).padding(4.dp)
                .clip(RoundedCornerShape(Rad.normal)).background(pal.acc.copy(alpha = 0.12f))
                .border(2.dp, pal.acc, RoundedCornerShape(Rad.normal)),
            contentAlignment = Alignment.Center,
        ) {
            if (stacking) Piece(Modifier.height(32.dp), RoundedCornerShape(Rad.segment), pal.acc) {
                Row(Modifier.padding(horizontal = 12.dp).align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                    Glyph("layers", size = 17.dp, color = pal.ink)
                    Spacer(Modifier.width(6.dp))
                    Lbl("Stack", size = 13.sp, color = pal.ink, weight = FontWeight.SemiBold)
                }
            }
        }
        return
    }
    val (x, y) = dropCell(ui, w, h) ?: return
    if (!st.fits(ui.screen, ui.page, x, y, w, h, ignore = src.item?.id)) return
    val ox by animateDpAsState(cw * x, label = "hx")
    val oy by animateDpAsState(ch * y, label = "hy")
    Box(
        Modifier.offset(ox, oy).size(cw * w, ch * h).padding(4.dp)
            .clip(RoundedCornerShape(Rad.normal)).background(pal.acc.copy(alpha = 0.16f))
            .border(1.5.dp, pal.acc.copy(alpha = 0.7f), RoundedCornerShape(Rad.normal)),
    )
}

@Composable
private fun PageDots(count: Int, current: Int) {
    val pal = LocalPal.current
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val w by animateDpAsState(if (i == current) 16.dp else 6.dp, label = "dot")
            Box(Modifier.width(w).height(6.dp).clip(CircleShape).background(if (i == current) pal.acc else Color.White.copy(alpha = 0.5f)))
        }
    }
}

// ---------- Ninomae widgets ----------

@Composable
fun NinomaeWidget(st: HomeState, ui: Ui, kind: String) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val media by Media.now.collectAsState()
    val all = RoundedCornerShape(Rad.normal)
    when (kind) {
        "stack" -> Widgets(st, ui, Modifier.fillMaxWidth())
        "greeting" -> Greeting(st, all, Modifier.clickable { ui.toggle(Layer.Deck) })
        "player" -> media?.let { PlayerCard(it, all) } ?: if (!ui.editing) Unit else Box(Modifier.fillMaxWidth().height(92.dp).frosted(st, all, wash = 0.34f, edge = 0.2f)) {
            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(null, 68.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Title("Nothing playing", size = 16.sp)
                    Lbl(if (st.notifAccess) "Play something and it shows here" else "Allow notification access in settings", size = 12.sp, color = pal.sub, maxLines = 2)
                }
            }
        }
        "jump" -> {
            val items = jumpItems(st)
            if (items.isEmpty()) Box(Modifier.fillMaxWidth().height(64.dp).frosted(st, all, wash = 0.34f, edge = 0.2f)) {
                Lbl("Apps you open show up here", Modifier.align(Alignment.Center), size = 13.sp, color = pal.sub)
            } else JumpBack(st, items, all)
        }
        "clock" -> ClockSlab(st)
        "calendar" -> CalendarWidget(st, ui, Modifier.fillMaxSize(), fit = true)
        "photos" -> PhotoWidget(st, ui, Modifier.fillMaxSize())
    }
}

/** Several widgets in one spot: swipe up and down through them, dots show which. */
@Composable
private fun WidgetStack(st: HomeState, ui: Ui, item: HomeItem, wDp: Float, hDp: Float) {
    val pal = LocalPal.current
    val pager = androidx.compose.foundation.pager.rememberPagerState(pageCount = { item.apps.size })
    LaunchedEffect(pager.currentPage) { ui.stackPage[item.id] = pager.currentPage }
    Box(Modifier.fillMaxSize()) {
        androidx.compose.foundation.pager.VerticalPager(pager, Modifier.fillMaxSize().clip(RoundedCornerShape(Rad.normal)), userScrollEnabled = !ui.editing) { i ->
            val part = item.apps[i]
            Box(Modifier.fillMaxSize()) {
                if (part.startsWith("w:")) AndroidWidget(part.drop(2).toIntOrNull() ?: 0, wDp, hDp, Modifier.clip(RoundedCornerShape(Rad.normal)))
                else NinomaeWidget(st, ui, part.drop(2))
            }
        }
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 3.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(item.apps.size) { i ->
                val h by animateDpAsState(if (i == pager.currentPage) 14.dp else 5.dp, label = "sd")
                Box(Modifier.size(5.dp, h).clip(CircleShape).background(if (i == pager.currentPage) pal.acc else Color.White.copy(alpha = 0.5f)))
            }
        }
    }
}

/** A folder big enough to show its apps (BF1): frosted like the small ones, names under the apps, tap them straight from home. */
@Composable
private fun BigFolder(st: HomeState, ui: Ui, item: HomeItem, badged: Set<String>, hold: (Offset) -> Unit) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize().frosted(st, RoundedCornerShape(26.dp), wash = 0.30f, edge = 0.22f)) {
        Column(Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 6.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .holdable(onTap = { if (!ui.editing) ui.openFolder(item.id) }, onHold = hold).padding(start = 10.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Title(item.name.ifBlank { "Folder" }, Modifier.weight(1f, fill = false), size = 18.sp)
                Spacer(Modifier.width(8.dp))
                Num("${item.apps.size}", size = 13.sp, color = pal.acc)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) { Glyph("open_in_full", size = 15.dp) }
            }
            Spacer(Modifier.height(4.dp))
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val cols = (maxWidth / 68.dp).toInt().coerceAtLeast(2)
                val rows = (maxHeight / 92.dp).toInt().coerceAtLeast(1)
                val shown = item.apps.take(cols * rows)
                Column(verticalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxSize()) {
                    shown.chunked(cols).forEach { line ->
                        Row(Modifier.fillMaxWidth()) {
                            line.forEach { k ->
                                val app = st.app(k)
                                Column(
                                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).pressScale()
                                        .holdable(onTap = { if (!ui.editing && app != null) Actions.open(ctx, st, app) }, onHold = hold)
                                        .padding(vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Box {
                                        if (app != null) GlassIcon(app, 52.dp) else AppIcon(null, 52.dp)
                                        if (st.dots && app?.pkg in badged) NoteBar()
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Lbl(app?.label ?: "Missing", Modifier.padding(horizontal = 2.dp), size = 11.sp, align = TextAlign.Center)
                                }
                            }
                            repeat(cols - line.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The clock (S3 · Slab + agenda): one pane of frosted glass. The time and date on the left; on the
 * right a little joined agenda of what's next: calendar event, alarm, weather. Anything missing drops
 * out, and with nothing to show the time has the slab to itself. No battery: the user doesn't want it.
 */
@Composable
private fun ClockSlab(st: HomeState) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val now = rememberNow()
    val ev = Glance.event
    val w = Glance.weather
    // Android shares the next alarm with any app; read it again each minute.
    val alarm = remember(now.minute) {
        ctx.getSystemService(android.app.AlarmManager::class.java)?.nextAlarmClock?.triggerTime
    }
    BoxWithConstraints(Modifier.fillMaxSize().frosted(st, RoundedCornerShape(30.dp), wash = 0.32f, edge = 0.24f)) {
        val wide = maxWidth > 300.dp
        val agendaW = if (wide) 150.dp else 100.dp
        val lines = buildList {
            if (ev != null) add(AgendaLine("event", ev.title, if (wide) ev.whenText else ev.whenText.substringBefore(' '), false) { Actions.calendar(ctx) })
            if (alarm != null) {
                val at = java.time.Instant.ofEpochMilli(alarm).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                val time = android.text.format.DateFormat.getTimeFormat(ctx).format(java.util.Date(alarm))
                val soon = java.time.Duration.between(now, at).toHours() < 20
                add(AgendaLine("alarm", if (wide) "Alarm" else time, if (wide) (if (soon) time else at.format(DateTimeFormatter.ofPattern("EEE ")) + time) else "", false) { Actions.clock(ctx) })
            }
            if (w != null) add(AgendaLine(w.glyph, if (wide) "${w.temp} ${w.text}" else w.temp, "", true) {})
        }
        val showAgenda = lines.isNotEmpty() && maxWidth > 200.dp
        val leftW = if (showAgenda) maxWidth - agendaW - 12.dp else maxWidth
        val timeSize = with(LocalDensity.current) { minOf(maxHeight * 0.48f, leftW * 0.3f).toSp() }
        Column(
            Modifier.align(if (showAgenda) Alignment.CenterStart else Alignment.Center).width(leftW).padding(start = if (showAgenda) 20.dp else 0.dp),
            horizontalAlignment = if (showAgenda) Alignment.Start else Alignment.CenterHorizontally,
        ) {
            BasicText(
                clockText(now),
                Modifier.clip(RoundedCornerShape(12.dp)).clickable { Actions.clock(ctx) },
                style = TextStyle(color = pal.fg, fontSize = timeSize, fontFamily = TitleFont, fontWeight = FontWeight.Black),
                maxLines = 1,
            )
            Lbl(
                now.format(DateTimeFormatter.ofPattern(if (wide) "EEEE d MMMM" else "EEE d MMM")),
                Modifier.padding(start = 3.dp).clip(RoundedCornerShape(8.dp)).clickable { Actions.calendar(ctx) },
                size = 13.sp, color = pal.sub,
            )
        }
        if (showAgenda) Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).width(agendaW),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            lines.forEachIndexed { i, l ->
                val t = if (i == 0) 14.dp else 5.dp
                val b = if (i == lines.lastIndex) 14.dp else 5.dp
                Row(
                    Modifier.fillMaxWidth().height(30.dp).clip(corners(t, t, b, b)).background(Color.White.copy(alpha = 0.08f))
                        .clickable(onClick = l.go).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(l.glyph, size = 15.dp, color = pal.acc, fill = l.fill)
                    Spacer(Modifier.width(6.dp))
                    Lbl(l.text, Modifier.weight(1f, fill = false), size = 12.sp)
                    if (l.note.isNotEmpty()) {
                        Spacer(Modifier.width(5.dp))
                        Lbl(l.note, size = 11.sp, color = pal.sub)
                    }
                }
            }
        }
    }
}

private class AgendaLine(val glyph: String, val text: String, val note: String, val fill: Boolean, val go: () -> Unit)
