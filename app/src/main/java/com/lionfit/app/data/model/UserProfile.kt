package com.lionfit.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    @SerialName("id")
    val id: String,

    @SerialName("full_name")
    val fullName: String,

    @SerialName("email")
    val email: String? = null,

    @SerialName("phone_number")
    val phoneNumber: String? = null,

    @SerialName("birth_date")
    val birthDate: String? = null,

    @SerialName("gender")
    val gender: String? = null,

    @SerialName("weight_kg")
    val weightKg: Double,

    @SerialName("height_cm")
    val heightCm: Double,

    @SerialName("profile_pic_url")
    val profilePicUrl: String? = null
)