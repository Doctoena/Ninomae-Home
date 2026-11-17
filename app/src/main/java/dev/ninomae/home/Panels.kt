package dev.ninomae.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A folder opened: its name (editable) over its apps. Drag an app out to take it out. */
@Composable
fun FolderPanel(st: HomeState, ui: Ui, modifier: Modifier) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val folder = st.home.firstOrNull { it.id == ui.folderId } ?: return
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    val big = Rad.normal
    val j = Rad.joined

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Piece(Modifier.fillMaxWidth().height(56.dp), corners(big, big, j, j), edge = true) {
            Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Glyph("folder", size = 20.dp, color = pal.acc, fill = true)
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    name,
                    { name = it.take(24); st.renameFolder(folder.id, name) },
                    Modifier.weight(1f),
                    singleLine = true,
                    textStyle = TextStyle(color = pal.fg, fontSize = 18.sp, fontFamily = TitleFont),
                    cursorBrush = SolidColor(pal.acc),
                )
                Num("${folder.apps.size}", size = 13.sp, color = pal.sub)
                Spacer(Modifier.width(6.dp))
                IconButton("close") { ui.close() }
            }
        }
        Piece(Modifier.fillMaxWidth().weight(1f, fill = false), corners(j, j, big, big), edge = true) {
            LazyVerticalGrid(
                GridCells.Adaptive(92.dp),
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(folder.apps, key = { it }) { key ->
                    val app = st.app(key)
                    var rect by remember { mutableStateOf(Rect.Zero) }
                    Column(
                        Modifier
                            .onGloballyPositioned { rect = it.boundsInRoot() }
                            .clip(RoundedCornerShape(Rad.container))
                            .holdable(
                                onTap = { if (app != null) { Actions.open(ctx, st, app); ui.close() } },
                                onHold = { pos ->
                                    ui.hold = Hold(DragSrc(appKey = key, folderId = folder.id), pos)
                                    if (app != null) ui.showMenu(app, rect, above = false, fromDock = false, folderId = folder.id)
                                },
                            )
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AppIcon(app, 54.dp)
                        Spacer(Modifier.height(6.dp))
                        Lbl(app?.label ?: "Missing", Modifier.padding(horizontal = 2.dp), size = 12.sp, align = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/** Rename an app: empty puts its own name back. */
@Composable
fun RenamePanel(st: HomeState, ui: Ui, modifier: Modifier) {
    val pal = LocalPal.current
    val app = st.app(ui.renameKey) ?: return
    var text by remember(ui.openCount) { mutableStateOf(app.label) }
    val big = Rad.normal
    val j = Rad.joined
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Piece(Modifier.fillMaxWidth().height(60.dp), corners(big, big, j, j), edge = true) {
            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app, 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Title("Rename", size = 16.sp)
                    Lbl("Its own name is ${app.original}", size = 12.sp, color = pal.sub)
                }
            }
        }
        Piece(Modifier.fillMaxWidth().height(58.dp), RoundedCornerShape(j), edge = true) {
            Box(
                Modifier.fillMaxSize().padding(10.dp).clip(RoundedCornerShape(Rad.segment)).background(pal.l5).padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    text, { text = it.take(30) }, Modifier.fillMaxWidth(), singleLine = true,
                    textStyle = TextStyle(color = pal.fg, fontSize = 15.sp, fontFamily = Sans),
                    cursorBrush = SolidColor(pal.acc),
                )
            }
        }
        Piece(Modifier.fillMaxWidth().height(56.dp), corners(j, j, big, big), edge = true) {
            Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                PlainButton("Reset") { st.rename(app.key, null); ui.close() }
                Spacer(Modifier.weight(1f))
                PlainButton("Cancel") { ui.close() }
                Spacer(Modifier.width(6.dp))
                AccentButton("Save") { st.rename(app.key, text.takeIf { it != app.original }); ui.close() }
            }
        }
    }
}
