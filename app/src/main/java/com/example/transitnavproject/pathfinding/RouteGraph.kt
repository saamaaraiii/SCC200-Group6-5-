package com.example.transitnavproject.pathfinding

class RouteGraph {
    private val adjacencyList = mutableMapOf<Int, MutableList<Pair<Int, Double>>>()

    fun addEdge(from: Int, to: Int, weight: Double) {
        adjacencyList.getOrPut(from) { mutableListOf() }.add(to to weight)
    }

    fun neighbours(of: Int): List<Pair<Int, Double>> {
        return adjacencyList[of] ?: emptyList()
    }
}
