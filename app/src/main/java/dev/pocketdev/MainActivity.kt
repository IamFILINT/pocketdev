package dev.pocketdev

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pocketdev.ui.PocketDevApp
import dev.pocketdev.ui.theme.PocketDevTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as PocketDevApplication).container

        setContent {
            val themeMode by container.themeMode.collectAsStateWithLifecycle()
            PocketDevTheme(themeMode = themeMode) {
                PocketDevApp()
            }
        }
    }
}