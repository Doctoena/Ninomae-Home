package dev.ninomae.home

import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.Settings
import android.widget.Toast
import kotlin.math.roundToInt

/** What the buttons do. Anything that needs a permission explains itself and opens the right settings page. */
object Actions {
    private val main = Handler(Looper.getMainLooper())

    private fun la(ctx: Context) = ctx.getSystemService(LauncherApps::class.java)

    private fun start(ctx: Context, i: Intent) {
        try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(ctx, "Nothing on this phone opens that", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(ctx, "Android didn't let that open", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toast(ctx: Context, s: String) = Toast.makeText(ctx, s, Toast.LENGTH_LONG).show()

    fun open(ctx: Context, st: HomeState, app: AppEntry) {
        runCatching { la(ctx).startMainActivity(app.component, app.user, null, null) }
            .onFailure { toast(ctx, "Couldn't open ${app.label}") }
        st.noteLaunch(app.key)
    }

    /** A floating window. Samsung opens it as a pop-up window when the app allows resizing. */
    fun window(ctx: Context, st: HomeState, app: AppEntry) {
        val dm = ctx.resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        val r = Rect(w / 8, h / 6, w * 7 / 8, h * 5 / 6)
        val opts = ActivityOptions.makeBasic().setLaunchBounds(r).toBundle()
        runCatching { la(ctx).startMainActivity(app.component, app.user, r, opts) }
            .onFailure { toast(ctx, "Couldn't open ${app.label} in a window") }
        st.noteLaunch(app.key)
    }

    /**
     * Split screen: open [first], ask the system to split, then open [second] beside it.
     * With no second app, Samsung shows its own picker for the other half.
     */
    fun split(ctx: Context, st: HomeState, first: AppEntry, second: AppEntry?) {
        val svc = SplitService.instance
        if (svc == null) {
            toast(ctx, "Turn on Ninomae Home in Accessibility to use split screen")
            openAccessibility(ctx)
            return
        }
        open(ctx, st, first)
        main.postDelayed({ svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN) }, 650)
        if (second != null) {
            main.postDelayed({
                svc.launchAdjacent(second)
                st.noteLaunch(second.key)
            }, 1500)
        }
    }

    fun openPair(ctx: Context, st: HomeState, p: AppPair) {
        val a = st.app(p.left)
        val b = st.app(p.right)
        if (a == null || b == null) {
            toast(ctx, "An app in ${p.name} isn't installed any more")
            return
        }
        st.setActive(p.id)
        split(ctx, st, a, b)
    }

    fun info(ctx: Context, app: AppEntry) {
        runCatching { la(ctx).startAppDetailsActivity(app.component, app.user, null, null) }
    }

    fun uninstall(ctx: Context, app: AppEntry) =
        start(ctx, Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}")))

    // ---------- system ----------

    fun global(ctx: Context, action: Int, delay: Long = 0) {
        val svc = SplitService.instance
        if (svc == null) {
            val switchedOn = (Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "").contains(ctx.packageName)
            toast(ctx, if (switchedOn) "Android disconnected Ninomae Home's accessibility. Switch it off and on again." else "Turn on Ninomae Home in Accessibility for this")
            openAccessibility(ctx)
            return
        }
        main.postDelayed({ svc.performGlobalAction(action) }, delay)
    }

    fun webSearch(ctx: Context, q: String) =
        start(ctx, Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, q))

    fun playStore(ctx: Context, q: String) =
        start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(q))))

    fun wifi(ctx: Context) = start(ctx, Intent(Settings.Panel.ACTION_WIFI))
    fun location(ctx: Context) = start(ctx, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    fun bluetooth(ctx: Context) = start(ctx, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    fun androidSettings(ctx: Context) = start(ctx, Intent(Settings.ACTION_SETTINGS))
    fun calendar(ctx: Context) = start(
        ctx,
        Intent(Intent.ACTION_VIEW, android.provider.CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
            .appendPath(System.currentTimeMillis().toString()).build()),
    )
    fun battery(ctx: Context) = start(ctx, Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
    fun clock(ctx: Context) = start(ctx, Intent(AlarmClock.ACTION_SHOW_ALARMS))
    fun wallpaper(ctx: Context) = start(ctx, Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper"))
    fun homeSettings(ctx: Context) = start(ctx, Intent(Settings.ACTION_HOME_SETTINGS))
    fun openAccessibility(ctx: Context) = start(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    fun openListenerSettings(ctx: Context) = start(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    fun openWriteSettings(ctx: Context) =
        start(ctx, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")))

    fun toggleTorch(ctx: Context) {
        val id = Status.torchId ?: return toast(ctx, "No flashlight found")
        runCatching { ctx.getSystemService(android.hardware.camera2.CameraManager::class.java).setTorchMode(id, !Status.torch) }
    }

    fun toggleDnd(ctx: Context) {
        val want = if (Status.dnd) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY
        val listener = NotifService.instance
        val nm = ctx.getSystemService(NotificationManager::class.java)
        when {
            listener != null -> listener.requestInterruptionFilter(want)
            nm.isNotificationPolicyAccessGranted -> nm.setInterruptionFilter(want)
            else -> {
                toast(ctx, "Allow notification access to switch Do not disturb")
                openListenerSettings(ctx)
            }
        }
    }

    fun toggleRotate(ctx: Context) {
        if (!Settings.System.canWrite(ctx)) {
            toast(ctx, "Allow Ninomae Home to change system settings for auto-rotate")
            return openWriteSettings(ctx)
        }
        Settings.System.putInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (Status.rotate) 0 else 1)
    }

    fun setVolume(ctx: Context, v: Float) {
        val am = ctx.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, (v * max).roundToInt(), 0)
        Status.volume = v
    }

    fun setBrightness(ctx: Context, v: Float) {
        if (!Settings.System.canWrite(ctx)) return
        val cr = ctx.contentResolver
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, (v * 255).roundToInt().coerceIn(1, 255))
        Status.brightness = v
    }
}
