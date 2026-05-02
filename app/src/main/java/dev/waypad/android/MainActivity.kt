package dev.waypad.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.waypad.android.ui.WaypadApp

class MainActivity : ComponentActivity() {
    private val viewModel: WaypadViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WaypadApp(viewModel)
        }
    }
}
