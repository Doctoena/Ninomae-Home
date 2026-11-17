package dev.ninomae.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** At a glance: the next thing in your calendar and the weather, for the clock and greeting widgets. */
object Glance {
    data class Event(val title: String, val whenText: String)
    data class Weather(val temp: String, val text: String, val glyph: String)

    var event by mutableStateOf<Event?>(null)
        private set
    var weather by mutableStateOf<Weather?>(null)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var weatherAt = 0L

    fun has(ctx: Context, p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    /** Called when home comes back and every so often while it's showing. */
    fun refresh(ctx: Context, st: HomeState) {
        val app = ctx.applicationContext
        scope.launch {
            event = if (st.glanceCalendar && has(app, Manifest.permission.READ_CALENDAR)) withContext(Dispatchers.IO) { nextEvent(app) } else null
            if (!st.glanceWeather) {
                weather = null
            } else if (System.currentTimeMillis() - weatherAt > 30 * 60_000 && has(app, Manifest.permission.ACCESS_COARSE_LOCATION)) {
                withContext(Dispatchers.IO) { fetchWeather(app) }?.let {
                    weather = it
                    weatherAt = System.currentTimeMillis()
                }
            }
        }
    }

    fun forgetWeather() {
        weather = null
        weatherAt = 0
    }

    // ---------- calendar ----------

    private fun nextEvent(ctx: Context): Event? = runCatching {
        val now = System.currentTimeMillis()
        val until = now + 24 * 3600_000L
        val cur = CalendarContract.Instances.query(
            ctx.contentResolver,
            arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY),
            now, until,
        ) ?: return@runCatching null
        cur.use { c ->
            var best: Triple<String, Long, Long>? = null
            while (c.moveToNext()) {
                if (c.getInt(3) == 1) continue
                val title = c.getString(0)?.takeIf { it.isNotBlank() } ?: continue
                val begin = c.getLong(1)
                val end = c.getLong(2)
                if (end < now) continue
                if (best == null || begin < best.second) best = Triple(title, begin, end)
            }
            best?.let { (title, begin, _) ->
                val mins = ((begin - now) / 60_000).toInt()
                val time = DateFormat.getTimeFormat(ctx).format(Date(begin))
                val whenText = when {
                    mins <= 0 -> "now"
                    mins < 60 -> "in $mins min"
                    isTomorrow(begin) -> "tomorrow $time"
                    else -> time
                }
                Event(title, whenText)
            }
        }
    }.getOrNull()

    private fun isTomorrow(t: Long): Boolean {
        val a = Calendar.getInstance()
        val b = Calendar.getInstance().apply { timeInMillis = t }
        return b.get(Calendar.DAY_OF_YEAR) != a.get(Calendar.DAY_OF_YEAR)
    }

    // ---------- weather (Open-Meteo, rough location only) ----------

    @Suppress("MissingPermission")
    private fun lastLocation(ctx: Context): Location? {
        val lm = ctx.getSystemService(LocationManager::class.java)
        return listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.FUSED_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    private fun fetchWeather(ctx: Context): Weather? = runCatching {
        val loc = lastLocation(ctx) ?: return@runCatching null
        // Two decimals is about a kilometre: enough for weather, not an address.
        val lat = "%.2f".format(Locale.US, loc.latitude)
        val lon = "%.2f".format(Locale.US, loc.longitude)
        val fahrenheit = Locale.getDefault().country in setOf("US", "LR", "MM")
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code,is_day" + if (fahrenheit) "&temperature_unit=fahrenheit" else "",
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val cur = JSONObject(body).getJSONObject("current")
        val code = cur.getInt("weather_code")
        val day = cur.optInt("is_day", 1) == 1
        val (text, glyph) = describe(code, day)
        Weather("${cur.getDouble("temperature_2m").roundToInt()}°", text, glyph)
    }.getOrNull()

    private fun describe(code: Int, day: Boolean): Pair<String, String> = when (code) {
        0 -> "Clear" to if (day) "clear_day" else "clear_night"
        1, 2 -> "Partly cloudy" to if (day) "partly_cloudy_day" else "partly_cloudy_night"
        3 -> "Cloudy" to "cloud"
        45, 48 -> "Fog" to "foggy"
        in 51..57 -> "Drizzle" to "rainy_light"
        in 61..67, in 80..82 -> "Rain" to "rainy"
        in 71..77, 85, 86 -> "Snow" to "weather_snowy"
        in 95..99 -> "Storms" to "thunderstorm"
        else -> "Weather" to "cloud"
    }
}
