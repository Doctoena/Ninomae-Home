package dev.ninomae.home

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    private val ui = Ui()
    private lateinit var st: HomeState

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
        setContent {
            NinomaeTheme {
                // Home never closes on back; back only closes what's open.
                BackHandler { if (ui.layer != Layer.None) ui.close() else ui.editing = false }
                Home(st, ui)
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
        applyStatusBar()
    }

    override fun onStop() {
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
