package com.trackit.expense.data.remote.dto

import com.google.gson.annotations.SerializedName

data class UserProfileDto(
    @SerializedName("_id")      val uid: String?,
    @SerializedName("name")     val name: String?,
    @SerializedName("email")    val email: String?,
    @SerializedName("photoUrl") val photoUrl: String?,
    @SerializedName("upiId")    val upiId: String?
)
