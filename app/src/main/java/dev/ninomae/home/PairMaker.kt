package dev.ninomae.home

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Make or edit a pair: tap a side, pick its app, name it, save. */
@Composable
fun PairMaker(st: HomeState, ui: Ui, compact: Boolean, modifier: Modifier) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val editing = ui.pair
    val key = ui.openCount
    var name by remember(key) { mutableStateOf(editing?.name ?: "") }
    var left by remember(key) { mutableStateOf(editing?.left ?: ui.seedLeft) }
    var right by remember(key) { mutableStateOf(editing?.right) }
    var slot by remember(key) { mutableIntStateOf(if (left == null || editing != null) 0 else 1) }
    var openNow by remember(key) { mutableStateOf(editing == null) }
    val a = st.app(left)
    val b = st.app(right)
    val big = Rad.normal
    val j = Rad.joined

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // header + name
        Piece(Modifier.fillMaxWidth().height(56.dp), corners(big, big, j, j), edge = true) {
            Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Title(if (editing != null) "Edit pair" else "New pair", Modifier.weight(1f), size = 18.sp)
                Box(
                    Modifier.width(190.dp).height(36.dp).clip(RoundedCornerShape(Rad.segment)).background(pal.l5)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        name,
                        { name = it.take(18) },
                        Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = TextStyle(color = pal.fg, fontSize = 14.sp, fontFamily = Sans),
                        cursorBrush = SolidColor(pal.acc),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (name.isEmpty()) Lbl(autoName(a, b) ?: "Name it", size = 14.sp, color = pal.dim)
                                inner()
                            }
                        },
                    )
                }
            }
        }

        // preview of the two halves
        Piece(Modifier.fillMaxWidth(), RoundedCornerShape(j), edge = true) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).height(if (compact) 150.dp else 190.dp).clip(RoundedCornerShape(14.dp)).background(pal.l1).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Half(a, "Left", slot == 0) { slot = 0 }
                    Half(b, "Right", slot == 1) { slot = 1 }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.width(132.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Lbl("Sides", size = 12.sp, color = pal.sub)
                    PlainButton("Swap", "swap_horiz") { val t = left; left = right; right = t }
                    Spacer(Modifier.height(4.dp))
                    Lbl("Left opens first, then the right joins it.", size = 12.sp, color = pal.sub, maxLines = 3)
                }
            }
        }

        // app picker
        Piece(Modifier.fillMaxWidth().weight(1f, fill = false), RoundedCornerShape(j), edge = true) {
            Column(Modifier.padding(top = 12.dp)) {
                Title(if (slot == 0) "Pick the left app" else "Pick the right app", Modifier.padding(horizontal = 16.dp), size = 14.sp)
                LazyVerticalGrid(
                    GridCells.Adaptive(68.dp),
                    Modifier.height(230.dp),
                    contentPadding = PaddingValues(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(st.apps, key = { it.key }) { app ->
                        val side = when (app.key) { left -> "L"; right -> "R"; else -> null }
                        Column(
                            Modifier.clip(RoundedCornerShape(Rad.container)).clickable {
                                if (slot == 0) { left = app.key; if (right == null) slot = 1 } else right = app.key
                            }.padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box {
                                AppIcon(
                                    app, 44.dp,
                                    if (side != null) Modifier.border(2.dp, pal.acc, RoundedCornerShape(14.dp)).padding(3.dp) else Modifier.padding(3.dp),
                                )
                                if (side != null) {
                                    Box(
                                        Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp).size(18.dp).clip(CircleShape).background(pal.acc),
                                        contentAlignment = Alignment.Center,
                                    ) { Lbl(side, size = 10.sp, color = pal.ink, weight = FontWeight.Bold) }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Lbl(app.label, size = 11.sp, color = pal.sub, align = TextAlign.Center)
                        }
                    }
                }
            }
        }

        // footer
        Piece(Modifier.fillMaxWidth().height(58.dp), corners(j, j, big, big), edge = true) {
            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                MiniSwitch(openNow) { openNow = !openNow }
                Spacer(Modifier.width(10.dp))
                Lbl("Open it now", Modifier.weight(1f))
                if (editing != null) {
                    PlainButton("Delete") { st.deletePair(editing.id); ui.close() }
                    Spacer(Modifier.width(6.dp))
                }
                PlainButton("Cancel") { ui.close() }
                Spacer(Modifier.width(6.dp))
                AccentButton("Save pair", enabled = a != null && b != null && left != right) {
                    val p = AppPair(editing?.id ?: st.newPairId(), name.ifBlank { autoName(a, b) ?: "Pair" }, left!!, right!!)
                    st.savePair(p)
                    ui.close()
                    if (openNow) Actions.openPair(ctx, st, p)
                }
            }
        }
    }
}

@Composable
private fun RowScope.Half(app: AppEntry?, side: String, selected: Boolean, onClick: () -> Unit) {
    val pal = LocalPal.current
    val bg = app?.let { mix(pal.l3, it.tint, 0.32f) } ?: pal.l3
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .border(2.dp, if (selected) pal.acc else Color.Transparent, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (app != null) AppIcon(app, 48.dp) else Glyph("add", size = 30.dp, color = pal.sub)
            Spacer(Modifier.height(8.dp))
            Lbl(app?.label ?: "Choose", Modifier.padding(horizontal = 6.dp), size = 13.sp, align = TextAlign.Center)
            Lbl(side, size = 11.sp, color = if (selected) pal.acc else pal.sub)
        }
    }
}

private fun autoName(a: AppEntry?, b: AppEntry?): String? {
    if (a == null || b == null) return null
    fun short(s: String) = s.substringBefore(' ').take(8)
    return "${short(a.label)} & ${short(b.label)}"
}
