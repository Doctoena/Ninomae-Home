package dev.ninomae.home

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Date
import java.util.Locale

/* The calendar widget (K1a · Calm): a month on glass, with your next events joined under it as rows. */

private data class CalEvent(val id: Long, val title: String, val day: LocalDate, val time: String)

private object CalData {
    private val cols = arrayOf(
        CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY,
    )

    /** All-day events are stored at UTC midnight, so their day is read in UTC; timed ones in local time. */
    private fun dayOf(begin: Long, allDay: Boolean): LocalDate =
        Instant.ofEpochMilli(begin).atZone(if (allDay) ZoneOffset.UTC else ZoneId.systemDefault()).toLocalDate()

    /** The days of [ym] that have something on. */
    fun busyDays(ctx: Context, ym: YearMonth): Set<Int> = runCatching {
        val zone = ZoneId.systemDefault()
        val from = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() - 24 * 3600_000L
        val to = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() + 24 * 3600_000L
        val out = mutableSetOf<Int>()
        CalendarContract.Instances.query(ctx.contentResolver, cols, from, to)?.use { c ->
            while (c.moveToNext()) {
                val allDay = c.getInt(4) == 1
                val d = dayOf(c.getLong(2), allDay)
                if (YearMonth.from(d) == ym) out += d.dayOfMonth
            }
        }
        out
    }.getOrDefault(emptySet())

    /** The next [n] events that haven't ended, from now until [until] (a day, not included). */
    fun upcoming(ctx: Context, until: LocalDate, n: Int): List<CalEvent> = runCatching {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val end = until.atStartOfDay(zone).toInstant().toEpochMilli()
        val fmt = DateFormat.getTimeFormat(ctx)
        val today = LocalDate.now()
        val all = mutableListOf<Pair<Long, CalEvent>>()
        val from = now
        CalendarContract.Instances.query(ctx.contentResolver, cols, from - 24 * 3600_000L, end + 24 * 3600_000L)?.use { c ->
            while (c.moveToNext()) {
                val title = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                val begin = c.getLong(2)
                val allDay = c.getInt(4) == 1
                val day = dayOf(begin, allDay)
                if (day >= until) continue
                if (allDay) { if (day < today) continue } else if (c.getLong(3) < now) continue
                // All-day events sort to the start of their day.
                val key = if (allDay) day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() else begin
                all += key to CalEvent(c.getLong(0), title, day, if (allDay) "All day" else fmt.format(Date(begin)))
            }
        }
        all.sortedBy { it.first }.map { it.second }.take(n)
    }.getOrDefault(emptyList())
}

private fun openDay(ctx: Context, day: LocalDate) {
    val t = day.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    runCatching {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(t.toString()).build())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure { Actions.calendar(ctx) }
}

private fun openEvent(ctx: Context, id: Long) = runCatching {
    ctx.startActivity(Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}.onFailure { Actions.calendar(ctx) }

private fun newEvent(ctx: Context) = runCatching {
    ctx.startActivity(Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}.onFailure { Actions.calendar(ctx) }

private val Big = 26.dp
private val Small = 6.dp
private const val CELL = 34
private const val ROW = 44

/**
 * [fit] = true on the home grid: the month keeps its size and as many event rows as fit go under it.
 * Otherwise (the widgets page) it shows up to three rows and takes the height it needs.
 */
@Composable
fun CalendarWidget(st: HomeState, ui: Ui, modifier: Modifier, fit: Boolean, hideNext: Boolean = false) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val now = rememberNow()
    val today = now.toLocalDate()
    var shown by remember { mutableStateOf(YearMonth.from(today)) }
    val allowed = Glance.has(ctx, Manifest.permission.READ_CALENDAR)
    val stamp = Glance.stamp
    val busy by produceState(emptySet<Int>(), shown, allowed, stamp, today) {
        value = if (allowed) withContext(Dispatchers.IO) { CalData.busyDays(ctx, shown) } else emptySet()
    }
    // The list is this week and next week only (weeks start on the locale's first day).
    val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val weekStart = today.minusDays(((today.dayOfWeek.value - first.value + 7) % 7).toLong())
    val upcoming by produceState(emptyList<CalEvent>(), allowed, stamp, now.hour, today) {
        value = if (allowed) withContext(Dispatchers.IO) { CalData.upcoming(ctx, weekStart.plusDays(14), 6) } else emptyList()
    }
    // On the widgets page the rose line above already shows the next event, so the list skips it there.
    val shownAbove = if (hideNext) Glance.today.firstOrNull() else null
    val events = remember(upcoming, shownAbove, today) {
        val i = shownAbove?.let { n -> upcoming.indexOfFirst { it.day == today && it.title == n.title } } ?: -1
        if (i < 0) upcoming else upcoming.filterIndexed { j, _ -> j != i }
    }
    val lead = (shown.atDay(1).dayOfWeek.value - first.value + 7) % 7
    val weeks = (lead + shown.lengthOfMonth() + 6) / 7
    val monthH = 16 + 30 + 16 + 16 + weeks * CELL + 10

    BoxWithConstraints(modifier) {
        val room = if (fit) ((maxHeight.value - monthH - 4) / (ROW + 4)).toInt().coerceIn(0, 6) else 3
        // null = the "allow" row, an empty list = nothing coming up, else the events.
        val rows: List<CalEvent?> = when {
            !allowed -> listOf(null)
            events.isEmpty() -> listOf(CalEvent(-1, "", today, ""))
            else -> events
        }.take(room)

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val monthShape = if (rows.isEmpty()) RoundedCornerShape(Big) else corners(Big, Big, Small, Small)
            Column(Modifier.fillMaxWidth().frosted(st, monthShape, wash = 0.32f, edge = 0.2f).padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 10.dp)) {
                // Header: the month (tap to come back to this one), then ‹ when looking ahead, ›, and ＋.
                Row(Modifier.padding(start = 8.dp, end = 4.dp).height(30.dp), verticalAlignment = Alignment.CenterVertically) {
                    val title = if (shown.year == today.year) shown.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())
                    else shown.format(DateTimeFormatter.ofPattern("LLLL yyyy"))
                    Lbl(title, Modifier.weight(1f).clickable { shown = YearMonth.from(today) }, size = 20.sp, weight = FontWeight.Black, family = TitleFont)
                    if (shown != YearMonth.from(today)) { GlassSquare("chevron_left", false, glyphSize = 20.dp) { shown = shown.minusMonths(1) }; Spacer(Modifier.width(6.dp)) }
                    GlassSquare("chevron_right", false, glyphSize = 20.dp) { shown = shown.plusMonths(1) }
                    Spacer(Modifier.width(6.dp))
                    GlassSquare("add", true, glyphSize = 18.dp) { newEvent(ctx) }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { i ->
                        Lbl(first.plus(i.toLong()).getDisplayName(TextStyle.NARROW, Locale.getDefault()), Modifier.weight(1f), size = 10.5.sp,
                            color = pal.sub.copy(alpha = 0.8f), weight = FontWeight.SemiBold, align = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                Spacer(Modifier.height(4.dp))
                repeat(weeks) { w ->
                    Row(Modifier.fillMaxWidth().height(CELL.dp)) {
                        repeat(7) { c ->
                            val d = w * 7 + c - lead + 1
                            Box(Modifier.weight(1f).height(CELL.dp), contentAlignment = Alignment.Center) {
                                if (d in 1..shown.lengthOfMonth()) {
                                    val date = shown.atDay(d)
                                    val isToday = date == today
                                    val past = date < today
                                    Box(
                                        Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).then(if (isToday) Modifier.background(pal.acc) else Modifier)
                                            .clickable { openDay(ctx, date) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Num("$d", size = 13.sp, color = when { isToday -> pal.ink; past -> pal.sub.copy(alpha = 0.55f); else -> pal.fg })
                                    }
                                    if (!isToday && d in busy) Box(
                                        Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp).size(4.dp).clip(RoundedCornerShape(2.dp))
                                            .background(if (past) pal.sub.copy(alpha = 0.5f) else pal.acc),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            rows.forEachIndexed { i, e ->
                val b: Dp = if (i == rows.lastIndex) Big else Small
                val m = Modifier.fillMaxWidth().height(ROW.dp).frosted(st, corners(Small, Small, b, b), wash = 0.32f, edge = 0.2f)
                when {
                    e == null -> Row(m.clickable { ui.askPermissions(arrayOf(Manifest.permission.READ_CALENDAR)) }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Glyph("event", size = 16.dp, color = pal.acc)
                        Spacer(Modifier.width(10.dp))
                        Lbl("Show your events", Modifier.weight(1f), size = 14.sp, weight = FontWeight.Medium)
                        Lbl("Allow", size = 12.5.sp, color = pal.acc, weight = FontWeight.SemiBold)
                    }
                    e.id < 0 -> Row(m.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { Lbl("Nothing else this week or next", size = 14.sp, color = pal.sub) }
                    else -> Row(m.clickable { openEvent(ctx, e.id) }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        val label = when (e.day) {
                            today -> "Today"
                            today.plusDays(1) -> "Tmrw"
                            else -> e.day.format(DateTimeFormatter.ofPattern("EEE d"))
                        }
                        Lbl(label, Modifier.width(62.dp), size = 12.5.sp, color = pal.acc, weight = FontWeight.SemiBold)
                        Lbl(e.title, Modifier.weight(1f), size = 14.sp, weight = FontWeight.Medium)
                        Spacer(Modifier.width(8.dp))
                        Lbl(e.time, size = 12.5.sp, color = pal.sub)
                    }
                }
            }
        }
    }
}
