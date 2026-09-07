package com.personal.triptrail

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.IntentCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.personal.triptrail.data.TripRepository
import com.personal.triptrail.ui.TripTrailApp
import com.personal.triptrail.ui.TripTrailTheme

class MainActivity : ComponentActivity() {
    private val incomingUri = mutableStateOf<Uri?>(null)
    private val incomingVersion = mutableStateOf(0)

    private fun receiveFile(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            else -> null
        }?.takeIf { it.scheme == "content" || it.scheme == "file" }
        incomingUri.value = uri
        incomingVersion.value += 1
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveFile(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val repository = TripRepository(applicationContext)
        receiveFile(intent)
        setContent {
            val dark = isSystemInDarkTheme()
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            TripTrailTheme { TripTrailApp(repository = repository, initialSharedUri = incomingUri.value, incomingVersion = incomingVersion.value) }
        }
    }
}
