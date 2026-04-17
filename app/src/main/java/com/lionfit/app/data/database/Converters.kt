package com.lionfit.app.data.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lionfit.app.data.model.RoutePoint

class Converters {

    @TypeConverter
    fun fromRoutePointList(list: List<List<RoutePoint>>?): String? {
        if (list == null) return null
        return Gson().toJson(list)
    }

    @TypeConverter
    fun toRoutePointList(jsonString: String?): List<List<RoutePoint>>? {
        if (jsonString == null) return null
        // Update the TypeToken to List<List<RoutePoint>>
        val type = object : TypeToken<List<List<RoutePoint>>>() {}.type
        return Gson().fromJson(jsonString, type)
    }
}