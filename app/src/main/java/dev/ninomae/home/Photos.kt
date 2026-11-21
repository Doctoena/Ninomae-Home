package dev.ninomae.home

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Photos from your own Immich server, for the photo widget (P2): on this day (Immich's memories),
 * your favourites, or random ones. Only the server and an API key you made in Immich are needed;
 * both stay on the phone, and the pictures come straight from the server.
 */
object Photos {
    data class Photo(val id: String, val taken: LocalDate?)

    /** What's shown: the photos, which one, and what the list is (it can fall back to random). */
    var photos by mutableStateOf<List<Photo>>(emptyList())
        private set
    var index by mutableIntStateOf(0)
    /** 0 on this day, 1 favourites, 2 random: what was actually loaded. */
    var shownMode by mutableIntStateOf(0)
        private set
    /** Set when "on this day" had nothing, so random ones are shown instead. */
    var fellBack by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private val lock = Mutex()
    private var loadedFor = ""
    private var loadedAt = 0L
    private val thumbs = object : android.util.LruCache<String, ImageBitmap>(8) {}

    fun configured(st: HomeState) = st.immichUrl.isNotBlank() && st.immichKey.isNotBlank()

    /** The server's address as typed, tidied: a scheme if it has none, no slash at the end. */
    fun base(url: String): String {
        val u = url.trim().trimEnd('/')
        return if (u.startsWith("http://") || u.startsWith("https://")) u else "https://$u"
    }

    /** Loads the list again when what's asked for changed, the day turned, or an hour went by. */
    suspend fun refresh(st: HomeState, force: Boolean = false) {
        if (!configured(st)) { photos = emptyList(); error = null; return }
        val wanted = "${base(st.immichUrl)}|${st.immichKey.hashCode()}|${st.immichMode}|${LocalDate.now()}"
        lock.withLock {
            if (!force && wanted == loadedFor && System.currentTimeMillis() - loadedAt < 3600_000L) return
            loading = true
            val result = runCatching { withContext(Dispatchers.IO) { fetch(st, st.immichMode) } }
            loading = false
            result.onSuccess { (list, mode) ->
                photos = list
                shownMode = mode
                fellBack = st.immichMode == 0 && mode != 0
                index = 0
                error = null
                loadedFor = wanted
                loadedAt = System.currentTimeMillis()
            }.onFailure { error = describe(it) }
        }
    }

    fun step(dir: Int) {
        if (photos.isEmpty()) return
        index = (index + dir).mod(photos.size)
    }

    /** The list for [mode]; "on this day" with nothing today falls back to random. */
    private fun fetch(st: HomeState, mode: Int): Pair<List<Photo>, Int> {
        val list = when (mode) {
            0 -> memories(st)
            1 -> assets(JSONObject(post(st, "/api/search/metadata", JSONObject().put("isFavorite", true).put("size", 60))).getJSONObject("assets").getJSONArray("items")).shuffled()
            else -> assets(JSONArray(post(st, "/api/search/random", JSONObject().put("size", 30))))
        }
        if (list.isEmpty() && mode == 0) return fetch(st, 2).first to 2
        return list to mode
    }

    /** Immich's "on this day" memories for today, newest year first. */
    private fun memories(st: HomeState): List<Photo> {
        val today = LocalDate.now()
        val all = JSONArray(get(st, "/api/memories?for=" + Uri.encode(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))))
        val out = mutableListOf<Photo>()
        for (i in 0 until all.length()) {
            val m = all.getJSONObject(i)
            if (m.optString("type", "on_this_day") != "on_this_day") continue
            out += assets(m.optJSONArray("assets") ?: JSONArray())
        }
        // Only the ones taken on this day of an earlier year (in case the server sent more than today's).
        return out.filter { p -> p.taken == null || (p.taken.monthValue == today.monthValue && p.taken.dayOfMonth == today.dayOfMonth && p.taken.year < today.year) }
            .distinctBy { it.id }
            .sortedByDescending { it.taken }
    }

    private fun assets(a: JSONArray): List<Photo> = (0 until a.length()).map { a.getJSONObject(it) }
        .filter { it.optString("type", "IMAGE") == "IMAGE" }
        .map { o ->
            val taken = (o.optString("localDateTime").ifEmpty { o.optString("fileCreatedAt") }).take(10)
            Photo(o.getString("id"), runCatching { LocalDate.parse(taken) }.getOrNull())
        }

    /** One photo, at the server's preview size, kept for the last few. */
    suspend fun thumb(st: HomeState, id: String): ImageBitmap? {
        thumbs.get(id)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val c = open(st, "/api/assets/$id/thumbnail?size=preview", "GET")
                c.inputStream.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
            }.getOrNull()
        }?.also { thumbs.put(id, it) }
    }

    /** For settings: the server's version and how many photos it has for today, or what went wrong. */
    suspend fun test(st: HomeState): String = withContext(Dispatchers.IO) {
        runCatching {
            val v = JSONObject(open(st, "/api/server/version", "GET").inputStream.bufferedReader().readText())
            val version = "Immich ${v.optInt("major")}.${v.optInt("minor")}.${v.optInt("patch")}"
            val today = memories(st).size
            "$version · " + if (today == 1) "1 memory today" else "$today memories today"
        }.getOrElse { "Not connected: " + describe(it) }
    }

    fun openInImmich(ctx: Context, st: HomeState, id: String) {
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${base(st.immichUrl)}/photos/$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun describe(e: Throwable): String = when (e) {
        is java.net.UnknownHostException -> "can't find the server"
        is java.net.SocketTimeoutException, is java.net.ConnectException -> "the server didn't answer"
        is HttpError -> if (e.code == 401 || e.code == 403) "the API key wasn't accepted" else "the server said ${e.code}"
        is javax.net.ssl.SSLException -> "a secure connection failed"
        else -> e.message ?: "something went wrong"
    }

    private class HttpError(val code: Int) : Exception("HTTP $code")

    private fun open(st: HomeState, path: String, method: String, body: String? = null): HttpURLConnection {
        val c = (URL(base(st.immichUrl) + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8000
            readTimeout = 12000
            setRequestProperty("x-api-key", st.immichKey.trim())
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write(body.toByteArray()) }
            }
        }
        if (c.responseCode !in 200..299) { val code = c.responseCode; c.disconnect(); throw HttpError(code) }
        return c
    }

    private fun get(st: HomeState, path: String) = open(st, path, "GET").inputStream.bufferedReader().use { it.readText() }
    private fun post(st: HomeState, path: String, body: JSONObject) = open(st, path, "POST", body.toString()).inputStream.bufferedReader().use { it.readText() }
}

/**
 * The photo widget (P2 · framed on glass): the photo inset in a frosted pane, and a caption strip
 * joined under it with what it is, when it was taken, and ‹ › to step through. Tap the photo to open it
 * in Immich.
 */
@Composable
fun PhotoWidget(st: HomeState, ui: Ui, modifier: Modifier) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    LaunchedEffect(Glance.stamp, st.immichUrl, st.immichKey, st.immichMode) { Photos.refresh(st) }
    val list = Photos.photos
    val photo = list.getOrNull(Photos.index.coerceIn(0, (list.size - 1).coerceAtLeast(0)))
    val pic by produceState<ImageBitmap?>(null, photo?.id) { value = photo?.let { Photos.thumb(st, it.id) } }
    val ready = Photos.configured(st)

    BoxWithConstraints(modifier) {
        val roomy = maxWidth > 220.dp
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // ---------- the photo, inset in glass ----------
            Box(Modifier.weight(1f).fillMaxWidth().frosted(st, corners(26.dp, 26.dp, 6.dp, 6.dp), wash = 0.32f, edge = 0.2f).padding(8.dp)) {
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(glass(0.06f))
                        .clickable {
                            when {
                                !ready -> ui.openSettings("photos")
                                photo != null -> Photos.openInImmich(ctx, st, photo.id)
                                else -> {}
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val p = pic
                    when {
                        p != null -> Image(p, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        !ready -> Note("photo_library", "Your Immich photos", "Set it up in Settings → Photos")
                        Photos.error != null -> Note("cloud_off", "Can't reach Immich", Photos.error!!.replaceFirstChar { it.uppercase() })
                        Photos.loading || photo != null -> Glyph("photo_library", size = 30.dp, color = pal.sub)
                        else -> Note("photo_library", "No photos", "Nothing to show yet")
                    }
                }
            }
            // ---------- the caption, joined under it ----------
            Row(
                Modifier.fillMaxWidth().height(52.dp).frosted(st, corners(6.dp, 6.dp, 26.dp, 26.dp), wash = 0.40f, edge = 0.2f)
                    .clickable(enabled = list.size > 1) { Photos.step(1) }
                    .padding(start = 16.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Title(if (!ready) "Photos" else when (Photos.shownMode) { 0 -> "On this day"; 1 -> "Favourites"; else -> "Random" }, size = 14.5.sp)
                    Lbl(caption(photo, list.size), size = 11.sp, color = pal.sub)
                }
                if (roomy && list.size > 1) {
                    Arrow("chevron_left") { Photos.step(-1) }
                    Spacer(Modifier.width(8.dp))
                    Arrow("chevron_right") { Photos.step(1) }
                }
            }
        }
    }
}

/** "2023 · 3 years ago · 1 of 4", or the date for favourites and random ones. */
private fun caption(p: Photos.Photo?, count: Int): String {
    if (p == null) return if (Photos.fellBack) "Nothing on this day" else ""
    val of = if (count > 1) " · ${Photos.index + 1} of $count" else ""
    val taken = p.taken ?: return "${Photos.index + 1} of $count"
    if (Photos.shownMode == 0) {
        val years = LocalDate.now().year - taken.year
        return "${taken.year} · " + (if (years == 1) "a year ago" else "$years years ago") + of
    }
    val date = taken.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
    return (if (Photos.fellBack) "Nothing on this day · " else "") + date + of
}

@Composable
private fun Arrow(glyph: String, onClick: () -> Unit) {
    // 32 dp to look at, 44 dp to tap.
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(11.dp)).background(glass(0.10f)), contentAlignment = Alignment.Center) { Glyph(glyph, size = 18.dp) }
    }
}

@Composable
private fun Note(glyph: String, title: String, sub: String) {
    val pal = LocalPal.current
    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Glyph(glyph, size = 28.dp, color = pal.acc)
        Spacer(Modifier.height(8.dp))
        Lbl(title, size = 14.sp, weight = FontWeight.Medium, align = TextAlign.Center)
        Lbl(sub, size = 11.sp, color = pal.sub, maxLines = 2, align = TextAlign.Center)
    }
}
