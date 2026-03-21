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
    private val availableVoices = mutableStateOf<List<Pair<String, String>>>(emptyList())
    private val selectedVoice = mutableStateOf<String?>(null)
    private var previewTts: SchoolZoneTtsManager? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionGranted.value = permissions.values.any { it }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
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

        val prefs = getSharedPreferences("school_zone", MODE_PRIVATE)
        selectedVoice.value = prefs.getString("tts_voice", null)

        SchoolZoneTtsManager.listEnglishVoices(this) { voices ->
            availableVoices.value = voices
        }

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
                },
                availableVoices = availableVoices.value,
                selectedVoice = selectedVoice.value,
                onVoiceSelected = { voiceName ->
                    selectedVoice.value = voiceName
                    prefs.edit().putString("tts_voice", voiceName).apply()
                    // Recreate preview TTS with new voice
                    previewTts?.shutdown()
                    previewTts = SchoolZoneTtsManager(this@MainActivity, voiceName)
                    // Restart service if running to pick up new voice
                    if (isMonitoring.value) {
                        stopMonitoring()
                        startMonitoring()
                    }
                },
                onTestVoice = {
                    if (previewTts == null) {
                        previewTts = SchoolZoneTtsManager(this@MainActivity, selectedVoice.value)
                    }
                    previewTts?.announceSchools(listOf(
                        School(name = "Sample Elementary School", ncessch = "", leaid = "", level = "Elementary")
                    ))
                }
            )
        }
    }

    private fun startMonitoring() {
        isMonitoring.value = true

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

    override fun onDestroy() {
        previewTts?.shutdown()
        super.onDestroy()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
