package com.example.transitnavproject.pathfinding

import com.example.transitnavproject.models.RouteResult
import java.util.*

/**
 * Pathfinding: BFS (fewest segments) and Dijkstra (shortest by time or distance).
 */
class PathfindingService(private var graph: RouteGraph = RouteGraph()) {

    fun replaceGraph(newGraph: RouteGraph) {
        graph = newGraph
    }

    /** BFS: returns one path with fewest segments (unweighted). */
    fun findPathBFS(originId: Int, destinationId: Int): RouteResult? {
        if (originId == destinationId) {
            return RouteResult(listOf(originId))
        }

        val visited = mutableSetOf<Int>()
        val queue: Deque<Pair<Int, List<Int>>> = ArrayDeque()

        queue.add(Pair(originId, listOf(originId)))
        visited.add(originId)

        while (queue.isNotEmpty()) {
            val (node, path) = queue.removeFirst()
            for ((neighbour, _) in graph.neighbours(node)) {
                if (neighbour == destinationId) {
                    return RouteResult(path + neighbour)
                }
                if (neighbour !in visited) {
                    visited.add(neighbour)
                    queue.add(Pair(neighbour, path + neighbour))
                }
            }
        }
        return null
    }

    /** Dijkstra: returns one shortest path by weight. */
    fun findPathDijkstra(originId: Int, destinationId: Int, useDuration: Boolean = true): RouteResult? {
        if (originId == destinationId) {
            return RouteResult(listOf(originId), totalDurationMins = 0, totalDistanceKm = 0.0)
        }

        val dist = mutableMapOf<Int, Double>()
        dist[originId] = 0.0
        val prev = mutableMapOf<Int, Int>()
        
        // PriorityQueue stores Pair(distance, nodeId)
        val pq = PriorityQueue<Pair<Double, Int>>(compareBy { it.first })
        pq.add(Pair(0.0, originId))

        while (pq.isNotEmpty()) {
            val (d, node) = pq.poll() ?: break

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
                    RouteResult(path, totalDurationMins = totalWeight.toInt())
                } else {
                    RouteResult(path, totalDistanceKm = totalWeight)
                }
            }

            if (d > (dist[node] ?: Double.POSITIVE_INFINITY)) continue

            for ((neighbour, weight) in graph.neighbours(node)) {
                val alt = d + weight
                if (alt < (dist[neighbour] ?: Double.POSITIVE_INFINITY)) {
                    dist[neighbour] = alt
                    prev[neighbour] = node
                    pq.add(Pair(alt, neighbour))
                }
            }
        }
        return null
    }
}
