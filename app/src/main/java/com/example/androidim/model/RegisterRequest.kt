package com.example.androidim.model

import com.google.gson.annotations.SerializedName

data class RegisterRequest(
    @SerializedName("username")
    val username: String,
    
    @SerializedName("password")
    val password: String,
    
    @SerializedName("nickname")
    val nickname: String
)







