package dev.ninomae.home

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** All apps: search with sort chips, pairs along the top, then every app. */
@Composable
fun Drawer(st: HomeState, ui: Ui, compact: Boolean, modifier: Modifier) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    var sort by remember { mutableIntStateOf(0) }
    val list = remember(st.apps, q, sort, st.launches) {
        val base = when (sort) {
            1 -> st.apps.sortedByDescending { st.launches[it.key] ?: 0 }
            2 -> st.apps.filter { it.isGame }
            else -> st.apps
        }
        if (q.isBlank()) base else {
            val t = q.trim()
            base.filter { it.label.contains(t, ignoreCase = true) || initials(it.label).startsWith(t, ignoreCase = true) }
                .sortedByDescending { it.label.startsWith(t, ignoreCase = true) }
        }
    }
    val big = Rad.normal
    val j = Rad.joined

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Piece(Modifier.fillMaxWidth().height(50.dp), corners(big, big, j, j), edge = true) {
            Row(Modifier.fillMaxHeight().padding(start = 15.dp, end = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Glyph("search", size = 20.dp, color = pal.sub)
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    q,
                    { q = it },
                    Modifier.weight(1f),
                    singleLine = true,
                    textStyle = TextStyle(color = pal.fg, fontSize = 15.sp, fontFamily = Sans),
                    cursorBrush = SolidColor(pal.acc),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        list.firstOrNull()?.let { Actions.open(ctx, st, it); ui.close() }
                    }),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (q.isEmpty()) Lbl("Search apps", size = 15.sp, color = pal.dim)
                            inner()
                        }
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Seg("A–Z", sort == 0) { sort = 0 }
                    if (!compact) Seg("Most used", sort == 1) { sort = 1 }
                    if (st.apps.any { it.isGame }) Seg("Games", sort == 2) { sort = 2 }
                }
            }
        }

        if (q.isBlank() && sort != 2) {
            Piece(Modifier.fillMaxWidth(), RoundedCornerShape(j), edge = true) {
                Column(Modifier.padding(vertical = 12.dp)) {
                    Title("Pairs", Modifier.padding(horizontal = 16.dp), size = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        st.pairs.forEach { p ->
                            var rect by remember { mutableStateOf(Rect.Zero) }
                            Tile(
                                p.name,
                                Modifier.onGloballyPositioned { rect = it.boundsInRoot() },
                                onLong = { ui.showPairMenu(p, rect, above = true) },
                                onTap = { ui.close(); Actions.openPair(ctx, st, p) },
                            ) { PairIcon(st.app(p.left), st.app(p.right), 50.dp) }
                        }
                        Tile("New", onTap = { ui.newPair() }) {
                            Box(
                                Modifier.size(50.dp).border(1.5.dp, pal.line, RoundedCornerShape(15.dp)),
                                contentAlignment = Alignment.Center,
                            ) { Glyph("add", size = 24.dp, color = pal.sub) }
                        }
                    }
                }
            }
        }

        Piece(Modifier.fillMaxWidth().weight(1f, fill = false), corners(j, j, big, big), edge = true) {
            if (list.isEmpty()) {
                Lbl(
                    if (sort == 2 && q.isBlank()) "No games found" else "Nothing matches “$q”",
                    Modifier.fillMaxWidth().padding(28.dp),
                    color = pal.sub,
                    align = TextAlign.Center,
                )
            } else {
                val screenH = LocalConfiguration.current.screenHeightDp
                LazyVerticalGrid(
                    GridCells.Adaptive(if (compact) 78.dp else 92.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(list, key = { it.key }) { app ->
                        var rect by remember { mutableStateOf(Rect.Zero) }
                        val density = LocalDensity.current
                        Tile(
                            app.label,
                            Modifier.onGloballyPositioned { rect = it.boundsInRoot() },
                            onLong = {
                                val mid = with(density) { (screenH.dp / 2).toPx() }
                                ui.showMenu(app, rect, above = rect.center.y > mid, fromDock = false)
                            },
                            onTap = { Actions.open(ctx, st, app); ui.close() },
                        ) { AppIcon(app, 54.dp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(
    label: String,
    modifier: Modifier = Modifier,
    onLong: (() -> Unit)? = null,
    onTap: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val pal = LocalPal.current
    Column(
        modifier
            .width(80.dp)
            .clip(RoundedCornerShape(Rad.container))
            .press(onLong = onLong, onTap = onTap)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        Spacer(Modifier.height(6.dp))
        Lbl(label, Modifier.padding(horizontal = 2.dp), size = 12.sp, color = pal.fg, align = TextAlign.Center)
    }
}

private fun initials(s: String) = s.split(' ', '-', '_').filter { it.isNotEmpty() }.joinToString("") { it.take(1) }
