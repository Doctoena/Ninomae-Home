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
        hideStatusBar()
        st = HomeState.get(this)
        st.load()
        setContent {
            NinomaeTheme {
                // Home never closes on back; back only closes what's open.
                BackHandler { ui.close() }
                Home(st, ui)
            }
        }
    }

    /** Our bar replaces Android's on the home screen. A swipe from the top edge still shows it briefly. */
    private fun hideStatusBar() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while already home: close whatever is open.
        ui.close()
    }

    override fun onStart() {
        super.onStart()
        Status.start(this)
    }

    override fun onResume() {
        super.onResume()
        st.refreshAccess()
        hideStatusBar()
    }

    override fun onStop() {
        Status.stop(this)
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }
}
