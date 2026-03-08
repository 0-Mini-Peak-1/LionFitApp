package com.lionfit.app.data.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    // Assuming you store coordinates as a simple custom class or a Pair<Double, Double>
    // For this example, we'll use a data class: data class Point(val lat: Double, val lng: Double)

    @TypeConverter
    fun fromString(value: String?): List<Pair<Double, Double>> {
        if (value == null) return emptyList()
        val listType = object : TypeToken<List<Pair<Double, Double>>>() {}.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun fromList(list: List<Pair<Double, Double>>?): String {
        return Gson().toJson(list)
    }
}