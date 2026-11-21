package dev.ninomae.home

import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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

/**
 * An open folder (A3 · Quiet): one small pane of frosted glass that grows out of the folder and hugs
 * its apps, up to four across, with the name as a quiet caption (tap it to rename) and ＋ to pick apps.
 * The folder's own icon lifts above the dim with a rose bar under it, and tapping it closes the folder.
 */
@Composable
fun FolderPopup(st: HomeState, ui: Ui, progress: () -> Float, lift: () -> Float, modifier: Modifier) {
    val pal = LocalPal.current
    val density = LocalDensity.current
    val folder = st.home.firstOrNull { it.id == ui.folderId && it.type == "folder" } ?: return
    val notes by NotifService.notes.collectAsState()
    val badged = remember(notes) { if (st.dots) notes.map { it.pkg }.toSet() else emptySet() }
    var adding by remember(folder.id) { mutableStateOf(false) }
    LaunchedEffect(ui.inFolder) { if (!ui.inFolder) adding = false }
    // Where it grows from: the folder's icon if it was tapped, else its cell.
    val cell = Rect(
        ui.gridRect.left + folder.x * ui.cellW, ui.gridRect.top + folder.y * ui.cellH,
        ui.gridRect.left + (folder.x + folder.w) * ui.cellW, ui.gridRect.top + (folder.y + folder.h) * ui.cellH,
    )
    val lifted = ui.folderIcon
    val icon = lifted ?: cell
    BoxWithConstraints(modifier) {
        val sw = constraints.maxWidth.toFloat()
        val sh = constraints.maxHeight.toFloat()
        val cols = folder.apps.size.coerceIn(2, 4)
        val cardW = if (adding) minOf(maxWidth - 32.dp, 360.dp) else 70.dp * cols + 16.dp
        val cardWpx = with(density) { cardW.toPx() }
        val margin = with(density) { 16.dp.toPx() }
        // Room for the lifted icon and its rose bar.
        val gap = with(density) { if (lifted != null) 18.dp.toPx() else 10.dp.toPx() }
        val topLimit = with(density) { 64.dp.toPx() }
        var cardH by remember { mutableFloatStateOf(0f) }
        val above = icon.top - gap - cardH > topLimit
        // Lined up with the folder's edge on its side of the screen, centred on it in the middle.
        val x = when {
            icon.center.x > sw * 0.62f -> icon.right + margin * 0.25f - cardWpx
            icon.center.x < sw * 0.38f -> icon.left - margin * 0.25f
            else -> icon.center.x - cardWpx / 2
        }.coerceIn(margin, sw - cardWpx - margin)
        val y = if (above) icon.top - gap - cardH else minOf(icon.bottom + gap * 1.4f, sh - cardH - margin)
        val shape = RoundedCornerShape(26.dp)
        val frosted = st.glass
        Column(
            Modifier
                .offset { IntOffset(x.toInt(), y.toInt()) }
                .width(cardW)
                .onSizeChanged { cardH = it.height.toFloat() }
                .graphicsLayer {
                    val e = progress()
                    alpha = e.coerceIn(0f, 1f)
                    val k = 0.82f + 0.18f * e
                    scaleX = k; scaleY = k
                    transformOrigin = TransformOrigin(((icon.center.x - x) / cardWpx).coerceIn(0f, 1f), if (above) 1f else 0f)
                }
                .clip(shape)
                .drawBehind {
                    // The wallpaper, frosted, lined up with where it really is behind the card.
                    if (frosted != null) {
                        val k = maxOf(sw / frosted.width, sh / frosted.height)
                        val dw = frosted.width * k
                        val dh = frosted.height * k
                        drawImage(
                            frosted,
                            dstOffset = IntOffset(((sw - dw) / 2 - x).toInt(), ((sh - dh) / 2 - y).toInt()),
                            dstSize = IntSize(dw.toInt(), dh.toInt()),
                            filterQuality = FilterQuality.High,
                        )
                    }
                }
                .background(if (frosted != null) pal.l0.copy(alpha = (0.42f * st.glassTint).coerceIn(0f, 0.95f)) else pal.l1.copy(alpha = 0.92f))
                .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(bottom = 8.dp),
        ) {
            FolderCaption(st, folder, adding) { adding = !adding }
            if (adding) FolderPicker(st, ui, folder)
            else Column(
                Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            ) {
                folder.apps.chunked(cols).forEach { row ->
                    Row {
                        row.forEach { key -> Box(Modifier.width(70.dp)) { FolderTile(st, ui, folder, key, badged) } }
                    }
                }
            }
        }

        // The folder itself, lifted over the dim with a rose bar under it. Tap it to close.
        if (lifted != null) {
            val w = with(density) { lifted.width.toDp() }
            val liftShape = iconShape(w)
            Box(
                Modifier
                    .offset { IntOffset(lifted.left.toInt(), lifted.top.toInt()) }
                    .graphicsLayer {
                        val e = lift()
                        val k = 1f + 0.1f * e
                        scaleX = k; scaleY = k
                        translationY = -4.dp.toPx() * e
                        shadowElevation = 14.dp.toPx() * e
                        this.shape = liftShape
                        clip = false
                    }
                    .clickable(interactionSource = null, indication = null) { ui.close() },
            ) { FolderIcon(st, folder, w) }
            Box(
                Modifier
                    .offset {
                        IntOffset((lifted.center.x - 11.dp.toPx()).toInt(), (lifted.bottom + 7.dp.toPx()).toInt())
                    }
                    .graphicsLayer { alpha = lift(); scaleX = lift() }
                    .size(22.dp, 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(pal.acc),
            )
        }
    }
}

/** The name as a small caption (tap it to rename), its count, and ＋ to pick apps. */
@Composable
private fun FolderCaption(st: HomeState, folder: HomeItem, adding: Boolean, onAdd: () -> Unit) {
    val pal = LocalPal.current
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    // Saved once, when the folder closes, rather than rewriting the whole home layout on every key.
    androidx.compose.runtime.DisposableEffect(folder.id) {
        onDispose { if (name != folder.name && st.home.any { it.id == folder.id }) st.renameFolder(folder.id, name) }
    }
    val caption = TextStyle(color = pal.sub, fontSize = 11.sp, fontFamily = Sans, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            name, { name = it.take(24) },
            Modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Min).widthIn(min = 24.dp, max = 180.dp),
            singleLine = true,
            textStyle = caption,
            cursorBrush = SolidColor(pal.acc),
            visualTransformation = Upper,
            decorationBox = { inner -> Box { if (name.isEmpty()) Lbl("FOLDER", size = 11.sp, color = pal.sub, weight = FontWeight.Bold); inner() } },
        )
        Lbl("  ·  ${folder.apps.size}", size = 11.sp, color = pal.acc, weight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(34.dp, 26.dp).clip(RoundedCornerShape(13.dp))
                .background(if (adding) pal.acc else Color.White.copy(alpha = 0.10f)).clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) { Glyph(if (adding) "check" else "add", size = 17.dp, color = if (adding) pal.ink else pal.acc) }
    }
}

/** Shows the folder's name in capitals while keeping what was typed. */
private object Upper : androidx.compose.ui.text.input.VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString) = androidx.compose.ui.text.input.TransformedText(
        androidx.compose.ui.text.AnnotatedString(text.text.uppercase().let { if (it.length == text.length) it else text.text }),
        androidx.compose.ui.text.input.OffsetMapping.Identity,
    )
}

/** One app in a folder: tap to open, hold for its menu or to drag it out. */
@Composable
private fun FolderTile(st: HomeState, ui: Ui, folder: HomeItem, key: String, badged: Set<String>) {
    val ctx = LocalContext.current
    val app = st.app(key)
    var rect by remember { mutableStateOf(Rect.Zero) }
    Column(
        Modifier.fillMaxWidth()
            .onGloballyPositioned { rect = it.boundsInRoot() }
            .clip(RoundedCornerShape(Rad.container))
            .pressScale()
            .holdable(
                onTap = { if (app != null) { Actions.open(ctx, st, app); ui.close() } },
                onHold = { pos ->
                    ui.hold = Hold(DragSrc(appKey = key, folderId = folder.id), pos)
                    if (app != null) ui.showMenu(app, rect, above = false, fromDock = false, folderId = folder.id)
                },
            )
            .padding(top = 6.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            if (app != null) GlassIcon(app, 48.dp) else AppIcon(null, 48.dp)
            if (app != null && app.pkg in badged) NoteBar()
        }
        Spacer(Modifier.height(6.dp))
        Lbl(app?.label ?: "Missing", Modifier.padding(horizontal = 3.dp), size = 11.sp, align = TextAlign.Center)
    }
}

/** Pick what's in the folder: every app, ticked if it's in. */
@Composable
private fun FolderPicker(st: HomeState, ui: Ui, folder: HomeItem) {
    val pal = LocalPal.current
    var q by remember { mutableStateOf("") }
    val apps = remember(st.apps, st.hidden, q) {
        st.apps.filter { it.key !in st.hidden && (q.isBlank() || it.label.contains(q.trim(), ignoreCase = true)) }
            .sortedBy { it.label.lowercase() }
    }
    Column(Modifier.padding(horizontal = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(21.dp)).background(Color.White.copy(alpha = 0.10f)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph("search", size = 18.dp, color = pal.acc)
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                q, { q = it }, Modifier.weight(1f), singleLine = true,
                textStyle = TextStyle(color = pal.fg, fontSize = 14.sp, fontFamily = Sans),
                cursorBrush = SolidColor(pal.acc),
                decorationBox = { inner -> Box { if (q.isEmpty()) Lbl("Find an app to add", size = 14.sp, color = pal.sub); inner() } },
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(apps, key = { it.key }) { app ->
                val inside = app.key in folder.apps
                Row(
                    Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (inside) pal.acc.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable {
                            if (inside) {
                                // A folder needs two apps; taking out the second-last turns it back into an app.
                                if (folder.apps.size <= 2) ui.close()
                                st.removeFromFolder(folder.id, app.key)
                            } else st.mergeInto(folder, app.key, null)
                        }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassIcon(app, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Lbl(app.label, Modifier.weight(1f))
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).background(if (inside) pal.acc else Color.White.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) { if (inside) Glyph("check", size = 15.dp, color = pal.ink) }
                }
            }
        }
    }
}

/**
 * Rename an app (R1a): one piece of glass sitting on the keyboard. Its icon sits in the field, its own
 * name is a chip to put it back, and the rose tick (or Enter) saves. Empty also puts its own name back.
 */
@Composable
fun RenamePanel(st: HomeState, ui: Ui, modifier: Modifier) {
    val pal = LocalPal.current
    val app = st.app(ui.renameKey) ?: return
    var text by remember(ui.openCount) {
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(app.label, androidx.compose.ui.text.TextRange(0, app.label.length)))
    }
    val focus = remember { FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(ui.openCount) { runCatching { focus.requestFocus() }; keyboard?.show() }
    fun save() { st.rename(app.key, text.text.trim().takeIf { it != app.original }); ui.close() }
    Column(
        modifier.frosted(st, RoundedCornerShape(28.dp), wash = 0.6f, edge = 0.2f)
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Title("Rename", Modifier.weight(1f), size = 15.sp, color = pal.sub)
            // Its own name, to put back.
            Row(
                Modifier.height(28.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.10f))
                    .clickable { text = androidx.compose.ui.text.input.TextFieldValue(app.original, androidx.compose.ui.text.TextRange(app.original.length)) }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph("undo", size = 15.dp, color = pal.acc)
                Spacer(Modifier.width(5.dp))
                Lbl(app.original, Modifier.widthIn(max = 150.dp), size = 12.sp)
                Spacer(Modifier.width(5.dp))
                Lbl("its own", size = 11.sp, color = pal.sub)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.10f))
                    .border(1.5.dp, pal.acc, RoundedCornerShape(18.dp)).padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIcon(app, 36.dp)
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    text, { text = it.copy(text = it.text.take(30)) },
                    Modifier.weight(1f).focusRequester(focus),
                    singleLine = true,
                    textStyle = TextStyle(color = pal.fg, fontSize = 17.sp, fontFamily = Sans),
                    cursorBrush = SolidColor(pal.acc),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { save() }),
                    decorationBox = { inner -> Box { if (text.text.isEmpty()) Lbl(app.original, size = 17.sp, color = pal.sub); inner() } },
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(pal.acc).pressScale().clickable { save() },
                contentAlignment = Alignment.Center,
            ) { Glyph("check", size = 26.dp, color = pal.ink) }
        }
    }
}
