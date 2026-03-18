package com.bmarthi.hello_android.geojson

/**
 * Ray-casting algorithm to determine if a point is inside a polygon ring.
 * Ring is a list of [lng, lat] coordinate pairs.
 */
fun isPointInRing(lat: Double, lng: Double, ring: List<List<Double>>): Boolean {
    var inside = false
    var j = ring.size - 1
    for (i in ring.indices) {
        val yi = ring[i][1] // lat
        val xi = ring[i][0] // lng
        val yj = ring[j][1]
        val xj = ring[j][0]

        if ((yi > lat) != (yj > lat) &&
            lng < (xj - xi) * (lat - yi) / (yj - yi) + xi
        ) {
            inside = !inside
        }
        j = i
    }
    return inside
}

/**
 * Check if point is inside a polygon (first ring is outer boundary, rest are holes).
 */
fun isPointInPolygon(lat: Double, lng: Double, rings: List<List<List<Double>>>): Boolean {
    if (rings.isEmpty()) return false
    // Must be inside outer ring
    if (!isPointInRing(lat, lng, rings[0])) return false
    // Must be outside all holes
    for (i in 1 until rings.size) {
        if (isPointInRing(lat, lng, rings[i])) return false
    }
    return true
}

/**
 * Check if point is inside any polygon of a MultiPolygon.
 */
fun isPointInMultiPolygon(lat: Double, lng: Double, coordinates: List<List<List<List<Double>>>>): Boolean {
    return coordinates.any { polygon -> isPointInPolygon(lat, lng, polygon) }
}
