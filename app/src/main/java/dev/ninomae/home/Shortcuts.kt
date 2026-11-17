package dev.ninomae.home

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Bundle
import android.os.UserManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** App shortcuts (long-press actions like "New tab"). Android only gives them to the default home app. */
object Shortcuts {
    private fun la(ctx: Context) = ctx.getSystemService(LauncherApps::class.java)
    private val icons = HashMap<String, ImageBitmap?>()

    fun allowed(ctx: Context): Boolean = runCatching { la(ctx).hasShortcutHostPermission() }.getOrDefault(false)

    fun forApp(ctx: Context, app: AppEntry): List<ShortcutInfo> {
        if (!allowed(ctx)) return emptyList()
        val q = LauncherApps.ShortcutQuery()
            .setPackage(app.pkg)
            .setActivity(app.component)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
        return runCatching { la(ctx).getShortcuts(q, app.user) }.getOrNull().orEmpty()
            .filter { it.isEnabled }
            .sortedWith(compareBy({ !it.isDeclaredInManifest }, { it.rank }))
            .take(4)
    }

    fun find(ctx: Context, pkg: String, id: String, serial: Long): ShortcutInfo? {
        if (!allowed(ctx)) return null
        val user = ctx.getSystemService(UserManager::class.java).getUserForSerialNumber(serial) ?: return null
        val q = LauncherApps.ShortcutQuery()
            .setPackage(pkg)
            .setShortcutIds(listOf(id))
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST,
            )
        return runCatching { la(ctx).getShortcuts(q, user) }.getOrNull()?.firstOrNull()
    }

    fun icon(ctx: Context, s: ShortcutInfo): ImageBitmap? = icons.getOrPut("${s.`package`}/${s.id}") {
        runCatching {
            val px = (64 * ctx.resources.displayMetrics.density).toInt()
            la(ctx).getShortcutIconDrawable(s, ctx.resources.displayMetrics.densityDpi)?.toBitmap(px, px)?.asImageBitmap()
        }.getOrNull()
    }

    fun start(ctx: Context, s: ShortcutInfo) {
        runCatching { la(ctx).startShortcut(s, null, null) }
            .onFailure { Toast.makeText(ctx, "Couldn't open that shortcut", Toast.LENGTH_SHORT).show() }
    }

    fun start(ctx: Context, pkg: String, id: String, serial: Long) {
        val s = find(ctx, pkg, id, serial)
        if (s == null) Toast.makeText(ctx, "That shortcut is gone", Toast.LENGTH_SHORT).show() else start(ctx, s)
    }
}

/** Another app asked to put a shortcut on the home screen (a website, a contact, a playlist). */
class PinRequestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val la = getSystemService(LauncherApps::class.java)
        val req = runCatching { la.getPinItemRequest(intent) }.getOrNull()
        val info = req?.shortcutInfo
        if (req != null && req.isValid && req.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT && info != null && req.accept()) {
            val st = HomeState.get(this)
            val serial = getSystemService(UserManager::class.java).getSerialNumberForUser(info.userHandle)
            st.addShortcut(info.id, info.`package`, serial, (info.shortLabel ?: info.longLabel ?: "Shortcut").toString(), st.lastScreen)
            Toast.makeText(this, "Added to your home screen", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
