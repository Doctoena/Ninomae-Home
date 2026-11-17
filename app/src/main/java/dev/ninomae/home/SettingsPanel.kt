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
                    Lbl("Version 0.1", size = 12.sp, color = pal.sub)
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
        SettingRow("widgets", "Desktop widgets", "Greeting, player and jump back in", js) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Seg("Left", st.widgetSide == 1) { st.updateWidgetSide(1) }
                Seg("Right", st.widgetSide == 2) { st.updateWidgetSide(2) }
                Seg("Off", st.widgetSide == 0) { st.updateWidgetSide(0) }
            }
        }

        Lbl("Access", Modifier.padding(start = 14.dp, top = 10.dp, bottom = 4.dp), size = 13.sp, color = pal.sub)

        AccessRow("home", "Home app", "Make Ninomae your home screen", st.isDefaultHome, js) { Actions.homeSettings(ctx) }
        AccessRow("notifications", "Notification access", "The player and notifications", st.notifAccess, js) { Actions.openListenerSettings(ctx) }
        AccessRow("splitscreen_left", "Accessibility", "Split screen, pairs, lock, screenshot, power menu", st.a11yOn, js) { Actions.openAccessibility(ctx) }
        AccessRow("brightness_6", "Change system settings", "Brightness and auto-rotate", st.canWrite, corners(j, j, big, big)) { Actions.openWriteSettings(ctx) }
    }
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
