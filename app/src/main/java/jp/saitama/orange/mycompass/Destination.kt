package jp.saitama.orange.mycompass

import kotlinx.serialization.Serializable

@Serializable
data class Destination(
    val id: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double
) {
    companion object {
        const val MAX_DESTINATIONS = 5
    }
}