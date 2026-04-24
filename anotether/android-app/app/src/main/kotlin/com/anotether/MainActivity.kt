package com.anotether

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.anotether.data.AppPreferences
import com.anotether.ui.AnotetherApp
import com.anotether.ui.theme.AnotetherTheme

class MainActivity : ComponentActivity() {

    private lateinit var preferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferences = AppPreferences(applicationContext)

        // Edge-to-edge layout
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            AnotetherTheme {
                AnotetherApp(preferences = preferences)
            }
        }
    }
}
