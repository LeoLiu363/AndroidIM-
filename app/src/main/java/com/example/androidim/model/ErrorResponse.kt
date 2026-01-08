package com.example.androidim.model

import com.google.gson.annotations.SerializedName

data class ErrorResponse(
    @SerializedName("error_code")
    val errorCode: Int,
    
    @SerializedName("error_message")
    val errorMessage: String
)







