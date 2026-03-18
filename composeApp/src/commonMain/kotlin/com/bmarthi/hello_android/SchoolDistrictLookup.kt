package com.bmarthi.hello_android

import com.bmarthi.hello_android.geojson.FeatureCollection
import com.bmarthi.hello_android.geojson.Feature
import com.bmarthi.hello_android.geojson.isPointInMultiPolygon
import helloandroid.composeapp.generated.resources.Res
import kotlinx.serialization.json.Json

data class School(
    val name: String,
    val ncessch: String,
    val leaid: String,
    val level: String
)

private data class BoundedFeature(
    val feature: Feature,
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double
)

class SchoolLookup {
    private var boundedFeatures: List<BoundedFeature>? = null

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load() {
        if (boundedFeatures != null) return
        val bytes = Res.readBytes("files/attendance_boundaries/merged_all.geojson")
        val text = bytes.decodeToString()
        val collection = json.decodeFromString<FeatureCollection>(text)
        boundedFeatures = collection.features.map { feature ->
            val coords = feature.geometry.coordinates
            var minLat = Double.MAX_VALUE
            var maxLat = -Double.MAX_VALUE
            var minLng = Double.MAX_VALUE
            var maxLng = -Double.MAX_VALUE
            for (polygon in coords) {
                for (ring in polygon) {
                    for (point in ring) {
                        val lng = point[0]
                        val lat = point[1]
                        if (lat < minLat) minLat = lat
                        if (lat > maxLat) maxLat = lat
                        if (lng < minLng) minLng = lng
                        if (lng > maxLng) maxLng = lng
                    }
                }
            }
            BoundedFeature(feature, minLat, maxLat, minLng, maxLng)
        }
    }

    fun findSchools(lat: Double, lng: Double): List<School> {
        val features = boundedFeatures ?: return emptyList()
        return features.filter { bf ->
            lat in bf.minLat..bf.maxLat && lng in bf.minLng..bf.maxLng &&
                isPointInMultiPolygon(lat, lng, bf.feature.geometry.coordinates)
        }.map { bf ->
            val p = bf.feature.properties
            School(
                name = p.schnam,
                ncessch = p.ncessch,
                leaid = p.leaid,
                level = p.level
            )
        }
    }
}
