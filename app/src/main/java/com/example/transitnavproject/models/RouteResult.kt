package com.example.transitnavproject.models

/**
 * A single path between two stations (list of station IDs in order).
 */
data class RouteResult(
    /** Station IDs from origin to destination (inclusive). */
    val stationIds: List<Int>,
    /** Total duration in minutes (Dijkstra) or null (BFS). */
    val totalDurationMins: Int? = null,
    /** Total distance in km (Dijkstra) or null (BFS). */
    val totalDistanceKm: Double? = null
) {
    /** Number of segments (legs). */
    val segmentCount: Int
        get() = if (stationIds.isEmpty()) 0 else stationIds.size - 1
}
