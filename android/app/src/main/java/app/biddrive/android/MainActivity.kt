package app.biddrive.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.biddrive.android.ui.navigation.BidDriveNavHost
import app.biddrive.android.ui.theme.BidDriveTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = (application as BidDriveApp).container.settings
        setContent {
            val mode by settings.theme.collectAsState()
            BidDriveTheme(mode) { BidDriveNavHost() }
        }
    }
}
