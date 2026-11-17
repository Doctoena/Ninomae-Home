package dev.ninomae.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Launcher settings, built from rows like the shell's Settings app. */
@Composable
fun SettingsPanel(st: HomeState, ui: Ui, modifier: Modifier) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val big = Rad.normal
    val j = Rad.joined
    val js = RoundedCornerShape(j)

    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Piece(Modifier.fillMaxWidth().height(64.dp), corners(big, big, j, j), edge = true) {
            Row(Modifier.fillMaxSize().padding(start = 14.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                SqIcon("tune", true, 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Title("Ninomae Home", size = 18.sp)
                    Lbl("Version 0.3", size = 12.sp, color = pal.sub)
                }
                IconButton("close") { ui.close() }
            }
        }

        SettingRow("person", "Your name", "Shown in the greeting", js) {
            Box(
                Modifier.width(150.dp).height(34.dp).clip(RoundedCornerShape(Rad.segment)).background(pal.l5).padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    st.name,
                    { st.updateName(it.take(20)) },
                    Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = TextStyle(color = pal.fg, fontSize = 14.sp, fontFamily = Sans),
                    cursorBrush = SolidColor(pal.acc),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (st.name.isEmpty()) Lbl("Name", size = 14.sp, color = pal.dim)
                            inner()
                        }
                    },
                )
            }
        }
        Section("Status bar")
        SettingRow("smartphone", "Cover screen", "Ninomae's bar, or Samsung's like in your apps", js) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Seg("Ninomae", st.barCover) { st.updateBar(cover = true, own = true) }
                Seg("Samsung", !st.barCover) { st.updateBar(cover = true, own = false) }
            }
        }
        SettingRow("tablet", "Inner screen", "Ninomae's bar holds your pairs and the player", js) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Seg("Ninomae", st.barInner) { st.updateBar(cover = false, own = true) }
                Seg("Samsung", !st.barInner) { st.updateBar(cover = false, own = false) }
            }
        }

        Section("Home screen")
        SettingRow("label", "App names", "Under icons on the home screen", js) { MiniSwitch(st.labels) { st.updateLabels(!st.labels) } }
        SettingRow("circle_notifications", "Notification dots", "On apps that have notifications", js) { MiniSwitch(st.dots) { st.updateDots(!st.dots) } }
        SettingRow("history", "Recent apps in the dock", "The middle piece of the dock", js) { MiniSwitch(st.dockRecentsOn) { st.updateDockRecents(!st.dockRecentsOn) } }
        SettingRow("edit", "Edit the home screen", "Or hold an empty spot", js) {
            PlainButton("Edit") { ui.close(); ui.editing = true }
        }

        Section("Gestures")
        SettingRow("swipe_down", "Swipe down on the cover", "Samsung's panel, like pulling the status bar", js) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Seg("Deck", st.swipeDown == 0) { st.updateSwipeDown(true, 0) }
                Seg("Samsung", st.swipeDown == 1) { st.updateSwipeDown(true, 1) }
                Seg("Off", st.swipeDown == 2) { st.updateSwipeDown(true, 2) }
            }
        }
        SettingRow("swipe_down", "Swipe down on the inner screen", "The bar's status pieces open the deck too", js) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Seg("Deck", st.swipeDownInner == 0) { st.updateSwipeDown(false, 0) }
                Seg("Samsung", st.swipeDownInner == 1) { st.updateSwipeDown(false, 1) }
                Seg("Off", st.swipeDownInner == 2) { st.updateSwipeDown(false, 2) }
            }
        }
        SettingRow("touch_app", "Double-tap to lock", "Needs accessibility", js) { MiniSwitch(st.doubleTapLock) { st.updateDoubleTapLock(!st.doubleTapLock) } }
        SettingRow("swipe_up", "Swipe up", "Opens all apps", js) {}

        if (st.hidden.isNotEmpty()) {
            Section("Hidden apps")
            st.hidden.mapNotNull { st.app(it) }.forEach { app ->
                Piece(Modifier.fillMaxWidth().height(56.dp), js, edge = true) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(app, 32.dp)
                        Spacer(Modifier.width(12.dp))
                        Lbl(app.label, Modifier.weight(1f))
                        PlainButton("Show") { st.setHidden(app.key, false) }
                    }
                }
            }
        }

        Section("Access")

        AccessRow("home", "Home app", "Make Ninomae your home screen", st.isDefaultHome, js) { Actions.homeSettings(ctx) }
        AccessRow("notifications", "Notification access", "The player and notifications", st.notifAccess, js) { Actions.openListenerSettings(ctx) }
        AccessRow("splitscreen_left", "Accessibility", "Split screen, pairs, lock, screenshot, power menu", st.a11yOn, js) { Actions.openAccessibility(ctx) }
        AccessRow("brightness_6", "Change system settings", "Brightness and auto-rotate", st.canWrite, corners(j, j, big, big)) { Actions.openWriteSettings(ctx) }
    }
}

@Composable
private fun Section(text: String) {
    Lbl(text, Modifier.padding(start = 14.dp, top = 12.dp, bottom = 4.dp), size = 13.sp, color = LocalPal.current.sub)
}

@Composable
private fun SettingRow(glyph: String, title: String, sub: String, shape: Shape, trailing: @Composable () -> Unit) {
    val pal = LocalPal.current
    Piece(Modifier.fillMaxWidth().heightIn(min = 64.dp), shape, edge = true) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SqIcon(glyph, false)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Lbl(title, size = 14.sp)
                Lbl(sub, size = 12.sp, color = pal.sub, maxLines = 2)
            }
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

@Composable
private fun AccessRow(glyph: String, title: String, sub: String, granted: Boolean, shape: Shape, onClick: () -> Unit) {
    val pal = LocalPal.current
    SettingRow(glyph, title, sub, shape) {
        if (granted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph("check_circle", size = 18.dp, color = pal.acc, fill = true)
                Spacer(Modifier.width(6.dp))
                Lbl("On", size = 13.sp, color = pal.acc)
            }
        } else {
            AccentButton("Allow", height = 32.dp, onClick = onClick)
        }
    }
}
