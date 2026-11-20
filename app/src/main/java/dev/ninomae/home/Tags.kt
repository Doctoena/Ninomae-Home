package dev.ninomae.home

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

/** A tag you made for the drawer: its name, its rail symbol and the apps in it (by app key). */
data class DrawerTag(val id: String, val name: String, val glyph: String, val apps: List<String>)

/** Symbols to pick from for a tag. */
val TAG_GLYPHS = listOf(
    "sell", "favorite", "star", "account_balance", "shopping_bag", "work", "school", "fitness_center",
    "restaurant", "flight", "sports_esports", "music_note", "photo_camera", "chat", "home", "build",
)

fun loadTags(prefs: SharedPreferences): List<DrawerTag> = runCatching {
    val a = JSONArray(prefs.getString("drawerTags", "[]")!!)
    (0 until a.length()).map { i ->
        val o = a.getJSONObject(i)
        val apps = o.optJSONArray("apps") ?: JSONArray()
        DrawerTag(o.getString("id"), o.getString("name"), o.optString("glyph", "sell"), (0 until apps.length()).map { apps.getString(it) })
    }
}.getOrDefault(emptyList())

fun tagsToJson(list: List<DrawerTag>): String = JSONArray().apply {
    list.forEach { t -> put(JSONObject().put("id", t.id).put("name", t.name).put("glyph", t.glyph).put("apps", JSONArray(t.apps))) }
}.toString()

/** A new, empty tag; returns its id. */
fun HomeState.newTag(name: String = "New tag", glyph: String = "sell", apps: List<String> = emptyList()): String {
    val id = "t" + System.currentTimeMillis().toString(36)
    saveTags(tags + DrawerTag(id, name, glyph, apps))
    return id
}

fun HomeState.editTag(id: String, name: String, glyph: String) =
    saveTags(tags.map { if (it.id == id) it.copy(name = name.ifBlank { it.name }, glyph = glyph) else it })

fun HomeState.deleteTag(id: String) = saveTags(tags.filter { it.id != id })

/** Put [app] in the tag or take it out. */
fun HomeState.toggleTag(id: String, app: String) = saveTags(
    tags.map { t -> if (t.id != id) t else t.copy(apps = if (app in t.apps) t.apps - app else t.apps + app) },
)

// ---------------------------------------------------------------- panels

/** Hold an app → Tags: its tags as chips to tick, and a field to make a new one with it in. */
@androidx.compose.runtime.Composable
fun TagsPanel(st: HomeState, ui: Ui, modifier: androidx.compose.ui.Modifier) {
    val pal = LocalPal.current
    val app = st.app(ui.tagApp) ?: return
    var name by androidx.compose.runtime.remember(ui.openCount) { androidx.compose.runtime.mutableStateOf("") }
    val big = 22.dp
    val j = Rad.joined
    androidx.compose.foundation.layout.Column(modifier, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
        PanelHead(st, corners(big, big, j, j)) {
            GlassIcon(app, 36.dp)
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.width(12.dp))
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.weight(1f)) {
                Title("Tags", size = 16.sp)
                Lbl("${app.label} shows under each one in the drawer", size = 12.sp, color = pal.sub, maxLines = 2)
            }
        }
        if (st.tags.isNotEmpty()) {
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(
                androidx.compose.ui.Modifier.fillMaxWidth().frosted(st, androidx.compose.foundation.shape.RoundedCornerShape(j), wash = 0.4f, edge = 0.16f).padding(12.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                st.tags.forEach { t -> TagChip(st, t.glyph, t.name, app.key in t.apps) { st.toggleTag(t.id, app.key) } }
            }
        }
        // A new tag, with this app already in it.
        androidx.compose.foundation.layout.Row(
            androidx.compose.ui.Modifier.fillMaxWidth().height(60.dp).frosted(st, corners(j, j, big, big), wash = 0.4f, edge = 0.16f).padding(horizontal = 10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            NameField(name, if (st.tags.isEmpty()) "Name your first tag" else "New tag", androidx.compose.ui.Modifier.weight(1f)) { name = it }
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.width(8.dp))
            if (name.isNotBlank()) AccentButton("Add") { st.newTag(name.trim(), apps = listOf(app.key)); name = "" }
            else PlainButton("Done") { ui.back() }
        }
    }
}

/** Rename a tag, pick its symbol, or delete it. */
@androidx.compose.runtime.Composable
fun TagEditPanel(st: HomeState, ui: Ui, modifier: androidx.compose.ui.Modifier) {
    val pal = LocalPal.current
    val tag = st.tags.firstOrNull { it.id == ui.tagId } ?: return
    var name by androidx.compose.runtime.remember(ui.openCount) { androidx.compose.runtime.mutableStateOf(tag.name) }
    var glyph by androidx.compose.runtime.remember(ui.openCount) { androidx.compose.runtime.mutableStateOf(tag.glyph) }
    val big = 22.dp
    val j = Rad.joined
    androidx.compose.foundation.layout.Column(modifier, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
        PanelHead(st, corners(big, big, j, j)) {
            androidx.compose.foundation.layout.Box(
                androidx.compose.ui.Modifier.size(36.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(pal.acc),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) { Glyph(glyph, size = 20.dp, color = pal.ink, fill = true) }
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.width(12.dp))
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.weight(1f)) {
                Title(if (ui.tagFresh) "New tag" else "Edit tag", size = 16.sp)
                Lbl(if (tag.apps.size == 1) "1 app · hold an app → Tags to add more" else "${tag.apps.size} apps · hold an app → Tags to add more", size = 12.sp, color = pal.sub, maxLines = 2)
            }
        }
        androidx.compose.foundation.layout.Box(
            androidx.compose.ui.Modifier.fillMaxWidth().height(60.dp).frosted(st, androidx.compose.foundation.shape.RoundedCornerShape(j), wash = 0.4f, edge = 0.16f).padding(horizontal = 10.dp),
            contentAlignment = androidx.compose.ui.Alignment.CenterStart,
        ) { NameField(name, "Name", androidx.compose.ui.Modifier.fillMaxWidth()) { name = it } }
        // the symbol, in a grid of eight across
        androidx.compose.foundation.layout.Column(
            androidx.compose.ui.Modifier.fillMaxWidth().frosted(st, androidx.compose.foundation.shape.RoundedCornerShape(j), wash = 0.4f, edge = 0.16f).padding(10.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        ) {
            TAG_GLYPHS.chunked(8).forEach { row ->
                androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                    row.forEach { g ->
                        val on = g == glyph
                        androidx.compose.foundation.layout.Box(
                            androidx.compose.ui.Modifier.size(38.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                .background(if (on) pal.acc else androidx.compose.ui.graphics.Color.Transparent).clickable { glyph = g },
                            contentAlignment = androidx.compose.ui.Alignment.Center,
                        ) { Glyph(g, size = 20.dp, color = if (on) pal.ink else pal.fg, fill = on) }
                    }
                }
            }
        }
        androidx.compose.foundation.layout.Row(
            androidx.compose.ui.Modifier.fillMaxWidth().height(58.dp).frosted(st, corners(j, j, big, big), wash = 0.4f, edge = 0.16f).padding(horizontal = 10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            if (!ui.tagFresh) PlainButton("Delete", color = androidx.compose.ui.graphics.Color(0xFFE8A39B)) { st.deleteTag(tag.id); ui.back() }
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.weight(1f))
            PlainButton("Cancel") { if (ui.tagFresh) st.deleteTag(tag.id); ui.back() }
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.width(6.dp))
            AccentButton("Save") { st.editTag(tag.id, name.trim(), glyph); ui.back() }
        }
    }
}

@androidx.compose.runtime.Composable
private fun PanelHead(st: HomeState, shape: androidx.compose.ui.graphics.Shape, content: @androidx.compose.runtime.Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    androidx.compose.foundation.layout.Row(
        androidx.compose.ui.Modifier.fillMaxWidth().height(64.dp).frosted(st, shape, wash = 0.4f, edge = 0.16f).padding(horizontal = 14.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        content = content,
    )
}

/** A tag as a chip: rose when the app is in it. */
@androidx.compose.runtime.Composable
fun TagChip(st: HomeState, glyph: String, name: String, on: Boolean, onClick: () -> Unit) {
    val pal = LocalPal.current
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(17.dp)
    androidx.compose.foundation.layout.Row(
        androidx.compose.ui.Modifier.height(34.dp)
            .then(if (on) androidx.compose.ui.Modifier.clip(shape).background(pal.acc) else androidx.compose.ui.Modifier.clip(shape).background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.10f)))
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Glyph(glyph, size = 16.dp, color = if (on) pal.ink else pal.acc, fill = on)
        androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.width(6.dp))
        Lbl(name, size = 13.sp, color = if (on) pal.ink else pal.fg, weight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    }
}

@androidx.compose.runtime.Composable
private fun NameField(text: String, hint: String, modifier: androidx.compose.ui.Modifier, onChange: (String) -> Unit) {
    val pal = LocalPal.current
    androidx.compose.foundation.layout.Box(
        modifier.height(40.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f)).padding(horizontal = 12.dp),
        contentAlignment = androidx.compose.ui.Alignment.CenterStart,
    ) {
        if (text.isEmpty()) Lbl(hint, size = 15.sp, color = pal.sub)
        androidx.compose.foundation.text.BasicTextField(
            text, { onChange(it.take(24)) }, androidx.compose.ui.Modifier.fillMaxWidth(), singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = pal.fg, fontSize = 15.sp, fontFamily = Sans),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(pal.acc),
        )
    }
}
