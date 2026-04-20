package com.lionfit.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class FoodItem(
    val id: Int? = null,
    val name: String,
    val calories: Int,
    val category: String? = null,
    val image_url: String? = null
)
