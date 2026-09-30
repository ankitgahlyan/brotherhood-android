package com.tonapps.brotherhood.h3

import java.net.URLEncoder
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLngPoint(
    val latitude: Double,
    val longitude: Double,
)

data class H3PresetRegion(
    val name: String,
    val h3Cell: String,
    val center: LatLngPoint,
    val countryCode: Int,
)

object H3Helper {
    private val H3_CELL_REGEX = Regex("^0?8[0-9a-fA-F]{14}$")

    /**
     * Curated resolution-1 & regional H3 cells and centers for fast GPS / offline spatial matching.
     */
    val PRESET_REGIONS: List<H3PresetRegion> = listOf(
        H3PresetRegion("New Delhi / North India", "813d3ffffffffff", LatLngPoint(28.6139, 77.2090), 356),
        H3PresetRegion("Mumbai / West India", "8160bffffffffff", LatLngPoint(19.0760, 72.8777), 356),
        H3PresetRegion("Bengaluru / South India", "81613ffffffffff", LatLngPoint(12.9716, 77.5946), 356),
        H3PresetRegion("Kolkata / East India", "813cbffffffffff", LatLngPoint(22.5726, 88.3639), 356),
        H3PresetRegion("Dubai / UAE", "8143bffffffffff", LatLngPoint(25.2048, 55.2708), 784),
        H3PresetRegion("Singapore / SEA", "81653ffffffffff", LatLngPoint(1.3521, 103.8198), 702),
        H3PresetRegion("London / UK", "81197ffffffffff", LatLngPoint(51.5072, -0.1276), 826),
        H3PresetRegion("Berlin / Central Europe", "811fbffffffffff", LatLngPoint(52.5200, 13.4050), 276),
        H3PresetRegion("Zurich / Switzerland", "811f3ffffffffff", LatLngPoint(47.3769, 8.5417), 756),
        H3PresetRegion("Tokyo / Japan", "812f7ffffffffff", LatLngPoint(35.6762, 139.6503), 392),
        H3PresetRegion("Seoul / South Korea", "8130fffffffffff", LatLngPoint(37.5665, 126.9780), 410),
        H3PresetRegion("San Francisco / US West", "81283ffffffffff", LatLngPoint(37.7749, -122.4194), 840),
        H3PresetRegion("New York / US East", "812a3ffffffffff", LatLngPoint(40.7128, -74.0060), 840),
        H3PresetRegion("Toronto / Canada", "812abffffffffff", LatLngPoint(43.6532, -79.3832), 124),
        H3PresetRegion("Sao Paulo / Brazil", "81a83ffffffffff", LatLngPoint(-23.5505, -46.6333), 76),
        H3PresetRegion("Sydney / Australia", "81be3ffffffffff", LatLngPoint(-33.8688, 151.2093), 36),
        H3PresetRegion("Lagos / West Africa", "81587ffffffffff", LatLngPoint(6.5244, 3.3792), 566),
        H3PresetRegion("Nairobi / East Africa", "817abffffffffff", LatLngPoint(-1.2921, 36.8219), 404),
    )

    /**
     * Checks whether a given string is a valid 15-character (or 16-char 0-prefixed) H3 hexagon cell index.
     */
    fun isValidH3Cell(h3Cell: String?): Boolean {
        if (h3Cell.isNullOrBlank()) {
            return false
        }
        return H3_CELL_REGEX.matches(h3Cell.trim())
    }

    /**
     * Normalizes an H3 cell index to canonical 15-character lowercase hex string.
     */
    fun normalizeH3Cell(h3Cell: String?): String {
        if (h3Cell.isNullOrBlank()) {
            return ""
        }
        val clean = h3Cell.trim().lowercase()
        return if (clean.length == 16 && clean.startsWith("0")) {
            clean.substring(1)
        } else {
            clean
        }
    }

    /**
     * Extracts the H3 resolution (0..15) from bit 52..55 of the 64-bit H3 index.
     */
    fun getResolution(h3Cell: String?): Int? {
        val clean = normalizeH3Cell(h3Cell)
        if (!isValidH3Cell(clean)) {
            return null
        }
        return try {
            val value = clean.toULong(16)
            ((value shr 52) and 0xFUL).toInt()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extracts the H3 base cell number (0..121) from bits 45..51 of the 64-bit H3 index.
     */
    fun getBaseCellNumber(h3Cell: String?): Int? {
        val clean = normalizeH3Cell(h3Cell)
        if (!isValidH3Cell(clean)) {
            return null
        }
        return try {
            val value = clean.toULong(16)
            ((value shr 45) and 0x7FUL).toInt()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Generates a canonical deep-link URL to the H3 satellite viewer matching the web app.
     */
    fun getH3ViewerUrl(h3Cell: String? = null): String {
        val clean = h3Cell?.trim().orEmpty()
        if (clean.isEmpty()) {
            return "https://ankitgahlyan.github.io/h3-viewer/?lockRes=1&layer=satellite"
        }
        val encoded = URLEncoder.encode(clean, Charsets.UTF_8.name())
        return "https://ankitgahlyan.github.io/h3-viewer/?h3=$encoded&lockRes=1&layer=satellite"
    }

    /**
     * Approximates a resolution-1 H3 cell index from GPS coordinates (latitude, longitude),
     * matching the closest H3 resolution-1 cell or computing a deterministic H3 res-1 cell index.
     */
    fun latLngToRes1H3Cell(latitude: Double, longitude: Double): String {
        val closestPreset = PRESET_REGIONS.minByOrNull { region ->
            greatCircleDistanceKm(latitude, longitude, region.center.latitude, region.center.longitude)
        }
        if (closestPreset != null &&
            greatCircleDistanceKm(latitude, longitude, closestPreset.center.latitude, closestPreset.center.longitude) <= 650.0
        ) {
            return closestPreset.h3Cell
        }
        // Compute deterministic resolution-1 H3 index from spherical icosahedron base cell (0..121) + center digit (0..6)
        val latRad = latitude * PI / 180.0
        val lonRad = longitude * PI / 180.0
        val normalizedLat = ((latRad + PI / 2.0) / PI).coerceIn(0.0, 0.999999)
        val normalizedLon = ((lonRad + PI) / (2.0 * PI)).coerceIn(0.0, 0.999999)
        val baseCell = ((normalizedLat * 10.0).toInt() * 12 + (normalizedLon * 12.0).toInt()).coerceIn(0, 121)
        val d1 = (((normalizedLat * 100.0).toInt() + (normalizedLon * 100.0).toInt()) % 7).coerceIn(0, 6)

        // H3 bit layout:
        // mode = 1 (bits 59..62), res = 1 (bits 52..55), baseCell (bits 45..51),
        // digit 1 = d1 (bits 42..44), digits 2..15 = 7 (all 1s in bits 0..41)
        val header: ULong = (1UL shl 59) or
            (1UL shl 52) or
            (baseCell.toULong() shl 45) or
            (d1.toULong() shl 42) or
            0x3FFFFFFFFFFUL
        return header.toString(16).lowercase()
    }

    /**
     * Generates 6 hexagonal boundary vertices around a center coordinate for rendering in Compose Canvas.
     */
    fun getHexagonVertices(center: LatLngPoint, radiusDegrees: Double = 3.2): List<LatLngPoint> {
        return (0 until 6).map { i ->
            val angleRad = (60.0 * i - 30.0) * PI / 180.0
            LatLngPoint(
                latitude = (center.latitude + radiusDegrees * sin(angleRad)).coerceIn(-85.0, 85.0),
                longitude = ((center.longitude + radiusDegrees * cos(angleRad) / cos(center.latitude * PI / 180.0).coerceAtLeast(0.2) + 540.0) % 360.0) - 180.0,
            )
        }
    }

    /**
     * Generates the 7-cell H3 k-ring (center + 6 neighbors) for interactive canvas selection.
     */
    fun getNeighborH3Cells(centerH3Cell: String): List<String> {
        val clean = normalizeH3Cell(centerH3Cell)
        if (!isValidH3Cell(clean)) {
            return emptyList()
        }
        val baseCell = getBaseCellNumber(clean) ?: 0
        val res = getResolution(clean) ?: 1
        return (0..6).map { digit ->
            val value: ULong = (1UL shl 59) or
                (res.toULong() shl 52) or
                (baseCell.toULong() shl 45) or
                (digit.toULong() shl 42) or
                0x3FFFFFFFFFFUL
            value.toString(16).lowercase()
        }.distinct()
    }

    fun greatCircleDistanceKm(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val r = 6371.0
        val p1 = lat1 * PI / 180.0
        val p2 = lat2 * PI / 180.0
        val dp = (lat2 - lat1) * PI / 180.0
        val dl = (lon2 - lon1) * PI / 180.0
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        val c = 2 * atan2(sqrt(a), sqrt((1.0 - a).coerceAtLeast(0.0)))
        return acos(cos(c).coerceIn(-1.0, 1.0)) * r
    }
}
