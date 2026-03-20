package com.bmarthi.hello_android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val locationProvider by lazy { AndroidLocationProvider(this) }
    private val permissionGranted = mutableStateOf(false)
    private val isMonitoring = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionGranted.value = permissions.values.any { it }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // After notification permission result, proceed to request background location
        requestBackgroundLocationThenStart()
    }

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startMonitoringService()
        } else {
            isMonitoring.value = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val hasPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            permissionGranted.value = true
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        setContent {
            App(
                locationProvider = if (permissionGranted.value) locationProvider else null,
                isMonitoring = isMonitoring.value,
                onToggleMonitoring = { enabled ->
                    if (enabled) startMonitoring() else stopMonitoring()
                }
            )
        }
    }

    private fun startMonitoring() {
        isMonitoring.value = true

        // Request notification permission first on API 33+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        requestBackgroundLocationThenStart()
    }

    private fun requestBackgroundLocationThenStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                return
            }
        }

        startMonitoringService()
    }

    private fun startMonitoringService() {
        val intent = Intent(this, SchoolZoneMonitorService::class.java)
        ContextCompat.startForegroundService(this, intent)
        isMonitoring.value = true
    }

    private fun stopMonitoring() {
        stopService(Intent(this, SchoolZoneMonitorService::class.java))
        isMonitoring.value = false
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
