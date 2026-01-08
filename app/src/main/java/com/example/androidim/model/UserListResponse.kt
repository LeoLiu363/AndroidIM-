package com.example.androidim.model

import com.google.gson.annotations.SerializedName

data class UserListResponse(
    @SerializedName("users")
    val users: List<UserInfoItem>
)

data class UserInfoItem(
    @SerializedName("user_id")
    val userId: String,
    
    @SerializedName("username")
    val username: String,
    
    @SerializedName("nickname")
    val nickname: String?,
    
    @SerializedName("online")
    val online: Boolean
)







