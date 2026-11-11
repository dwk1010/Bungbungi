package com.bungbungi.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bungbungi.app.signal.NaviLocationSource
import com.bungbungi.app.ui.BungApp

class MainActivity : ComponentActivity() {

    private val engine get() = (application as BungApp).engine

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        engine.onPermissionsGranted()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissions.launch(
            arrayOf(
                NaviLocationSource.PERMISSION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.RECORD_AUDIO,
            )
        )
        setContent {
            val state by engine.state.collectAsStateWithLifecycle()
            val signals by engine.hub.state.collectAsStateWithLifecycle()
            val log by engine.hub.log.collectAsStateWithLifecycle()
            // Pleos Connect 화면은 160dpi라 글자가 작게 보여서 전체를 1.25배로 키운다 (운전석에서 읽기 쉽게)
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density * 1.25f, base.fontScale)) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    BungApp(engine, state, signals, log)
                }
            }
        }
    }
}
