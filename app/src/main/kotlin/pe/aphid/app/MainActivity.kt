package pe.aphid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import pe.aphid.app.ui.AphidRoot
import pe.aphid.core.notifications.SystemNotifier

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val openAlerts = intent?.getBooleanExtra(SystemNotifier.EXTRA_OPEN_ALERTS, false) == true
        setContent { AphidRoot(openAlerts = openAlerts) }
    }
}
