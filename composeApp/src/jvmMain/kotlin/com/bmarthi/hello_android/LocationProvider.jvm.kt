package com.bmarthi.hello_android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class JvmLocationProvider : LocationProvider {
    override fun isAvailable(): Boolean = true

    override suspend fun getCurrentLocation(): LatLng? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("http://ip-api.com/json/?fields=lat,lon")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                val response = connection.inputStream.bufferedReader().readText()
                connection.disconnect()

                val latRegex = """"lat":\s*(-?\d+\.?\d*)""".toRegex()
                val lonRegex = """"lon":\s*(-?\d+\.?\d*)""".toRegex()
                val lat = latRegex.find(response)?.groupValues?.get(1)?.toDoubleOrNull()
                val lon = lonRegex.find(response)?.groupValues?.get(1)?.toDoubleOrNull()
                if (lat != null && lon != null) LatLng(lat, lon) else null
            } catch (e: Exception) {
                null
            }
        }
    }
}
