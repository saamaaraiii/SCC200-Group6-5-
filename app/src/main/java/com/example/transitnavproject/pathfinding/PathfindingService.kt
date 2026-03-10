package com.example.transitnavproject.pathfinding

import com.example.transitnavproject.models.RouteResult
import java.util.*

class PathfindingService {
    private var graph: RouteGraph = RouteGraph()

    fun replaceGraph(newGraph: RouteGraph) {
        graph = newGraph
    }

    fun findPathBFS(originId: Int, destinationId: Int): RouteResult? {
        if (originId == destinationId) {
            return RouteResult(stationIds = listOf(originId))
        }
        val visited = mutableSetOf<Int>()
        val queue: Queue<Pair<Int, List<Int>>> = LinkedList()
        queue.add(originId to listOf(originId))
        visited.add(originId)

        while (queue.isNotEmpty()) {
            val (node, path) = queue.poll()
            for ((neighbour, _) in graph.neighbours(of = node)) {
                if (neighbour == destinationId) {
                    return RouteResult(stationIds = path + neighbour)
                }
                if (!visited.contains(neighbour)) {
                    visited.add(neighbour)
                    queue.add(neighbour to (path + neighbour))
                }
            }
        }
        return null
    }

    fun findPathDijkstra(originId: Int, destinationId: Int, useDuration: Boolean = true): RouteResult? {
        if (originId == destinationId) {
            return RouteResult(stationIds = listOf(originId), totalDurationMins = 0)
        }
        val dist = mutableMapOf<Int, Double>()
        dist[originId] = 0.0
        val prev = mutableMapOf<Int, Int>()
        val pq = PriorityQueue<Pair<Double, Int>>(compareBy { it.first })
        pq.add(0.0 to originId)

        while (pq.isNotEmpty()) {
            val (d, node) = pq.poll()
            if (node == destinationId) {
                val path = mutableListOf<Int>()
                var cur: Int? = destinationId
                while (cur != null) {
                    path.add(cur)
                    cur = prev[cur]
                }
                path.reverse()
                val totalWeight = dist[destinationId] ?: 0.0
                return if (useDuration) {
                    RouteResult(stationIds = path, totalDurationMins = totalWeight.toInt())
                } else {
                    RouteResult(stationIds = path, totalDistanceKm = totalWeight)
                }
            }
            if (d > (dist[node] ?: Double.POSITIVE_INFINITY)) continue

            for ((neighbour, weight) in graph.neighbours(of = node)) {
                val alt = d + weight
                if (alt < (dist[neighbour] ?: Double.POSITIVE_INFINITY)) {
                    dist[neighbour] = alt
                    prev[neighbour] = node
                    pq.add(alt to neighbour)
                }
            }
        }
        return null
    }
}
