package app.latch

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import app.latch.ui.LatchScreen
import app.latch.ui.LatchViewModel
import app.latch.ui.LocalIconLoader
import app.latch.ui.theme.LatchTheme

class MainActivity : ComponentActivity() {

    private val vm: LatchViewModel by viewModels { LatchViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        // White text everywhere, so keep light system-bar icons whatever the system theme.
        val bars = SystemBarStyle.dark(Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        super.onCreate(savedInstanceState)
        val app = application as LatchApp
        setContent {
            LatchTheme {
                CompositionLocalProvider(LocalIconLoader provides app.icons) {
                    LatchScreen(vm)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            // Same check as AOSP Launcher3: we had focus and weren't merely brought to front.
            val alreadyOnHome = hasWindowFocus() &&
                (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
            if (alreadyOnHome) vm.resetHome() else vm.setDrawerOpen(false)
        }
    }
}
