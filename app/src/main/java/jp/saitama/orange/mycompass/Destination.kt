package jp.saitama.orange.mycompass

import kotlinx.serialization.Serializable

@Serializable
data class Destination(
    val id: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double
)