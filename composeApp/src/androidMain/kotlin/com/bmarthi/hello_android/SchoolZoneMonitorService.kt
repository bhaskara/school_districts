package com.bmarthi.hello_android

import android.Manifest
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
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

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
        ttsManager = SchoolZoneTtsManager(this)
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

    override fun onLocationChanged(location: Location) {
        if (!dataLoaded.get()) return

        scope.launch {
            val schools = withContext(Dispatchers.Default) {
                lookup.findSchools(location.latitude, location.longitude)
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
