package com.lionfit.app.utils

import org.osmdroid.util.GeoPoint
import java.util.concurrent.TimeUnit
import java.util.Calendar
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter

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

    fun calculateBMR(weightKg: Double, heightCm: Double, age: Int, gender: String): Double {
        // The core formula shared by both genders
        val base = (10 * weightKg) + (6.25 * heightCm) - (5 * age)

        return if (gender.equals("male", ignoreCase = true)) {
            base + 5
        } else {
            // "female" or default
            base - 161
        }
    }

    fun calculateTDEE(bmr: Double, activityFactor: Double = 1.2): Double {
        return bmr * activityFactor
    }

    fun calculateAge(birthDateStr: String?): Int {
        // 25, the leonardo lucky number :)
        if (birthDateStr.isNullOrEmpty()) return 25

        return try {
            // Parses the PostgreSQL date format ("yyyy-MM-dd")
            val birthDate = LocalDate.parse(birthDateStr, DateTimeFormatter.ISO_LOCAL_DATE)
            val currentDate = LocalDate.now()

            // Calculates the difference in years
            Period.between(birthDate, currentDate).years
        } catch (e: Exception) {
            e.printStackTrace()
            25 // Safety net fallback if the date format is weird
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