package dev.ninomae.home

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.pow

/** Things search can answer besides apps. */
sealed interface Answer {
    data class Calc(val expr: String, val value: String) : Answer
    data class Convert(val from: String, val to: String) : Answer
    data class Contact(val id: Long, val name: String, val number: String?) : Answer
    data class Setting(val name: String, val glyph: String, val action: String) : Answer
    data class Event(val id: Long, val title: String, val begin: Long, val note: String) : Answer
    data class File(val id: Long, val name: String, val mime: String?, val note: String) : Answer
}

object Search {
    // ---------- calculator ----------

    /** "12*(3+4)", "2^10", "15% of 80", "1,5 + 2". Null when it isn't maths. */
    fun calc(q: String): Answer.Calc? {
        val pct = Regex("""^\s*([\d.,]+)\s*%\s*of\s*([\d.,]+)\s*$""", RegexOption.IGNORE_CASE).find(q)
        if (pct != null) {
            val a = pct.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
            val b = pct.groupValues[2].replace(',', '.').toDoubleOrNull() ?: return null
            return Answer.Calc(q.trim(), fmt(a / 100 * b))
        }
        val e = q.replace('×', '*').replace('x', '*').replace('÷', '/').replace(',', '.').replace(" ", "")
        if (!Regex("""^[\d.+\-*/()^%]+$""").matches(e)) return null
        if (!e.any { it in "+-*/^%" } || e.all { it.isDigit() || it == '.' }) return null
        return runCatching { Answer.Calc(q.trim(), fmt(Expr(e).parse())) }.getOrNull()
    }

    private class Expr(val s: String) {
        var i = 0
        fun parse(): Double = sum().also { if (i != s.length) error("trailing") }
        private fun sum(): Double {
            var v = product()
            while (i < s.length && (s[i] == '+' || s[i] == '-')) v = if (s[i++] == '+') v + product() else v - product()
            return v
        }
        private fun product(): Double {
            var v = power()
            while (i < s.length && (s[i] == '*' || s[i] == '/' || s[i] == '%')) {
                v = when (s[i++]) { '*' -> v * power(); '/' -> v / power(); else -> v % power() }
            }
            return v
        }
        private fun power(): Double {
            val b = unary()
            return if (i < s.length && s[i] == '^') { i++; b.pow(power()) } else b
        }
        private fun unary(): Double = if (i < s.length && s[i] == '-') { i++; -unary() } else if (i < s.length && s[i] == '+') { i++; unary() } else atom()
        private fun atom(): Double {
            if (i < s.length && s[i] == '(') {
                i++
                val v = sum()
                if (i >= s.length || s[i] != ')') error("paren")
                i++
                return v
            }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            return s.substring(start, i).toDouble()
        }
    }

    private fun fmt(v: Double): String {
        if (v.isNaN() || v.isInfinite()) error("bad")
        val bd = BigDecimal(v).round(MathContext(12, RoundingMode.HALF_UP)).stripTrailingZeros()
        return if (bd.scale() < 0) bd.setScale(0).toPlainString() else bd.toPlainString()
    }

    // ---------- units ----------

    private data class Unit2(val names: List<String>, val kind: String, val toBase: (Double) -> Double, val fromBase: (Double) -> Double, val show: String)

    private fun lin(kind: String, factor: Double, show: String, vararg names: String) =
        Unit2(names.toList(), kind, { it * factor }, { it / factor }, show)

    private val units = listOf(
        lin("len", 1000.0, "km", "km", "kilometre", "kilometres", "kilometer", "kilometers"),
        lin("len", 1.0, "m", "m", "metre", "metres", "meter", "meters"),
        lin("len", 0.01, "cm", "cm", "centimetre", "centimetres", "centimeter", "centimeters"),
        lin("len", 0.001, "mm", "mm"),
        lin("len", 1609.344, "mi", "mi", "mile", "miles"),
        lin("len", 0.3048, "ft", "ft", "foot", "feet"),
        lin("len", 0.0254, "in", "in", "inch", "inches"),
        lin("len", 0.9144, "yd", "yd", "yard", "yards"),
        lin("mass", 1000.0, "kg", "kg", "kilo", "kilos", "kilogram", "kilograms"),
        lin("mass", 1.0, "g", "g", "gram", "grams"),
        lin("mass", 453.59237, "lb", "lb", "lbs", "pound", "pounds"),
        lin("mass", 28.349523125, "oz", "oz", "ounce", "ounces"),
        lin("vol", 1.0, "L", "l", "litre", "litres", "liter", "liters"),
        lin("vol", 0.001, "mL", "ml", "millilitre", "milliliter"),
        lin("vol", 3.785411784, "gal", "gal", "gallon", "gallons"),
        lin("vol", 0.2365882365, "cups", "cup", "cups"),
        lin("speed", 1.0, "km/h", "kmh", "km/h", "kph"),
        lin("speed", 1.609344, "mph", "mph"),
        Unit2(listOf("c", "°c", "celsius"), "temp", { it }, { it }, "°C"),
        Unit2(listOf("f", "°f", "fahrenheit"), "temp", { (it - 32) * 5 / 9 }, { it * 9 / 5 + 32 }, "°F"),
        Unit2(listOf("k", "kelvin"), "temp", { it - 273.15 }, { it + 273.15 }, "K"),
    )

    /** "5 km to mi", "70 f in c", "2 cups in ml". */
    fun convert(q: String): Answer.Convert? {
        val m = Regex("""^\s*(-?[\d.,]+)\s*([a-zA-Z°/]+)\s+(?:to|in|into|as)\s+([a-zA-Z°/]+)\s*$""", RegexOption.IGNORE_CASE).find(q) ?: return null
        val v = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val from = units.firstOrNull { m.groupValues[2].lowercase() in it.names } ?: return null
        val to = units.firstOrNull { m.groupValues[3].lowercase() in it.names && it.kind == from.kind } ?: return null
        val out = to.fromBase(from.toBase(v))
        return Answer.Convert("${fmt(v)} ${from.show}", "${fmt(BigDecimal(out).round(MathContext(6)).toDouble())} ${to.show}")
    }

    // ---------- contacts ----------

    fun canReadContacts(ctx: Context) = ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun contacts(ctx: Context, q: String): List<Answer.Contact> {
        if (q.length < 2 || !canReadContacts(ctx)) return emptyList()
        val uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(q))
        val out = LinkedHashMap<Long, Answer.Contact>()
        runCatching {
            ctx.contentResolver.query(
                uri,
                arrayOf(ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext() && out.size < 4) {
                    val id = c.getLong(0)
                    if (id !in out) out[id] = Answer.Contact(id, c.getString(1) ?: "", c.getString(2))
                }
            }
        }
        return out.values.toList()
    }

    fun openContact(ctx: Context, c: Answer.Contact) = start(ctx, Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, c.id)))
    fun call(ctx: Context, c: Answer.Contact) = c.number?.let { start(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(it)))) }
    fun text(ctx: Context, c: Answer.Contact) = c.number?.let { start(ctx, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(it)))) }

    // ---------- Android settings pages ----------

    private val settings = listOf(
        Answer.Setting("Wi-Fi", "wifi", Settings.ACTION_WIFI_SETTINGS),
        Answer.Setting("Bluetooth", "bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS),
        Answer.Setting("Display and brightness", "brightness_6", Settings.ACTION_DISPLAY_SETTINGS),
        Answer.Setting("Battery", "battery_full", Intent.ACTION_POWER_USAGE_SUMMARY),
        Answer.Setting("Sound and vibration", "volume_up", Settings.ACTION_SOUND_SETTINGS),
        Answer.Setting("Notifications", "notifications", Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS),
        Answer.Setting("Apps", "apps", Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
        Answer.Setting("Storage", "hard_drive", Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
        Answer.Setting("Location", "location_on", Settings.ACTION_LOCATION_SOURCE_SETTINGS),
        Answer.Setting("Security and privacy", "lock", Settings.ACTION_SECURITY_SETTINGS),
        Answer.Setting("Accessibility", "accessibility_new", Settings.ACTION_ACCESSIBILITY_SETTINGS),
        Answer.Setting("Date and time", "schedule", Settings.ACTION_DATE_SETTINGS),
        Answer.Setting("Language and keyboard", "language", Settings.ACTION_LOCALE_SETTINGS),
        Answer.Setting("Mobile data and network", "signal_cellular_alt", Settings.ACTION_WIRELESS_SETTINGS),
        Answer.Setting("Hotspot and tethering", "wifi_tethering", "android.settings.TETHER_SETTINGS"),
        Answer.Setting("NFC", "nfc", Settings.ACTION_NFC_SETTINGS),
        Answer.Setting("Do not disturb", "do_not_disturb_on", Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS),
        Answer.Setting("Developer options", "code", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        Answer.Setting("About phone", "info", Settings.ACTION_DEVICE_INFO_SETTINGS),
        Answer.Setting("Wallpaper", "wallpaper", Intent.ACTION_SET_WALLPAPER),
    )

    // ---------- calendar ----------

    fun canReadCalendar(ctx: Context) = ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Events whose title matches, from a month back to a year ahead: upcoming ones first, soonest first. */
    fun events(ctx: Context, q: String): List<Answer.Event> = runCatching {
        val now = System.currentTimeMillis()
        val day = 24 * 3600_000L
        val uri = android.provider.CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath((now - 31 * day).toString()).appendPath((now + 366 * day).toString()).build()
        val cols = arrayOf(
            android.provider.CalendarContract.Instances.EVENT_ID, android.provider.CalendarContract.Instances.TITLE,
            android.provider.CalendarContract.Instances.BEGIN, android.provider.CalendarContract.Instances.END,
            android.provider.CalendarContract.Instances.ALL_DAY,
        )
        val out = mutableListOf<Answer.Event>()
        val time = android.text.format.DateFormat.getTimeFormat(ctx)
        ctx.contentResolver.query(uri, cols, "${android.provider.CalendarContract.Instances.TITLE} LIKE ?", arrayOf("%$q%"), null)?.use { c ->
            while (c.moveToNext()) {
                val begin = c.getLong(2)
                val allDay = c.getInt(4) == 1
                val zone = if (allDay) java.time.ZoneOffset.UTC else java.time.ZoneId.systemDefault()
                val date = java.time.Instant.ofEpochMilli(begin).atZone(zone).toLocalDate()
                    .format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM"))
                val note = if (allDay) "$date · All day" else "$date · ${time.format(java.util.Date(begin))} – ${time.format(java.util.Date(c.getLong(3)))}"
                out += Answer.Event(c.getLong(0), c.getString(1) ?: continue, begin, note)
            }
        }
        out.distinctBy { it.id to it.begin }
            .sortedWith(compareBy<Answer.Event> { it.begin < now - day }.thenBy { kotlin.math.abs(it.begin - now) })
            .take(5)
    }.getOrDefault(emptyList())

    fun openEvent(ctx: Context, e: Answer.Event) = start(
        ctx,
        Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(android.provider.CalendarContract.Events.CONTENT_URI, e.id))
            .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.begin),
    )

    // ---------- files (needs All files access) ----------

    fun canReadFiles() = android.os.Environment.isExternalStorageManager()

    /** Files whose name matches, newest first. */
    fun files(ctx: Context, q: String): List<Answer.File> = runCatching {
        val uri = android.provider.MediaStore.Files.getContentUri("external")
        val cols = arrayOf(
            android.provider.MediaStore.Files.FileColumns._ID, android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME,
            android.provider.MediaStore.Files.FileColumns.MIME_TYPE, android.provider.MediaStore.Files.FileColumns.RELATIVE_PATH,
            android.provider.MediaStore.Files.FileColumns.SIZE, android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED,
        )
        val args = android.os.Bundle().apply {
            putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, "${cols[1]} LIKE ? AND ${cols[2]} IS NOT NULL")
            putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf("%$q%"))
            putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${cols[5]} DESC")
            putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, 6)
        }
        val out = mutableListOf<Answer.File>()
        ctx.contentResolver.query(uri, cols, args, null)?.use { c ->
            while (c.moveToNext()) {
                val folder = c.getString(3)?.trimEnd('/')?.substringAfterLast('/')?.ifBlank { null } ?: "Phone"
                val size = android.text.format.Formatter.formatShortFileSize(ctx, c.getLong(4))
                val date = java.time.Instant.ofEpochSecond(c.getLong(5)).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    .format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))
                out += Answer.File(c.getLong(0), c.getString(1) ?: continue, c.getString(2), "$folder · $size · $date")
            }
        }
        out
    }.getOrDefault(emptyList())

    fun fileGlyph(mime: String?) = when {
        mime == null -> "description"
        mime.startsWith("image/") -> "image"
        mime.startsWith("video/") -> "movie"
        mime.startsWith("audio/") -> "music_note"
        mime == "application/pdf" -> "picture_as_pdf"
        mime.contains("zip") || mime.contains("compressed") -> "folder_zip"
        else -> "description"
    }

    fun openFile(ctx: Context, f: Answer.File) {
        val uri = android.provider.MediaStore.Files.getContentUri("external", f.id)
        start(ctx, Intent(Intent.ACTION_VIEW).setDataAndType(uri, f.mime ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    fun allowFiles(ctx: Context) = start(
        ctx,
        Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}")),
    )

    fun settings(q: String): List<Answer.Setting> {
        if (q.length < 2) return emptyList()
        fun norm(x: String) = x.lowercase().filter { it.isLetterOrDigit() || it == ' ' }
        val t = norm(q.trim()).replace(" ", "")
        return settings.filter { s ->
            val n = norm(s.name)
            n.replace(" ", "").startsWith(t) || n.split(' ').any { it.startsWith(t) }
        }.take(3)
    }

    fun openSetting(ctx: Context, s: Answer.Setting) = start(ctx, Intent(s.action))

    fun copy(ctx: Context, text: String) {
        ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Result", text))
        Toast.makeText(ctx, "Copied $text", Toast.LENGTH_SHORT).show()
    }

    private fun start(ctx: Context, i: Intent) {
        runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Toast.makeText(ctx, "Couldn't open that", Toast.LENGTH_SHORT).show() }
    }
}
