package com.example.androidim.model

import com.google.gson.annotations.SerializedName

data class ChatMessage(
    @SerializedName("from_user_id")
    val fromUserId: String,
    
    @SerializedName("from_username")
    val fromUsername: String,
    
    @SerializedName("content")
    val content: String,
    
    @SerializedName("message_type")
    val messageType: String,
    
    @SerializedName("timestamp")
    val timestamp: Long,
    
    // 群聊相关字段（可选）
    @SerializedName("conversation_type")
    val conversationType: String? = "single",  // "single" 或 "group"
    
    @SerializedName("group_id")
    val groupId: String? = null,
    
    @SerializedName("to_user_id")
    val toUserId: String? = null
)




