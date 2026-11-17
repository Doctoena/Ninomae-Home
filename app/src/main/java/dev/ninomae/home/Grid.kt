package dev.ninomae.home

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
    val pages = st.pageCount(screen) + if (dragging || ui.editing) 1 else 0
    val pager = rememberPagerState(pageCount = { pages })
    val density = LocalDensity.current
    val notes by NotifService.notes.collectAsState()
    val badged = remember(notes) { notes.map { it.pkg }.toSet() }

    SideEffect {
        ui.screen = screen
        st.lastScreen = screen
    }
    LaunchedEffect(pager.currentPage) { ui.page = pager.currentPage }
    // The wallpaper drifts a little as you turn pages.
    val view = androidx.compose.ui.platform.LocalView.current
    val ctx = LocalContext.current
    LaunchedEffect(pages) {
        val wm = android.app.WallpaperManager.getInstance(ctx)
        runCatching { wm.setWallpaperOffsetSteps(if (pages > 1) 1f / (pages - 1) else 1f, 1f) }
        androidx.compose.runtime.snapshotFlow { pager.currentPage + pager.currentPageOffsetFraction }.collect { f ->
            val x = if (pages > 1) (f / (pages - 1)).coerceIn(0f, 1f) else 0.5f
            view.windowToken?.let { runCatching { wm.setWallpaperOffsets(it, x, 0.5f) } }
        }
    }
    LaunchedEffect(ui.jumpToPage) {
        ui.jumpToPage?.let { pager.animateScrollToPage(it.coerceIn(0, pages - 1)) }
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
            HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !dragging, beyondViewportPageCount = 1) { page ->
                Box(Modifier.fillMaxSize().desktopGestures(st, ui)) {
                    if (ui.editing) EditCells(cols, rows, cw, ch)
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
                onLongPress = { ui.editing = true },
                onTap = { if (ui.editing) ui.editing = false },
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
                    mode = if (dy < 0) -1 else when (st.swipeDown) { 0 -> 1; 1 -> 2; else -> 3 }
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
            "folder" -> HomeIcon(
                st, item.name.ifBlank { "Folder" },
                st.dots && item.apps.any { st.app(it)?.pkg in badged },
                Modifier.holdable(onTap = { if (!ui.editing) ui.openFolder(item.id) }, onHold = hold),
            ) { FolderIcon(st, item, 56.dp) }
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
        }
        if (ui.editing) EditOverlay(st, ui, item, hold)
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
            if (badge) {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(3.dp, (-3).dp).size(13.dp).clip(CircleShape)
                        .background(pal.l0).padding(2.dp).clip(CircleShape).background(pal.acc),
                )
            }
        }
        if (st.labels) {
            Spacer(Modifier.height(6.dp))
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
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(pal.l3.copy(alpha = 0.92f))
            .border(1.dp, pal.fg.copy(alpha = 0.12f), RoundedCornerShape(size * 0.3f))
            .padding(size * 0.14f),
    ) {
        val apps = item.apps.take(4).map { st.app(it) }
        val s = size * 0.33f
        apps.forEachIndexed { i, a ->
            AppIcon(a, s, Modifier.offset(if (i % 2 == 0) 0.dp else s + size * 0.06f, if (i < 2) 0.dp else s + size * 0.06f))
        }
    }
}

@Composable
private fun EditOverlay(st: HomeState, ui: Ui, item: HomeItem, hold: (Offset) -> Unit) {
    val pal = LocalPal.current
    Box(Modifier.fillMaxSize()) {
        if (item.resizable) {
            Box(
                Modifier.fillMaxSize()
                    .border(1.5.dp, pal.acc, RoundedCornerShape(Rad.normal))
                    .clip(RoundedCornerShape(Rad.normal))
                    .background(pal.acc.copy(alpha = 0.08f))
                    .holdable(onTap = {}, onHold = hold),
            )
            Row(
                Modifier.align(Alignment.BottomEnd).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                EditButton("keyboard_arrow_left") { st.resize(item.id, -1, 0) }
                EditButton("keyboard_arrow_right") { st.resize(item.id, 1, 0) }
                EditButton("keyboard_arrow_up") { st.resize(item.id, 0, -1) }
                EditButton("keyboard_arrow_down") { st.resize(item.id, 0, 1) }
            }
        }
        EditButton("close", Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp)) { st.removeItem(item.id) }
    }
}

@Composable
private fun EditButton(glyph: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val pal = LocalPal.current
    Box(
        modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(pal.l4)
            .border(1.dp, pal.fg.copy(alpha = 0.12f), RoundedCornerShape(9.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Glyph(glyph, size = 18.dp) }
}

@Composable
private fun EditCells(cols: Int, rows: Int, cw: Dp, ch: Dp) {
    val pal = LocalPal.current
    for (x in 0 until cols) for (y in 0 until rows) {
        Box(
            Modifier.offset(cw * x, ch * y).size(cw, ch).padding(6.dp)
                .clip(RoundedCornerShape(Rad.container)).background(pal.fg.copy(alpha = 0.05f)),
        )
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
    if (target != null && key != null && (target.type == "folder" || target.type == "app")) {
        Box(
            Modifier.offset(cw * target.x, ch * target.y).size(cw, ch).padding(6.dp)
                .border(2.dp, pal.acc, RoundedCornerShape(Rad.normal)),
        )
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
        "greeting" -> Greeting(st, all, Modifier.clickable { ui.toggle(Layer.Sidebar) })
        "player" -> media?.let { PlayerCard(it, all) } ?: if (!ui.editing) Unit else Piece(Modifier.fillMaxWidth().height(98.dp), all) {
            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
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
            if (items.isEmpty()) Piece(Modifier.fillMaxWidth().height(64.dp), all) {
                Lbl("Apps you open show up here", Modifier.align(Alignment.Center), size = 13.sp, color = pal.sub)
            } else JumpBack(st, items, all)
        }
        "clock" -> {
            val now = rememberNow()
            Column(Modifier.fillMaxSize().clickable { Actions.clock(ctx) }, verticalArrangement = Arrangement.Center) {
                val shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 2f), 12f)
                BasicText(
                    clockText(now),
                    style = TextStyle(color = Color.White, fontSize = 64.sp, fontFamily = TitleFont, fontWeight = FontWeight.Black, shadow = shadow),
                )
                BasicText(
                    now.format(DateTimeFormatter.ofPattern("EEEE d MMMM")),
                    style = TextStyle(color = Color.White, fontSize = 16.sp, fontFamily = Sans, fontWeight = FontWeight.Medium, shadow = shadow),
                )
            }
        }
    }
}
