package com.example.androidim.model

import com.google.gson.annotations.SerializedName

data class RegisterResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("message")
    val message: String,
    
    @SerializedName("user_id")
    val userId: String?
)







