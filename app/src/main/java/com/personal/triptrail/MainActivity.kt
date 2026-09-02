package com.personal.triptrail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.personal.triptrail.data.TripRepository
import com.personal.triptrail.ui.TripTrailApp
import com.personal.triptrail.ui.TripTrailTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val repository = TripRepository(applicationContext)
        val incoming = intent?.data
        setContent {
            TripTrailTheme { TripTrailApp(repository = repository, initialSharedUri = incoming) }
        }
    }
}
