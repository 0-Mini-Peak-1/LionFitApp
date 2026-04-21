package com.lionfit.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PrivateFood(
    val id: Int? = null,
    val name: String,
    val calories: Int,
    val fat: Int = 0,
    val carb: Int = 0,
    val protein: Int = 0,
    @SerialName("serving_size")
    val servingSize: String = "1 serving",
    @SerialName("user_id")
    val userId: String
)
