package com.example.androidim.model

import com.google.gson.annotations.SerializedName

/**
 * 好友相关数据模型
 * 对应《05-功能扩展与接口设计.md》中好友体系的数据结构
 */

// 发送好友申请请求（使用对方用户名）
data class FriendApplyRequest(
    // 目标用户名，由服务端根据用户名查找 user_id
    @SerializedName("target_username")
    val targetUsername: String,
    @SerializedName("greeting")
    val greeting: String? = null,
    @SerializedName("remark")
    val remark: String? = null
)

// 好友申请响应
data class FriendApplyResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("apply_id")
    val applyId: String?,
    @SerializedName("message")
    val message: String?
)

// 好友申请通知（被申请方收到）
data class FriendApplyNotify(
    @SerializedName("apply_id")
    val applyId: String,
    @SerializedName("from_user")
    val fromUser: FriendUserBrief,
    @SerializedName("greeting")
    val greeting: String?,
    @SerializedName("created_at")
    val createdAt: Long
)

// 通知/好友中内嵌的用户简要信息
data class FriendUserBrief(
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("username")
    val username: String,
    @SerializedName("nickname")
    val nickname: String? = null,
    @SerializedName("avatar_url")
    val avatarUrl: String? = null
)

// 处理好友申请请求
data class FriendHandleRequest(
    @SerializedName("apply_id")
    val applyId: String,
    // "accept" / "reject"
    @SerializedName("action")
    val action: String,
    @SerializedName("remark")
    val remark: String? = null
)

// 处理好友申请响应
data class FriendHandleResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("action")
    val action: String?,
    @SerializedName("friend")
    val friend: FriendInfo?
)

// 好友信息（用于好友列表等）
data class FriendInfo(
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("username")
    val username: String,
    @SerializedName("nickname")
    val nickname: String? = null,
    @SerializedName("avatar_url")
    val avatarUrl: String? = null,
    @SerializedName("remark")
    val remark: String? = null,
    @SerializedName("group_name")
    val groupName: String? = null,
    @SerializedName("is_blocked")
    val isBlocked: Boolean = false,
    @SerializedName("online")
    val online: Boolean = false
)

// 好友列表响应
data class FriendListResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("friends")
    val friends: List<FriendInfo>
)

// 删除好友请求
data class FriendDeleteRequest(
    @SerializedName("friend_user_id")
    val friendUserId: String
)

// 删除好友响应
data class FriendDeleteResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("message")
    val message: String?
)

// 拉黑/解除拉黑请求
data class FriendBlockRequest(
    @SerializedName("target_user_id")
    val targetUserId: String,
    @SerializedName("block")
    val block: Boolean
)

// 拉黑响应
data class FriendBlockResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("block")
    val block: Boolean
)


