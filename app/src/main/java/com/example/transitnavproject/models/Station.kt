package com.example.transitnavproject.models

data class Station(
    val id: Int,
    val name: String,
    val code: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)
