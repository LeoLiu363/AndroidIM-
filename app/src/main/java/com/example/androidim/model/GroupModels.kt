package com.example.androidim.model

import com.google.gson.annotations.SerializedName

/**
 * 群聊相关数据模型
 * 对应《05-功能扩展与接口设计.md》中群聊体系的数据结构
 */

// 创建群请求
data class GroupCreateRequest(
    @SerializedName("group_name")
    val groupName: String,
    @SerializedName("avatar_url")
    val avatarUrl: String? = null,
    @SerializedName("member_user_ids")
    val memberUserIds: List<String>
)

// 群信息
data class GroupInfo(
    @SerializedName("group_id")
    val groupId: String,
    @SerializedName("group_name")
    val groupName: String,
    @SerializedName("owner_id")
    val ownerId: String,
    @SerializedName("avatar_url")
    val avatarUrl: String? = null,
    @SerializedName("announcement")
    val announcement: String? = null,
    @SerializedName("created_at")
    val createdAt: Long
)

// 创建群响应
data class GroupCreateResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("group")
    val group: GroupInfo?,
    @SerializedName("error_code")
    val errorCode: Int? = null,
    @SerializedName("error_message")
    val errorMessage: String? = null
)

// 群列表项（用于群列表展示）
data class GroupListItem(
    @SerializedName("group_id")
    val groupId: String,
    @SerializedName("group_name")
    val groupName: String,
    @SerializedName("avatar_url")
    val avatarUrl: String? = null,
    @SerializedName("role")
    val role: String  // "owner" / "admin" / "member"
)

// 群列表响应
data class GroupListResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("groups")
    val groups: List<GroupListItem>
)

// 群成员信息
data class GroupMember(
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("nickname_in_group")
    val nicknameInGroup: String? = null,
    @SerializedName("role")
    val role: String,  // "owner" / "admin" / "member"
    @SerializedName("avatar_url")
    val avatarUrl: String? = null,
    @SerializedName("online")
    val online: Boolean = false
)

// 获取群成员列表请求
data class GroupMemberListRequest(
    @SerializedName("group_id")
    val groupId: String
)

// 群成员列表响应
data class GroupMemberListResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("group_id")
    val groupId: String,
    @SerializedName("members")
    val members: List<GroupMember>,
    // 可选：群信息（包含公告等完整信息）
    @SerializedName("group")
    val group: GroupInfo? = null
)

// 更新群信息请求
data class GroupUpdateInfoRequest(
    @SerializedName("group_id")
    val groupId: String,
    @SerializedName("group_name")
    val groupName: String? = null,
    @SerializedName("avatar_url")
    val avatarUrl: String? = null,
    @SerializedName("announcement")
    val announcement: String? = null
)

// 更新群信息响应
data class GroupUpdateInfoResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("group")
    val group: GroupInfo?,
    @SerializedName("error_code")
    val errorCode: Int? = null,
    @SerializedName("error_message")
    val errorMessage: String? = null
)

// 解散群请求
data class GroupDismissRequest(
    @SerializedName("group_id")
    val groupId: String
)

// 解散群响应
data class GroupDismissResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("message")
    val message: String? = null,
    @SerializedName("error_code")
    val errorCode: Int? = null,
    @SerializedName("error_message")
    val errorMessage: String? = null
)

// 邀请成员请求
data class GroupInviteRequest(
    @SerializedName("group_id")
    val groupId: String,
    @SerializedName("member_user_ids")
    val memberUserIds: List<String>
)

// 邀请成员响应
data class GroupInviteResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("message")
    val message: String? = null,
    @SerializedName("error_code")
    val errorCode: Int? = null,
    @SerializedName("error_message")
    val errorMessage: String? = null
)

// 踢人请求
data class GroupKickRequest(
    @SerializedName("group_id")
    val groupId: String,
    @SerializedName("member_user_ids")
    val memberUserIds: List<String>
)

// 踢人响应
data class GroupKickResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("message")
    val message: String? = null,
    @SerializedName("error_code")
    val errorCode: Int? = null,
    @SerializedName("error_message")
    val errorMessage: String? = null
)

// 退群请求
data class GroupQuitRequest(
    @SerializedName("group_id")
    val groupId: String
)

// 退群响应
data class GroupQuitResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("message")
    val message: String? = null,
    @SerializedName("error_code")
    val errorCode: Int? = null,
    @SerializedName("error_message")
    val errorMessage: String? = null
)

// 群聊消息（扩展ChatMessage，用于群聊）
// 注意：群聊消息复用SEND_MESSAGE和RECEIVE_MESSAGE，通过conversation_type区分
// 这里定义群聊消息的扩展字段结构
data class GroupMessageContent(
    @SerializedName("conversation_type")
    val conversationType: String = "group",
    @SerializedName("group_id")
    val groupId: String,
    @SerializedName("from_user_id")
    val fromUserId: String,
    @SerializedName("send_time")
    val sendTime: Long,
    @SerializedName("content_type")
    val contentType: String = "text",
    @SerializedName("content")
    val content: Map<String, String>,  // {"text": "消息内容"}
    @SerializedName("extra")
    val extra: Map<String, Any>? = null
)

