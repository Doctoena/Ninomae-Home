package dev.ninomae.home

import android.app.Activity
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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

    private data class Pending(val id: Int, val info: AppWidgetProviderInfo, val screen: Int, val page: Int, val w: Int, val h: Int)
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

    fun add(info: AppWidgetProviderInfo, screen: Int, page: Int, w: Int, h: Int) {
        val a = activity?.get() ?: return
        val hst = host ?: return
        val m = mgr ?: return
        val id = hst.allocateAppWidgetId()
        pending = Pending(id, info, screen, page, w, h)
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
        HomeState.get(a).addAndroidWidget(p.id, p.screen, p.page, p.w, p.h)
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

/** Widget picker: Ninomae's own first, then every app's, each with its preview. */
@Composable
fun WidgetPicker(st: HomeState, ui: Ui, modifier: Modifier) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val screen = ui.screen
    val rows = remember(st.apps) {
        AppWidgets.providers().groupBy { it.provider.packageName }.map { (pkg, list) ->
            val app = st.byPkg[pkg]
            ProviderRow(pkg, app, app?.label ?: pkg, list.sortedBy { it.loadLabel(ctx.packageManager) })
        }.sortedBy { it.label.lowercase() }
    }
    val big = Rad.normal
    val j = Rad.joined

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Piece(Modifier.fillMaxWidth().height(58.dp), corners(big, big, j, j), edge = true) {
            Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Title("Widgets", Modifier.weight(1f), size = 18.sp)
                IconButton("close") { ui.close() }
            }
        }
        Piece(Modifier.fillMaxWidth().weight(1f, fill = false), corners(j, j, big, big), edge = true) {
            LazyColumn(Modifier.padding(vertical = 6.dp)) {
                item {
                    Section("Ninomae", null) {
                        ninomaeWidgets.forEach { (kind, name, about) ->
                            Choice(name, "${ninomaeSize(kind, screen).first}×${ninomaeSize(kind, screen).second}", about, preview = {
                                Box(Modifier.fillMaxSize().background(pal.l1), contentAlignment = Alignment.Center) {
                                    Glyph(
                                        when (kind) { "stack" -> "view_agenda"; "greeting" -> "waving_hand"; "player" -> "music_note"; "jump" -> "history"; else -> "schedule" },
                                        size = 34.dp, color = pal.acc,
                                    )
                                }
                            }) {
                                st.addNinomaeWidget(kind, screen, ui.page)
                                ui.close()
                            }
                        }
                    }
                }
                items(rows, key = { it.pkg }) { row ->
                    Section(row.label, row.app) {
                        row.items.forEach { info ->
                            val (w, h) = AppWidgets.spans(info, ctx, ui.cellWdp, ui.cellHdp, screen)
                            Choice(info.loadLabel(ctx.packageManager), "$w×$h", null, preview = { Preview(info) }) {
                                ui.close()
                                AppWidgets.add(info, screen, ui.page, w, h)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, app: AppEntry?, content: @Composable () -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (app != null) {
                AppIcon(app, 22.dp)
                Spacer(Modifier.width(8.dp))
            }
            Title(title, size = 14.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) { content() }
    }
}

@Composable
private fun Choice(name: String, size: String, about: String?, preview: @Composable () -> Unit, onClick: () -> Unit) {
    val pal = LocalPal.current
    Column(
        Modifier.width(168.dp).clip(RoundedCornerShape(Rad.container)).clickable(onClick = onClick).padding(4.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(Rad.container)).background(pal.l1)) { preview() }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Lbl(name, Modifier.weight(1f), size = 13.sp)
            Num(size, size = 12.sp, color = pal.sub)
        }
        if (about != null) Lbl(about, size = 11.sp, color = pal.sub, maxLines = 2)
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
