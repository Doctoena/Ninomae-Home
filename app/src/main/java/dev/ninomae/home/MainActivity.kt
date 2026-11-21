package dev.ninomae.home

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val ui = Ui()
    private lateinit var st: HomeState

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        ui.permissionTick++
        Glance.refresh(this, st)
    }

    private val saveBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@registerForActivityResult
        val ok = runCatching { contentResolver.openOutputStream(uri)?.use { it.write(st.exportJson().toByteArray()) } }.isSuccess
        Toast.makeText(this, if (ok) "Home saved" else "Couldn't save it", Toast.LENGTH_SHORT).show()
    }

    private val openBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        val text = runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
        if (text != null && st.importJson(text)) {
            Toast.makeText(this, "Home restored", Toast.LENGTH_SHORT).show()
            recreate()
        } else {
            Toast.makeText(this, "That isn't a Ninomae Home backup", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickWallpaper = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@registerForActivityResult
        Toast.makeText(this, "Setting your wallpaper…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val set = Glass.setWallpaper(this@MainActivity, uri, st.glassFrost)
            if (set != null) st.setWallpaper(set)
            Toast.makeText(this@MainActivity, if (set != null) "Wallpaper set" else "Couldn't set that picture", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        window.isNavigationBarContrastEnforced = false
        st = HomeState.get(this)
        applyStatusBar()
        st.load()
        AppWidgets.init(this)
        ui.askPermissions = { permissions.launch(it) }
        ui.backup = { saveBackup.launch("ninomae-home-${java.time.LocalDate.now()}.json") }
        ui.pickWallpaper = { pickWallpaper.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        ui.restore = { openBackup.launch(arrayOf("application/json", "text/plain", "*/*")) }
        setContent {
            NinomaeTheme(st.colourSource, st.swatch, st.wallSeed) {
              androidx.compose.runtime.CompositionLocalProvider(LocalThemedIcons provides (st.iconStyle == 1), LocalIconShape provides st.iconShape) {
                // Home never closes on back; back only closes what's open.
                BackHandler { if (ui.layer != Layer.None) ui.back() else ui.editing = false }
                Home(st, ui)
              }
            }
        }
    }

    /**
     * Each screen picks its own status bar: Ninomae's bar replaces Android's (a swipe from the top edge
     * still shows it briefly), or Samsung's stays like in every other app.
     */
    fun applyStatusBar() {
        val cover = resources.configuration.screenWidthDp < 600
        val own = if (cover) st.barCover else st.barInner
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (own) hide(WindowInsetsCompat.Type.statusBars()) else show(WindowInsetsCompat.Type.statusBars())
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Folding or unfolding switches screens, and each has its own choice.
        applyStatusBar()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while already home: close whatever is open, then go to the first page.
        if (ui.layer == Layer.None && !ui.editing) ui.jumpToPage = 0
        ui.close()
        ui.editing = false
    }

    override fun onStart() {
        super.onStart()
        Status.start(this)
        AppWidgets.start()
    }

    override fun onResume() {
        super.onResume()
        st.refreshAccess()
        st.checkWallpaper()
        applyStatusBar()
        Glance.refresh(this, st)
        Clock.start()
        Clock.tick()
    }

    override fun onStop() {
        Clock.stop()
        Status.stop(this)
        AppWidgets.stop()
        super.onStop()
    }

    @Deprecated("Widget binding and setup screens still report here")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!AppWidgets.onResult(requestCode, resultCode)) {
            @Suppress("DEPRECATION")
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyStatusBar()
    }
}
