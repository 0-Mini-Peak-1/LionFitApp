package com.lionfit.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class FoodItem(
    val id: Int? = null,
    val name: String,
    val calories: Int,
    val fat: Int = 0,
    val carb: Int = 0,
    val protein: Int = 0,
    val serving_size: String = "1 serving"
)
