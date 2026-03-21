package com.bmarthi.hello_android

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class SchoolZoneMonitorService : Service(), LocationListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val lookup = SchoolLookup()
    private val dataLoaded = AtomicBoolean(false)
    private var previousIds = emptySet<String>()

    private lateinit var notificationHelper: SchoolZoneNotificationHelper
    private lateinit var ttsManager: SchoolZoneTtsManager
    private lateinit var locationManager: LocationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        notificationHelper = SchoolZoneNotificationHelper(this)
        val prefs = getSharedPreferences("school_zone", MODE_PRIVATE)
        val voiceName = prefs.getString("tts_voice", null)
        ttsManager = SchoolZoneTtsManager(this, voiceName)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        notificationHelper.createChannels()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                SchoolZoneNotificationHelper.SERVICE_NOTIFICATION_ID,
                notificationHelper.buildServiceNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(
                SchoolZoneNotificationHelper.SERVICE_NOTIFICATION_ID,
                notificationHelper.buildServiceNotification()
            )
        }

        scope.launch {
            withContext(Dispatchers.Default) {
                lookup.load()
            }
            dataLoaded.set(true)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val hasPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                5000L,
                50f,
                this
            )
        }

        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private suspend fun getGpsFix(): Location? {
        // Try last known GPS location first (often fresh enough)
        val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        if (lastGps != null && lastGps.elapsedRealtimeNanos >
            android.os.SystemClock.elapsedRealtimeNanos() - 3_000_000_000L) {
            // GPS fix less than 3 seconds old
            return lastGps
        }

        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) return null

        return suspendCancellableCoroutine { cont ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    locationManager.removeUpdates(this)
                    cont.resume(location)
                }
                @Deprecated("Deprecated in API")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, mainLooper)
            cont.invokeOnCancellation { locationManager.removeUpdates(listener) }
        }
    }

    override fun onLocationChanged(location: Location) {
        if (!dataLoaded.get()) return

        scope.launch {
            // Network provider triggered us — now get a precise GPS fix
            val preciseLocation = getGpsFix() ?: location
            val schools = withContext(Dispatchers.Default) {
                lookup.findSchools(preciseLocation.latitude, preciseLocation.longitude)
            }
            val currentIds = schools.map { it.ncessch }.toSet()
            val newSchools = schools.filter { it.ncessch !in previousIds }

            if (newSchools.isNotEmpty()) {
                notificationHelper.sendSchoolAlertNotification(newSchools)
                ttsManager.announceSchools(newSchools)
            }
            previousIds = currentIds
        }
    }

    override fun onDestroy() {
        locationManager.removeUpdates(this)
        ttsManager.shutdown()
        scope.cancel()
        super.onDestroy()
    }
}
