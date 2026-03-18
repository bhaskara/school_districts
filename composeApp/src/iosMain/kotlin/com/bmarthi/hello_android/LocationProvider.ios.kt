package com.bmarthi.hello_android

class IosLocationProvider : LocationProvider {
    override fun isAvailable(): Boolean = false
    override suspend fun getCurrentLocation(): LatLng? = null
}
