package com.example.drumsamplebluepill

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.drumsamplebluepill.ui.DrumPadScreen
import com.example.drumsamplebluepill.ui.theme.DrumSAMPLEBluePIllTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DrumViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DrumSAMPLEBluePIllTheme(darkTheme = false) {
                DrumPadScreen(viewModel = viewModel)
            }
        }
    }
}
