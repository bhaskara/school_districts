package com.bmarthi.hello_android

data class LatLng(val latitude: Double, val longitude: Double)

interface LocationProvider {
    fun isAvailable(): Boolean
    suspend fun getCurrentLocation(): LatLng?
}
