package dev.ninomae.home

import android.app.Activity
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.SizeF
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import kotlin.math.ceil

/** Hosts other apps' Android widgets: binding, their setup screens, and drawing them. */
object AppWidgets {
    private const val HOST_ID = 1066
    const val REQ_BIND = 7101
    const val REQ_CONFIG = 7102

    private var host: AppWidgetHost? = null
    private var mgr: AppWidgetManager? = null
    private var activity: WeakReference<Activity>? = null

    private data class Pending(val id: Int, val info: AppWidgetProviderInfo, val screen: Int, val page: Int, val w: Int, val h: Int, val feed: Boolean)
    private var pending: Pending? = null

    fun init(a: Activity) {
        activity = WeakReference(a)
        if (host == null) {
            host = AppWidgetHost(a.applicationContext, HOST_ID)
            mgr = AppWidgetManager.getInstance(a.applicationContext)
        }
    }

    fun start() = runCatching { host?.startListening() }
    fun stop() = runCatching { host?.stopListening() }

    fun delete(id: Int) {
        if (id != 0) runCatching { host?.deleteAppWidgetId(id) }
    }

    fun info(id: Int): AppWidgetProviderInfo? = mgr?.getAppWidgetInfo(id)

    fun providers(): List<AppWidgetProviderInfo> = mgr?.installedProviders.orEmpty()

    /** Cells a widget wants, from its own target size or its minimum size. */
    fun spans(info: AppWidgetProviderInfo, ctx: Context, cellWdp: Float, cellHdp: Float, screen: Int): Pair<Int, Int> {
        if (info.targetCellWidth > 0 && info.targetCellHeight > 0) {
            // Targets assume a ~5-column phone grid; the inner screen has 8 roomier columns.
            return info.targetCellWidth.coerceAtMost(gridCols(screen)) to info.targetCellHeight.coerceAtMost(gridRows(screen))
        }
        val d = ctx.resources.displayMetrics.density
        val w = ceil(info.minWidth / d / cellWdp).toInt().coerceIn(1, gridCols(screen))
        val h = ceil(info.minHeight / d / cellHdp).toInt().coerceIn(1, gridRows(screen))
        return w to h
    }

    fun add(info: AppWidgetProviderInfo, screen: Int, page: Int, w: Int, h: Int, feed: Boolean = false) {
        val a = activity?.get() ?: return
        val hst = host ?: return
        val m = mgr ?: return
        val id = hst.allocateAppWidgetId()
        pending = Pending(id, info, screen, page, w, h, feed)
        if (m.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)) {
            afterBind()
        } else {
            val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
            @Suppress("DEPRECATION")
            a.startActivityForResult(i, REQ_BIND)
        }
    }

    private fun afterBind() {
        val p = pending ?: return
        val a = activity?.get() ?: return
        val optional = p.info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
        if (p.info.configure != null && !optional) {
            runCatching { host?.startAppWidgetConfigureActivityForResult(a, p.id, 0, REQ_CONFIG, null) }
                .onFailure { place() }
        } else {
            place()
        }
    }

    private fun place() {
        val p = pending ?: return
        val a = activity?.get() ?: return
        if (p.feed) HomeState.get(a).addFeedAndroidWidget(p.id, if (p.w * 2 <= gridCols(p.screen)) 1 else 2, p.h)
        else HomeState.get(a).addAndroidWidget(p.id, p.screen, p.page, p.w, p.h)
        pending = null
    }

    /** Called from the activity's onActivityResult. */
    fun onResult(requestCode: Int, resultCode: Int): Boolean {
        if (pending == null) return false
        when (requestCode) {
            REQ_BIND -> if (resultCode == Activity.RESULT_OK) afterBind() else cancel()
            REQ_CONFIG -> if (resultCode == Activity.RESULT_OK) place() else cancel()
            else -> return false
        }
        return true
    }

    private fun cancel() {
        pending?.let { delete(it.id) }
        pending = null
        activity?.get()?.let { Toast.makeText(it, "Widget not added", Toast.LENGTH_SHORT).show() }
    }

    fun createView(ctx: Context, id: Int) = host?.let { h ->
        info(id)?.let { h.createView(ctx, id, it) }
    }
}

/** An Android widget at its cell size. */
@Composable
fun AndroidWidget(id: Int, wDp: Float, hDp: Float, modifier: Modifier = Modifier) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val view = remember(id) { AppWidgets.createView(ctx, id) }
    if (view == null) {
        Piece(modifier.fillMaxSize(), RoundedCornerShape(Rad.normal)) {
            Lbl("Widget unavailable", Modifier.align(Alignment.Center), size = 13.sp, color = pal.sub)
        }
        return
    }
    AndroidView(
        factory = { view.apply { setPadding(0, 0, 0, 0) } },
        modifier = modifier.fillMaxSize(),
        update = { v -> runCatching { v.updateAppWidgetSize(Bundle(), listOf(SizeF(wDp, hDp))) } },
    )
}

private data class ProviderRow(val pkg: String, val app: AppEntry?, val label: String, val items: List<AppWidgetProviderInfo>)

/** Sizes offered for Ninomae's own widgets (the clock fits a 2-wide spot or a full row). */
private fun ninomaeSizes(kind: String, screen: Int): List<Pair<Int, Int>> =
    if (kind == "clock") listOf(2 to 1, gridCols(screen) to 1) else listOf(ninomaeSize(kind, screen))

/**
 * Widget picker (W2 · Apps first): full screen on the drawer's glass. A joined list of the apps
 * that have widgets, Ninomae first and open. Tap an app to open its widgets big, each with its
 * size chips and ＋; one app is open at a time.
 */
@Composable
fun WidgetPicker(st: HomeState, ui: Ui, progress: () -> Float, modifier: Modifier) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val screen = ui.screen
    val rows = remember(st.apps) {
        AppWidgets.providers().groupBy { it.provider.packageName }.map { (pkg, list) ->
            val app = st.byPkg[pkg]
            ProviderRow(pkg, app, app?.label ?: pkg, list.sortedBy { it.loadLabel(ctx.packageManager) })
        }.sortedBy { it.label.lowercase() }
    }
    var open by remember { mutableStateOf("ninomae") }
    LaunchedEffect(ui.layer) { if (ui.layer != Layer.Widgets) { open = "ninomae"; ui.pickForFeed = false } }
    val total = rows.size + 1

    Box(modifier) {
        GlassBackdrop(st, progress)
        LazyColumn(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    val e = FastOutSlowInEasing.transform(((progress() - 0.12f) / 0.88f).coerceIn(0f, 1f))
                    alpha = e
                    translationY = (1f - e) * 40.dp.toPx()
                }
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars))
                .padding(horizontal = 14.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "head") {
                Row(Modifier.fillMaxWidth().height(48.dp).padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Title("Widgets", Modifier.weight(1f), size = 30.sp)
                    GlassButton("close", 44.dp) { ui.close() }
                }
            }
            // Ninomae's own
            item(key = "ninomae") {
                AppHeader(
                    icon = { Box(Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(pal.acc), contentAlignment = Alignment.Center) { Glyph("widgets", size = 20.dp, color = pal.ink) } },
                    name = "Ninomae", count = "${ninomaeWidgets.size} widgets made for this home",
                    open = open == "ninomae", first = true, last = total == 1, tinted = true,
                ) { open = if (open == "ninomae") "" else "ninomae" }
            }
            if (open == "ninomae") item(key = "ninomae-list") {
                Expanded {
                    ninomaeWidgets.forEach { (kind, name, _) ->
                        Entry(name, ninomaeSizes(kind, screen), preview = { NinomaePreview(kind) }) { size ->
                            if (ui.pickForFeed) st.addFeedNinomae(kind) else st.addNinomaeWidget(kind, screen, maxOf(ui.page, 0), size)
                            ui.close()
                        }
                    }
                }
            }
            items(rows, key = { it.pkg }) { row ->
                val isOpen = open == row.pkg
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppHeader(
                        icon = { if (row.app != null) GlassIcon(row.app, 38.dp) else GlassBadge("widgets") },
                        name = row.label, count = if (row.items.size == 1) "1 widget" else "${row.items.size} widgets",
                        open = isOpen, first = false, last = row == rows.last() && !isOpen,
                    ) { open = if (isOpen) "" else row.pkg }
                    if (isOpen) Expanded(last = row == rows.last()) {
                        row.items.forEach { info ->
                            val span = AppWidgets.spans(info, ctx, ui.cellWdp, ui.cellHdp, screen)
                            Entry(info.loadLabel(ctx.packageManager), listOf(span), preview = { Preview(info) }) { (w, h) ->
                                ui.close()
                                AppWidgets.add(info, screen, maxOf(ui.page, 0), w, h, feed = ui.pickForFeed)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** An app in the list: its icon, name, how many widgets, and whether it's open. */
@Composable
private fun AppHeader(icon: @Composable () -> Unit, name: String, count: String, open: Boolean, first: Boolean, last: Boolean, tinted: Boolean = false, onClick: () -> Unit) {
    val pal = LocalPal.current
    val t = if (first) 22.dp else 6.dp
    val b = if (last) 22.dp else 6.dp
    val shape = corners(t, t, b, b)
    Row(
        Modifier.fillMaxWidth().height(60.dp).clip(shape)
            .background(if (tinted) pal.acc.copy(alpha = 0.14f) else glass(0.07f))
            .border(1.dp, glassEdge(), shape).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Lbl(name, size = 15.sp)
            Lbl(count, size = 11.sp, color = pal.sub)
        }
        Glyph(if (open) "expand_less" else "chevron_right", size = 20.dp, color = pal.dim)
    }
}

/** An open app's widgets, in their own piece under its row. */
@Composable
private fun Expanded(last: Boolean = false, content: @Composable () -> Unit) {
    val shape = corners(6.dp, 6.dp, if (last) 22.dp else 6.dp, if (last) 22.dp else 6.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(glass(0.07f)).border(1.dp, glassEdge(), shape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

/** One widget: its preview, its name, the sizes it can take (pick one) and ＋. */
@Composable
private fun Entry(name: String, sizes: List<Pair<Int, Int>>, preview: @Composable () -> Unit, onAdd: (Pair<Int, Int>) -> Unit) {
    val pal = LocalPal.current
    var chosen by remember { mutableStateOf(0) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(150.dp, 76.dp).clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.18f))) { preview() }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Lbl(name, size = 14.sp, weight = FontWeight.Medium, maxLines = 2)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                sizes.forEachIndexed { i, (w, h) ->
                    val on = i == chosen
                    Box(
                        Modifier.height(28.dp).clip(RoundedCornerShape(14.dp)).background(if (on) pal.acc else glass(0.10f))
                            .clickable { chosen = i }.padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) { Num("$w\u00d7$h", size = 11.sp, color = if (on) pal.ink else pal.fg) }
                }
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(glass(0.10f)).clickable { onAdd(sizes[chosen]) },
                    contentAlignment = Alignment.Center,
                ) { Glyph("add", size = 17.dp, color = pal.acc) }
            }
        }
    }
}

/** A small stand-in for each of Ninomae's widgets, in the widget's own look. */
@Composable
private fun NinomaePreview(kind: String) {
    val pal = LocalPal.current
    Box(Modifier.fillMaxSize().padding(10.dp)) {
        when (kind) {
            "clock" -> Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                Title(clockText(java.time.LocalDateTime.now()), size = 26.sp)
                Spacer(Modifier.width(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) { repeat(3) { Box(Modifier.size(40.dp, 10.dp).clip(RoundedCornerShape(4.dp)).background(glass(0.12f))) } }
            }
            "calendar" -> Column(Modifier.align(Alignment.CenterStart), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Lbl(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("MMMM")), size = 13.sp, weight = FontWeight.Black, family = TitleFont)
                repeat(3) { r -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { repeat(7) { c -> Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(if (r == 1 && c == 3) pal.acc else glass(0.14f))) } } }
            }
            "greeting" -> Column(Modifier.align(Alignment.CenterStart)) {
                Lbl("Good " + when (java.time.LocalTime.now().hour) { in 5..11 -> "morning"; in 12..17 -> "afternoon"; else -> "evening" }, size = 13.sp, weight = FontWeight.SemiBold)
                Lbl(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM")), size = 10.sp, color = pal.sub)
            }
            "player" -> Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(pal.acc.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) { Glyph("music_note", size = 20.dp, color = pal.fg) }
                Spacer(Modifier.width(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Box(Modifier.size(60.dp, 9.dp).clip(RoundedCornerShape(4.dp)).background(glass(0.2f))); Box(Modifier.size(40.dp, 8.dp).clip(RoundedCornerShape(4.dp)).background(glass(0.12f))) }
            }
            else -> Column(Modifier.align(Alignment.CenterStart), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Glyph(if (kind == "stack") "view_agenda" else "history", size = 18.dp, color = pal.acc)
                Box(Modifier.size(90.dp, 8.dp).clip(RoundedCornerShape(4.dp)).background(glass(0.14f)))
                Box(Modifier.size(60.dp, 8.dp).clip(RoundedCornerShape(4.dp)).background(glass(0.10f)))
            }
        }
    }
}

@Composable
private fun Preview(info: AppWidgetProviderInfo) {
    val ctx = LocalContext.current
    var img by remember(info.provider) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(info.provider) {
        img = withContext(Dispatchers.IO) {
            runCatching {
                val dpi = ctx.resources.displayMetrics.densityDpi
                (info.loadPreviewImage(ctx, dpi) ?: info.loadIcon(ctx, dpi))?.let { d ->
                    val w = d.intrinsicWidth.coerceIn(1, 600)
                    val h = d.intrinsicHeight.coerceIn(1, 600)
                    d.toBitmap(w, h).asImageBitmap()
                }
            }.getOrNull()
        }
    }
    img?.let {
        Image(it, null, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
    } ?: Lbl("…", Modifier.fillMaxWidth().padding(top = 40.dp), align = TextAlign.Center)
}
