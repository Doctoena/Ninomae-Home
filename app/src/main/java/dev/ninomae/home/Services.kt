package dev.ninomae.home

import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.accessibility.AccessibilityEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Accessibility service: the only way a home app can put apps into split screen,
 * plus lock, screenshot, power menu and Samsung's quick panel. It reads nothing on screen.
 */
class SplitService : AccessibilityService() {
    companion object {
        @Volatile var instance: SplitService? = null
            private set
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    /** Started from the service, which is allowed to start activities while another app is on top. */
    fun launchAdjacent(app: AppEntry) {
        val i = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        runCatching { startActivity(i) }
    }
}

data class Note(
    val key: String,
    val pkg: String,
    val app: String,
    val title: String,
    val text: String,
    val time: Long,
    val autoCancel: Boolean,
    val icon: ImageBitmap?,
    val intent: PendingIntent?,
    val actions: List<Pair<String, PendingIntent>>,
)

data class MediaNow(
    val title: String,
    val artist: String,
    val app: String,
    val art: ImageBitmap?,
    val tint: Color?,
    val playing: Boolean,
    val position: Long,
    val updatedAt: Long,
    val speed: Float,
    val duration: Long,
    val controller: MediaController,
) {
    fun positionNow(): Long =
        if (!playing) position else (position + (SystemClock.elapsedRealtime() - updatedAt) * speed).toLong()
            .coerceAtMost(if (duration > 0) duration else Long.MAX_VALUE)
}

class NotifService : NotificationListenerService() {
    companion object {
        @Volatile var instance: NotifService? = null
            private set
        val notes = MutableStateFlow<List<Note>>(emptyList())

        fun open(ctx: Context, n: Note) {
            n.intent?.let { sendPending(ctx, it) }
            if (n.autoCancel) instance?.cancelNotification(n.key)
        }

        fun dismiss(n: Note) {
            instance?.cancelNotification(n.key)
        }

        fun clearAll() {
            instance?.cancelAllNotifications()
        }

        fun sendPending(ctx: Context, p: PendingIntent) {
            runCatching {
                val opts = ActivityOptions.makeBasic()
                if (Build.VERSION.SDK_INT >= 34) {
                    @Suppress("DEPRECATION")
                    opts.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                }
                p.send(ctx, 0, null, null, null, null, opts.toBundle())
            }
        }
    }

    override fun onListenerConnected() {
        instance = this
        refresh()
        Media.attach(this)
    }

    override fun onListenerDisconnected() {
        instance = null
        notes.value = emptyList()
        Media.detach()
    }

    private val icons = HashMap<String, ImageBitmap?>()

    /** The app's own icon, for apps that have none in the launcher (watch managers, system services). */
    private fun iconFor(pkg: String): ImageBitmap? = icons.getOrPut(pkg) {
        runCatching {
            val px = (64 * resources.displayMetrics.density).toInt()
            packageManager.getApplicationIcon(pkg).toBitmap(px, px).asImageBitmap()
        }.getOrNull()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        val list = runCatching { activeNotifications }.getOrNull() ?: return
        val pm = packageManager
        notes.value = list.asSequence()
            .filter { it.isClearable && !it.isOngoing }
            .filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            // Samsung's "Now Bar" items (System UI's MediaOngoingActivity when music starts, and the like) aren't
            // flagged ongoing, but carry android.ongoingActivityNoti.* extras. They're not meant to be read.
            .filter { sbn -> sbn.notification.extras.keySet().none { it.startsWith("android.ongoingActivityNoti") } }
            .map { sbn ->
                val n = sbn.notification
                val ex = n.extras
                val app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString() }
                    .getOrDefault(sbn.packageName)
                Note(
                    key = sbn.key,
                    pkg = sbn.packageName,
                    app = app,
                    title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: app,
                    text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))
                        ?.toString()?.trim() ?: "",
                    time = sbn.postTime,
                    autoCancel = n.flags and Notification.FLAG_AUTO_CANCEL != 0,
                    icon = iconFor(sbn.packageName),
                    intent = n.contentIntent,
                    actions = n.actions.orEmpty()
                        .filter { it.remoteInputs.isNullOrEmpty() && it.actionIntent != null && !it.title.isNullOrBlank() }
                        .map { it.title.toString() to it.actionIntent },
                )
            }
            .sortedByDescending { it.time }
            .toList()
    }
}

/** The player: whichever media session is playing, through the notification listener's access. */
object Media {
    val now = MutableStateFlow<MediaNow?>(null)
    private var msm: MediaSessionManager? = null
    private var current: MediaController? = null
    private var appCtx: Context? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = update()
        override fun onPlaybackStateChanged(state: PlaybackState?) = update()
        override fun onSessionDestroyed() {
            current = null
            now.value = null
        }
    }

    fun attach(ctx: Context) {
        appCtx = ctx.applicationContext
        val m = ctx.getSystemService(MediaSessionManager::class.java)
        msm = m
        val cn = ComponentName(ctx, NotifService::class.java)
        runCatching {
            m.addOnActiveSessionsChangedListener(sessionsListener, cn)
            pick(m.getActiveSessions(cn))
        }
    }

    fun detach() {
        runCatching { msm?.removeOnActiveSessionsChangedListener(sessionsListener) }
        current?.unregisterCallback(callback)
        current = null
        now.value = null
    }

    private fun pick(list: List<MediaController>?) {
        val c = list.orEmpty().firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.orEmpty().firstOrNull()
        if (c?.sessionToken != current?.sessionToken) {
            current?.unregisterCallback(callback)
            current = c
            c?.registerCallback(callback)
        }
        update()
    }

    private var lastArt: Bitmap? = null
    private var lastArtImage: ImageBitmap? = null
    private var lastTint: Color? = null

    private fun update() {
        val c = current ?: run { now.value = null; return }
        val md = c.metadata
        val st = c.playbackState
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (title.isNullOrBlank()) { now.value = null; return }
        val art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (art !== lastArt) {
            lastArt = art
            lastArtImage = art?.asImageBitmap()
            lastTint = art?.let { averageColour(it.copy(Bitmap.Config.ARGB_8888, false)) }
        }
        val pm = appCtx?.packageManager
        val app = runCatching { pm!!.getApplicationLabel(pm.getApplicationInfo(c.packageName, 0)).toString() }
            .getOrDefault(c.packageName)
        now.value = MediaNow(
            title = title,
            artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "",
            app = app,
            art = lastArtImage,
            tint = lastTint,
            playing = st?.state == PlaybackState.STATE_PLAYING,
            position = st?.position ?: 0,
            updatedAt = st?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(),
            speed = st?.playbackSpeed ?: 1f,
            duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
            controller = c,
        )
    }

    fun toggle() {
        val m = now.value ?: return
        if (m.playing) m.controller.transportControls.pause() else m.controller.transportControls.play()
    }

    fun next() = now.value?.controller?.transportControls?.skipToNext()
    fun prev() = now.value?.controller?.transportControls?.skipToPrevious()
    fun seek(ms: Long) = now.value?.controller?.transportControls?.seekTo(ms)
}
