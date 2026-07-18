package com.geovideos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geovideos.app.ui.GeoVideosApp
import com.geovideos.app.ui.GeoVideosViewModel
import com.geovideos.app.ui.theme.GeoVideosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GeoVideosTheme {
                val viewModel: GeoVideosViewModel = viewModel()
                GeoVideosApp(viewModel)
            }
        }
    }
}
