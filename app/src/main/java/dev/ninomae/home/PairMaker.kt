package dev.ninomae.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Make or edit a pair, full screen on the drawer's glass. The two halves big at the top (tap one to
 * pick its app, the round button between them swaps), then the name and "Open now" joined in one
 * strip, the apps (most used first) and, at the thumb, search and Save.
 */
@Composable
fun PairMaker(st: HomeState, ui: Ui, compact: Boolean, progress: () -> Float, modifier: Modifier) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val editing = ui.pair
    val key = ui.openCount
    var name by remember(key) { mutableStateOf(editing?.name ?: "") }
    var left by remember(key) { mutableStateOf(editing?.left ?: ui.seedLeft) }
    var right by remember(key) { mutableStateOf(editing?.right) }
    var slot by remember(key) { mutableIntStateOf(if (left == null || editing != null) 0 else 1) }
    var openNow by remember(key) { mutableStateOf(editing == null) }
    var q by remember(key) { mutableStateOf("") }
    val a = st.app(left)
    val b = st.app(right)
    val apps = remember(st.apps, st.hidden, st.launches, q) {
        val t = q.trim()
        st.apps.filter { it.key !in st.hidden && (t.isEmpty() || it.label.contains(t, ignoreCase = true)) }
            .sortedWith(compareByDescending<AppEntry> { st.launches[it.key] ?: 0 }.thenBy { it.label.lowercase() })
    }
    val canSave = a != null && b != null && left != right
    fun save() {
        if (!canSave) return
        val p = AppPair(editing?.id ?: st.newPairId(), name.ifBlank { autoName(a, b) ?: "Pair" }, left!!, right!!)
        st.savePair(p)
        ui.close()
        if (openNow) Actions.openPair(ctx, st, p)
    }
    val big = 22.dp
    val j = 6.dp

    Box(modifier) {
        GlassBackdrop(st, progress)
        Column(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    val e = FastOutSlowInEasing.transform(((progress() - 0.12f) / 0.88f).coerceIn(0f, 1f))
                    alpha = e
                    translationY = (1f - e) * 40.dp.toPx()
                }
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(horizontal = 16.dp),
        ) {
            // ---------- title ----------
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Title(if (editing != null) "Edit pair" else "New pair", Modifier.weight(1f), size = 30.sp)
                if (editing != null) {
                    GlassButton("delete", 44.dp) { st.deletePair(editing.id); ui.close() }
                    Spacer(Modifier.width(8.dp))
                }
                GlassButton("close", 44.dp) { ui.close() }
            }

            // ---------- the two halves (P1) ----------
            Box(Modifier.fillMaxWidth().height(if (compact) 150.dp else 170.dp)) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Half(a, "Left · opens first", slot == 0, corners(26.dp, j, j, 26.dp)) { slot = 0 }
                    Half(b, "Right · joins it", slot == 1, corners(j, 26.dp, 26.dp, j)) { slot = 1 }
                }
                Box(
                    Modifier.align(Alignment.Center).size(40.dp).clip(CircleShape).background(pal.l0.copy(alpha = 0.9f))
                        .clickable { val t = left; left = right; right = t },
                    contentAlignment = Alignment.Center,
                ) { Glyph("swap_horiz", size = 20.dp, color = pal.acc) }
            }
            Spacer(Modifier.height(12.dp))

            // ---------- name | open now, joined (P1a) ----------
            Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.weight(3f).fillMaxHeight().clip(corners(big, j, j, big)).background(glass(0.07f))
                        .border(1.dp, glassEdge(), corners(big, j, j, big)).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph("edit", size = 17.dp, color = pal.acc)
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        name, { name = it.take(18) }, Modifier.weight(1f), singleLine = true,
                        textStyle = TextStyle(color = pal.fg, fontSize = 14.sp, fontFamily = Sans),
                        cursorBrush = SolidColor(pal.acc),
                        decorationBox = { inner -> Box { if (name.isEmpty()) Lbl(autoName(a, b) ?: "Name it", size = 14.sp, color = pal.sub); inner() } },
                    )
                }
                Row(
                    Modifier.weight(2f).fillMaxHeight().clip(corners(j, big, big, j)).background(glass(0.07f))
                        .border(1.dp, glassEdge(), corners(j, big, big, j)).clickable { openNow = !openNow }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Lbl("Open now", Modifier.weight(1f), size = 14.sp)
                    MiniSwitch(openNow) { openNow = !openNow }
                }
            }
            Spacer(Modifier.height(18.dp))

            // ---------- the apps ----------
            Row(Modifier.padding(start = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
                Title(if (slot == 0) "Left side" else "Right side", size = 18.sp)
                Spacer(Modifier.width(10.dp))
                Lbl("most used first", Modifier.padding(bottom = 2.dp), size = 13.sp, color = pal.sub)
            }
            LazyVerticalGrid(
                GridCells.Fixed(if (compact) 4 else 6),
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(apps, key = { it.key }) { app ->
                    val side = when (app.key) { left -> "L"; right -> "R"; else -> null }
                    Column(
                        Modifier.clip(RoundedCornerShape(Rad.container)).pressScale().clickable {
                            if (slot == 0) { left = app.key; if (right == null) slot = 1 } else right = app.key
                        }.padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box {
                            GlassIcon(app, 52.dp)
                            if (side != null) Box(
                                Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp).size(20.dp).clip(CircleShape).background(pal.acc),
                                contentAlignment = Alignment.Center,
                            ) { Lbl(side, size = 10.sp, color = pal.ink, weight = FontWeight.Bold) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Lbl(app.label, Modifier.padding(horizontal = 2.dp), size = 12.sp, align = TextAlign.Center)
                    }
                }
            }

            // ---------- search and Save, at the thumb ----------
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp).height(60.dp).clip(RoundedCornerShape(30.dp)).background(glass(0.12f))
                    .border(1.dp, glassEdge(), RoundedCornerShape(30.dp)).padding(start = 18.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph("search", size = 20.dp, color = pal.acc)
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    q, { q = it }, Modifier.weight(1f), singleLine = true,
                    textStyle = TextStyle(color = pal.fg, fontSize = 15.sp, fontFamily = Sans),
                    cursorBrush = SolidColor(pal.acc),
                    decorationBox = { inner -> Box { if (q.isEmpty()) Lbl("Find an app", size = 15.sp, color = pal.sub); inner() } },
                )
                if (q.isNotEmpty()) GlassButton("close", 34.dp) { q = "" }
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(if (canSave) pal.acc else glass(0.10f))
                        .clickable(enabled = canSave) { save() }.padding(horizontal = 22.dp),
                    contentAlignment = Alignment.Center,
                ) { Title("Save", size = 16.sp, color = if (canSave) pal.ink else pal.dim) }
            }
        }
    }
}

/** One half of the pair: its app big, or ＋ to choose. Lit when it's the side being picked. */
@Composable
private fun RowScope.Half(app: AppEntry?, note: String, selected: Boolean, shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit) {
    val pal = LocalPal.current
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(shape)
            .background(glass(0.07f))
            .background(if (selected) pal.acc.copy(alpha = 0.08f) else Color.Transparent)
            .border(if (selected) 2.dp else 1.dp, if (selected) pal.acc else glassEdge(), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (app != null) GlassIcon(app, 60.dp) else Box(
                Modifier.size(60.dp).clip(RoundedCornerShape(20.dp)).border(1.5.dp, glassEdge(), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) { Glyph("add", size = 28.dp, color = pal.acc) }
            Spacer(Modifier.height(10.dp))
            Title(app?.label ?: "Choose", Modifier.padding(horizontal = 8.dp), size = 17.sp)
            Spacer(Modifier.height(2.dp))
            Lbl(note, size = 12.sp, color = if (selected) pal.acc else pal.sub)
        }
    }
}

private fun autoName(a: AppEntry?, b: AppEntry?): String? {
    if (a == null || b == null) return null
    fun short(s: String) = s.substringBefore(' ').take(8)
    return "${short(a.label)} & ${short(b.label)}"
}
