package com.firsttrial.data

import android.location.Location

data class HealthData(
    val heartRate: Double = 0.0,
    val steps: Long = 0L,
    val distance: Double = 0.0,
    val calories: Double = 0.0,
    val speed: Double = 0.0,
    val location: Location? = null
) {
    companion object {
        // Average stride length in meters (varies by person, ~0.65-0.75m for adults)
        private const val AVERAGE_STRIDE_LENGTH = 0.70 // meters

        // Calculate distance from steps
        fun calculateDistance(steps: Long): Double = (steps * AVERAGE_STRIDE_LENGTH) / 1000.0 // km
    }
}
