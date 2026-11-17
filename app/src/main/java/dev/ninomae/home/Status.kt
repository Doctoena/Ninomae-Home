package dev.ninomae.home

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/** Battery, Wi-Fi, Bluetooth, torch, Do not disturb, rotation, volume and brightness, live while home is showing. */
object Status {
    var battery by mutableIntStateOf(0)
    var charging by mutableStateOf(false)
    var wifi by mutableStateOf(false)
    var bt by mutableStateOf(false)
    var torch by mutableStateOf(false)
    var location by mutableStateOf(false)
    var dnd by mutableStateOf(false)
    var rotate by mutableStateOf(false)
    var volume by mutableFloatStateOf(0f)
    var brightness by mutableFloatStateOf(0f)

    var torchId: String? = null
        private set

    private val main = Handler(Looper.getMainLooper())
    private var started = false
    private var cm: ConnectivityManager? = null
    private var cam: CameraManager? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                    val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                    battery = level * 100 / scale
                    val s = i.getIntExtra(BatteryManager.EXTRA_STATUS, 0)
                    charging = s == BatteryManager.BATTERY_STATUS_CHARGING || s == BatteryManager.BATTERY_STATUS_FULL
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> readBt(c)
                NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> readDnd(c)
                "android.media.VOLUME_CHANGED_ACTION" -> readVolume(c)
                android.location.LocationManager.PROVIDERS_CHANGED_ACTION -> readLocation(c)
            }
        }
    }

    private val net = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(n: Network, caps: NetworkCapabilities) {
            main.post { wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }
        }
        override fun onLost(n: Network) {
            main.post { wifi = false }
        }
    }

    private val torchCb = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == torchId) torch = enabled
        }
    }

    private var observer: ContentObserver? = null

    fun start(ctx: Context) {
        if (started) return
        started = true
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction("android.media.VOLUME_CHANGED_ACTION")
            addAction(android.location.LocationManager.PROVIDERS_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(ctx, receiver, f, ContextCompat.RECEIVER_EXPORTED)

        cm = ctx.getSystemService(ConnectivityManager::class.java).also {
            runCatching { it.registerDefaultNetworkCallback(net) }
            val caps = it.getNetworkCapabilities(it.activeNetwork)
            wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }

        cam = ctx.getSystemService(CameraManager::class.java).also { c ->
            torchId = runCatching {
                c.cameraIdList.firstOrNull { c.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            }.getOrNull()
            c.registerTorchCallback(torchCb, main)
        }

        observer = object : ContentObserver(main) {
            override fun onChange(selfChange: Boolean) = readSettings(ctx)
        }.also {
            ctx.contentResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, it)
            ctx.contentResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false, it)
        }

        readBt(ctx); readDnd(ctx); readVolume(ctx); readSettings(ctx); readLocation(ctx)
    }

    fun stop(ctx: Context) {
        if (!started) return
        started = false
        runCatching { ctx.unregisterReceiver(receiver) }
        runCatching { cm?.unregisterNetworkCallback(net) }
        runCatching { cam?.unregisterTorchCallback(torchCb) }
        observer?.let { runCatching { ctx.contentResolver.unregisterContentObserver(it) } }
        observer = null
    }

    private fun readBt(c: Context) {
        bt = runCatching { c.getSystemService(BluetoothManager::class.java).adapter?.isEnabled == true }.getOrDefault(false)
    }

    private fun readLocation(c: Context) {
        location = runCatching { c.getSystemService(android.location.LocationManager::class.java).isLocationEnabled }.getOrDefault(false)
    }

    private fun readDnd(c: Context) {
        dnd = c.getSystemService(NotificationManager::class.java).currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL
    }

    fun readVolume(c: Context) {
        val am = c.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        volume = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    private fun readSettings(c: Context) {
        val cr = c.contentResolver
        rotate = Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
        brightness = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
    }
}
