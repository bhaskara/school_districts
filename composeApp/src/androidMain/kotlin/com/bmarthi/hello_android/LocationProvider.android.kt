package com.bmarthi.hello_android

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AndroidLocationProvider(private val context: Context) : LocationProvider {

    override fun isAvailable(): Boolean = true

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(): LatLng? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Try to get last known location first
        val lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

        if (lastKnown != null) {
            return LatLng(lastKnown.latitude, lastKnown.longitude)
        }

        // Request a single location update
        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return null
        }

        return suspendCancellableCoroutine { cont ->
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(location: android.location.Location) {
                    locationManager.removeUpdates(this)
                    cont.resume(LatLng(location.latitude, location.longitude))
                }
                @Deprecated("Deprecated in API")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            locationManager.requestSingleUpdate(provider, listener, context.mainLooper)
            cont.invokeOnCancellation { locationManager.removeUpdates(listener) }
        }
    }
}
