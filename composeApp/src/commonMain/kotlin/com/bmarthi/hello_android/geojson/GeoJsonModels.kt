package com.bmarthi.hello_android.geojson

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable
data class FeatureCollection(
    val type: String,
    val features: List<Feature>
)

@Serializable
data class Feature(
    val type: String,
    val properties: SchoolProperties,
    val geometry: Geometry
)

@Serializable
data class SchoolProperties(
    val schnam: String = "",
    val ncessch: String = "",
    val leaid: String = "",
    val level: String = ""
)

/**
 * Geometry that normalizes both Polygon and MultiPolygon coordinates
 * to MultiPolygon format (4 levels of nesting).
 */
@Serializable(with = GeometrySerializer::class)
data class Geometry(
    val type: String,
    val coordinates: List<List<List<List<Double>>>>
)

object GeometrySerializer : KSerializer<Geometry> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Geometry")

    override fun serialize(encoder: Encoder, value: Geometry) {
        throw UnsupportedOperationException("Serialization not needed")
    }

    override fun deserialize(decoder: Decoder): Geometry {
        val jsonDecoder = decoder as kotlinx.serialization.json.JsonDecoder
        val obj = jsonDecoder.decodeJsonElement().jsonObject
        val type = obj["type"]!!.jsonPrimitive.content
        val rawCoords = obj["coordinates"]!!.jsonArray

        val multiPolygonCoords: List<List<List<List<Double>>>> = when (type) {
            "MultiPolygon" -> {
                // [polygon][ring][point][coord]
                rawCoords.map { polygon ->
                    polygon.jsonArray.map { ring ->
                        ring.jsonArray.map { point ->
                            point.jsonArray.map { it.jsonPrimitive.double }
                        }
                    }
                }
            }
            "Polygon" -> {
                // [ring][point][coord] — wrap in one more list to normalize
                listOf(
                    rawCoords.map { ring ->
                        ring.jsonArray.map { point ->
                            point.jsonArray.map { it.jsonPrimitive.double }
                        }
                    }
                )
            }
            else -> emptyList()
        }

        return Geometry(type = type, coordinates = multiPolygonCoords)
    }
}
