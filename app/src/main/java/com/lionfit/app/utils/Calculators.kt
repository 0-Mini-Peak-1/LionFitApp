package com.lionfit.app.utils

import org.osmdroid.util.GeoPoint
import java.util.concurrent.TimeUnit

object Calculators {

// Converts milliseconds into a readable stopwatch format (MM:SS or HH:MM:SS)
    fun getFormattedStopWatchTime(ms: Long): String {
        var milliseconds = ms
        val hours = TimeUnit.MILLISECONDS.toHours(milliseconds)
        milliseconds -= TimeUnit.HOURS.toMillis(hours)

        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
        milliseconds -= TimeUnit.MINUTES.toMillis(minutes)

        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds)

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    // Distance calculation
    fun calculatePolylineLength(pathSegments: List<List<GeoPoint>>): Float {
        var totalDistance = 0f

        // Loop through every separate running segment
        for (segment in pathSegments) {
            // Add up the distance within this specific segment
            for (i in 0 until segment.size - 1) {
                val pos1 = segment[i]
                val pos2 = segment[i + 1]
                totalDistance += pos1.distanceToAsDouble(pos2).toFloat()
            }
        }
        return totalDistance
    }
}