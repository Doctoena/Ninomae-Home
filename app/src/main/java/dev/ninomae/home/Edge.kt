package dev.ninomae.home

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/*
 * The edge sidebar (O2 · magnifier rail): slide up along the chosen edge of the home screen and a slim
 * rail of your own apps grows out of that edge, at your thumb or in the middle. Keep the finger down and
 * glide (M1): the icon under it and its neighbours swell inside the rail, the one under it on a soft rose
 * glow, and its name sits in a bubble joined onto the rail. Lift to open it; lift anywhere else and the rail stays open for taps.
 */

private val EDGE_W = 70.dp
/** Each icon has a fixed slot, so what's under the finger never shifts while the icons swell. */
private val EDGE_ROW = 52.dp
private val EDGE_PAD = 8.dp
/** How far in from the edge a slide can start. */
private val EDGE_ZONE = 24.dp

/** The edge's apps, in order. */
fun HomeState.edgeList(): List<AppEntry> = edgeApps.mapNotNull { app(it) }

/**
 * Watches every touch from the root, before anything else sees it. A slide that starts at the edge
 * and goes up opens the sidebar; from then on the finger picks rows. Anything else passes through.
 */
fun Modifier.edgeTracker(st: HomeState, ui: Ui, tick: () -> Unit, open: (AppEntry) -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val first = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.pressed && !it.previousPressed } ?: continue
            if (!st.edgeOn || ui.layer != Layer.None || ui.editing || ui.drag != null) continue
            val zone = EDGE_ZONE.toPx()
            val x = first.position.x
            val atEdge = if (st.edgeRight) x > size.width - zone else x < zone
            val r = ui.gridRect
            if (!atEdge || r == androidx.compose.ui.geometry.Rect.Zero || first.position.y !in r.top..r.bottom) continue

            val id = first.id
            var opened = false
            // The apps, taken once when the rail opens rather than on every move.
            var items = emptyList<AppEntry>()
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val c = ev.changes.firstOrNull { it.id == id } ?: break
                if (!c.pressed) {
                    if (opened) {
                        c.consume()
                        val app = items.getOrNull(ui.edgeSel)
                        ui.edgeSel = -1
                        if (app != null) { ui.close(); open(app) }
                    }
                    break
                }
                if (!opened) {
                    val d = c.position - first.position
                    if (d.getDistance() < viewConfiguration.touchSlop) continue
                    // Up along the edge, not across it (across is the page swipe, or Android's back).
                    if (-d.y <= abs(d.x) * 1.2f) break
                    items = st.edgeList()
                    val n = items.size.coerceAtLeast(1)
                    val listH = n * EDGE_ROW.toPx() + 2 * EDGE_PAD.toPx()
                    val top = (if (st.edgeMiddle) r.center.y else c.position.y) - listH / 2
                    ui.edgeTop = top.coerceIn(r.top, (r.bottom - listH).coerceAtLeast(r.top))
                    ui.edgeSel = -1
                    ui.layer = Layer.Edge
                    opened = true
                    tick()
                }
                c.consume()
                // Which icon is under the finger (anywhere across the rail, and a thumb's width beyond it).
                val w = EDGE_W.toPx()
                val inside = if (st.edgeRight) c.position.x > size.width - w - 72.dp.toPx() else c.position.x < w + 72.dp.toPx()
                val row = ((c.position.y - ui.edgeTop - EDGE_PAD.toPx()) / EDGE_ROW.toPx()).let { if (it < 0f) -1 else it.toInt() }
                val sel = if (inside && row in items.indices) row else -1
                if (sel != ui.edgeSel) {
                    ui.edgeSel = sel
                    if (sel >= 0) tick()
                }
            }
        }
    }
}

/** The rail, grown out of its edge, and the name of the icon under the finger beside it. [progress] is read only while drawing. */
@Composable
fun EdgePanel(st: HomeState, ui: Ui, progress: () -> Float, open: (AppEntry) -> Unit) {
    val pal = LocalPal.current
    val density = LocalDensity.current
    val items = remember(st.edgeApps, st.apps) { st.edgeList() }
    val right = st.edgeRight
    val shape = if (right) corners(26.dp, 0.dp, 0.dp, 26.dp) else corners(0.dp, 26.dp, 26.dp, 0.dp)
    val top = with(density) { ui.edgeTop.toDp() }
    val side = if (right) Alignment.TopEnd else Alignment.TopStart
    val slide = Modifier.graphicsLayer {
        val e = progress().coerceIn(0f, 1f)
        alpha = e
        translationX = (1f - e) * 40.dp.toPx() * (if (right) 1f else -1f)
    }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(side)
                .offset(y = top)
                .width(if (items.isEmpty()) 170.dp else EDGE_W)
                .then(slide),
        ) {
            // The glass is its own layer behind the icons, so the rose glow isn't cut off at its edge.
            Box(
                Modifier.matchParentSize()
                    .frosted(st, shape, wash = 0.40f, edge = 0.16f)
                    // A thin rose line along the screen edge it came out of.
                    .drawBehind {
                        val lw = 3.dp.toPx()
                        val inset = 12.dp.toPx()
                        drawRoundRect(
                            pal.acc.copy(alpha = 0.9f),
                            topLeft = Offset(if (right) size.width - lw else 0f, inset),
                            size = Size(lw, size.height - 2 * inset),
                            cornerRadius = CornerRadius(lw / 2),
                        )
                    },
            )
            Column(Modifier.fillMaxWidth().padding(vertical = EDGE_PAD)) {
                if (items.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(EDGE_ROW).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                        Lbl("Hold an app → Add to edge", size = 12.sp, color = pal.sub, maxLines = 2)
                    }
                }
                items.forEachIndexed { i, app ->
                    // Swell by how close the finger is: the icon under it, then its neighbours.
                    val near = if (ui.edgeSel < 0) 0f else when (kotlin.math.abs(i - ui.edgeSel)) { 0 -> 1f; 1 -> 0.35f; else -> 0f }
                    EdgeIcon(app, i, near, i == ui.edgeSel, progress) { ui.close(); open(app) }
                }
            }
        }
        // The name, floating out beside the rail, level with the icon under the finger.
        val sel = items.getOrNull(ui.edgeSel)
        if (sel != null) {
            val y = top + EDGE_PAD + EDGE_ROW * ui.edgeSel + EDGE_ROW / 2 - 21.dp
            Box(
                Modifier.align(side).offset(y = y)
                    .padding(start = if (right) 0.dp else EDGE_W + 4.dp, end = if (right) EDGE_W + 4.dp else 0.dp)
                    .then(slide)
                    .height(42.dp)
                    // Joined onto the rail: small corners on the side that meets it.
                    .frosted(st, if (right) corners(21.dp, 6.dp, 6.dp, 21.dp) else corners(6.dp, 21.dp, 21.dp, 6.dp), wash = 0.55f, edge = 0.2f)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) { Title(sel.label, size = 16.sp) }
        }
    }
}

/** One icon in the rail. [near] (0..1) is how much it swells, in place; the one under the finger glows rose behind. */
@Composable
private fun EdgeIcon(app: AppEntry, i: Int, near: Float, on: Boolean, progress: () -> Float, onClick: () -> Unit) {
    val pal = LocalPal.current
    val k by animateFloatAsState(near, spring(0.7f, 700f), label = "edgeIcon")
    val lit by animateFloatAsState(if (on) 1f else 0f, spring(0.8f, 800f), label = "edgeLit")
    Box(
        Modifier
            .fillMaxWidth()
            .height(EDGE_ROW)
            .graphicsLayer {
                // Icons land one after another from the top as it opens.
                val lag = (i * 0.05f).coerceAtMost(0.4f)
                val e = ((progress() - lag) / (1f - lag)).coerceIn(0f, 1f)
                alpha = e
                translationY = (1f - e) * 10.dp.toPx()
            }
            // A soft rose glow behind the icon under the finger.
            .drawBehind {
                if (lit > 0.01f) drawCircle(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        listOf(pal.acc.copy(alpha = 0.55f * lit), pal.acc.copy(alpha = 0f)),
                        center = center, radius = 32.dp.toPx(),
                    ),
                    radius = 32.dp.toPx(),
                )
            }
            .clickable(onClick = onClick, indication = null, interactionSource = null),
        contentAlignment = Alignment.Center,
    ) {
        // 38 dp at rest, 50 under the finger: it stays inside the 70 dp rail and its 52 dp slot.
        GlassIcon(app, (38f + 12f * k).dp)
    }
}

/** A light buzz as the finger lands on a row. */
@Composable
fun rememberTick(): () -> Unit {
    val view = androidx.compose.ui.platform.LocalView.current
    return { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
}
