package com.example.transitnavproject.pathfinding

/**
 * Directed graph for pathfinding: adjacency list with edge weight.
 */
class RouteGraph {
    /** Adjacency list: node id -> list of (neighbour id, weight) */
    private val adjacency = mutableMapOf<Int, MutableList<Pair<Int, Double>>>()

    fun addEdge(from: Int, to: Int, weight: Double) {
        adjacency.getOrPut(from) { mutableListOf() }.add(Pair(to, weight))
    }

    fun neighbours(of: Int): List<Pair<Int, Double>> {
        return adjacency[of] ?: emptyList()
    }

    /** All node IDs that appear as from or to in the graph. */
    fun allNodeIds(): Set<Int> {
        val ids = mutableSetOf<Int>()
        for ((from, edges) in adjacency) {
            ids.add(from)
            for ((to, _) in edges) {
                ids.add(to)
            }
        }
        return ids
    }
}
