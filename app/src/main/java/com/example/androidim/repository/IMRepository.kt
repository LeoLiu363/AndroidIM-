package com.example.androidim.repository

import android.content.Context
import android.util.Log
import com.example.androidim.IMApplication
import com.example.androidim.model.*
import com.example.androidim.network.IMClient
import com.example.androidim.protocol.MessageType
import com.example.androidim.utils.IMNotificationManager
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * IM 数据仓库
 * 管理网络连接和消息处理
 * 使用单例模式，确保连接在整个应用生命周期中保持
 */
class IMRepository private constructor() {
    
    companion object {
        @Volatile
        private var INSTANCE: IMRepository? = null
        
        fun getInstance(): IMRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: IMRepository().also { INSTANCE = it }
            }
        }
    }
    
    private val client = IMClient()
    private val gson = Gson()
    private var lastAuthErrorAtMs: Long = 0L
    
    // 用于在 handleMessage 中调用 suspend 函数的 CoroutineScope
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // 连接状态
    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()
    
    // 接收到的消息
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    // 当前用户信息
    private val _currentUser = MutableStateFlow<UserInfo?>(null)
    val currentUser: StateFlow<UserInfo?> = _currentUser.asStateFlow()
    
    // 在线用户列表（简单用户信息）
    private val _userList = MutableStateFlow<List<UserInfoItem>>(emptyList())
    val userList: StateFlow<List<UserInfoItem>> = _userList.asStateFlow()

    // 好友列表
    private val _friendList = MutableStateFlow<List<FriendInfo>>(emptyList())
    val friendList: StateFlow<List<FriendInfo>> = _friendList.asStateFlow()

    // 好友申请结果
    private val _friendApplyResponse = MutableStateFlow<FriendApplyResponse?>(null)
    val friendApplyResponse: StateFlow<FriendApplyResponse?> = _friendApplyResponse.asStateFlow()

    // 收到的好友申请通知列表（“新朋友”）
    private val _friendApplyNotifications = MutableStateFlow<List<FriendApplyNotify>>(emptyList())
    val friendApplyNotifications: StateFlow<List<FriendApplyNotify>> = _friendApplyNotifications.asStateFlow()

    // 好友申请处理结果
    private val _friendHandleResponse = MutableStateFlow<FriendHandleResponse?>(null)
    val friendHandleResponse: StateFlow<FriendHandleResponse?> = _friendHandleResponse.asStateFlow()
    
    // 未读消息数（按好友 userId 分组）
    private val _unreadMessageCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unreadMessageCounts: StateFlow<Map<String, Int>> = _unreadMessageCounts.asStateFlow()
    
    // 登录响应
    private val _loginResponse = MutableStateFlow<LoginResponse?>(null)
    val loginResponse: StateFlow<LoginResponse?> = _loginResponse.asStateFlow()
    
    // 注册响应
    private val _registerResponse = MutableStateFlow<RegisterResponse?>(null)
    val registerResponse: StateFlow<RegisterResponse?> = _registerResponse.asStateFlow()
    
    /**
     * 重置登录/注册响应状态
     * 在每次新的登录/注册请求前调用，确保能触发 StateFlow 更新
     */
    fun resetLoginResponse() {
        _loginResponse.value = null
        _registerResponse.value = null
    }
    
    init {
        // 设置消息接收回调
        client.onMessageReceived = { type, jsonData ->
            handleMessage(type, jsonData)
        }
        
        // 设置连接状态回调
        client.onConnectionChanged = { connected ->
            _connectionState.value = connected
        }
    }
    
    /**
     * 连接到服务端
     */
    suspend fun connect(): Boolean {
        return client.connect()
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        client.disconnect()
    }
    
    /**
     * 登录
     */
    suspend fun login(username: String, password: String) {
        val request = LoginRequest(username, password)
        val jsonData = gson.toJson(request)
        val ok = client.sendMessage(MessageType.LOGIN_REQUEST, jsonData)
        if (!ok) {
            // 发送失败时，直接给出错误响应，避免 UI 一直处于“加载中”
            _loginResponse.value = LoginResponse(
                success = false,
                message = "登录请求发送失败，请检查网络连接",
                userId = null,
                username = null
            )
        }
    }
    
    /**
     * 注册
     */
    suspend fun register(username: String, password: String, nickname: String) {
        val request = RegisterRequest(username, password, nickname)
        val jsonData = gson.toJson(request)
        val ok = client.sendMessage(MessageType.REGISTER_REQUEST, jsonData)
        if (!ok) {
            // 发送失败时，直接给出错误响应，避免 UI 一直处于“加载中”
            _registerResponse.value = RegisterResponse(
                success = false,
                message = "注册请求发送失败，请检查网络连接",
                userId = null
            )
        }
    }
    
    /**
     * 发送消息
     */
    suspend fun sendMessage(toUserId: String, content: String): Boolean {
        val request = mapOf(
            "to_user_id" to toUserId,
            "content" to content,
            "message_type" to "text"
        )
        val jsonData = gson.toJson(request)
        val success = client.sendMessage(MessageType.SEND_MESSAGE, jsonData)
        
        Log.d("IMRepository", "[发送] sendMessage 返回: success=$success")
        
        // 乐观更新：发送成功后立即在本地显示消息
        if (success) {
            val currentUser = _currentUser.value
            Log.d("IMRepository", "[发送] 当前用户: $currentUser")
            
            val localMessage = ChatMessage(
                fromUserId = currentUser?.userId ?: "unknown",
                fromUsername = currentUser?.username ?: "我",
                content = content,
                messageType = "text",
                timestamp = System.currentTimeMillis() / 1000  // 秒级时间戳
            )
            
            val currentMessages = _messages.value
            Log.d("IMRepository", "[发送] 当前消息数量: ${currentMessages.size}")
            
            _messages.value = currentMessages + localMessage
            
            Log.d("IMRepository", "[发送] ✅ 消息已添加到本地列表（乐观更新），新消息数量: ${_messages.value.size}")
            Log.d("IMRepository", "[发送] 消息内容: fromUserId=${localMessage.fromUserId}, fromUsername=${localMessage.fromUsername}, content=${localMessage.content}")
        } else {
            Log.w("IMRepository", "[发送] ⚠️ 消息发送失败，不进行乐观更新")
        }
        
        return success
    }

    /**
     * 发送好友申请
     */
    suspend fun sendFriendApply(targetUsername: String, greeting: String?, remark: String?) {
        val request = FriendApplyRequest(
            targetUsername = targetUsername,
            greeting = greeting,
            remark = remark
        )
        val jsonData = gson.toJson(request)
        val ok = client.sendMessage(MessageType.FRIEND_APPLY_REQUEST, jsonData)
        if (!ok) {
            // 本地直接给出失败结果，便于 UI 提示
            _friendApplyResponse.value = FriendApplyResponse(
                success = false,
                applyId = null,
                message = "好友申请发送失败，请检查网络连接"
            )
        }
    }

    /**
     * 处理好友申请（同意/拒绝）
     * @param action "accept" 或 "reject"
     */
    suspend fun handleFriendApply(applyId: String, action: String, remark: String?) {
        val request = FriendHandleRequest(
            applyId = applyId,
            action = action,
            remark = remark
        )
        val jsonData = gson.toJson(request)
        val ok = client.sendMessage(MessageType.FRIEND_HANDLE_REQUEST, jsonData)
        if (!ok) {
            _friendHandleResponse.value = FriendHandleResponse(
                success = false,
                action = action,
                friend = null
            )
        }
    }

    /**
     * 请求好友列表
     */
    suspend fun requestFriendList() {
        // 未登录时不要发请求，避免服务端返回“请先登录”导致 UI 重复提示
        if (_currentUser.value == null) {
            Log.d("IMRepository", "[好友] 未登录，跳过请求好友列表")
            return
        }
        val jsonData = "{}"  // 好友列表请求可为空
        client.sendMessage(MessageType.FRIEND_LIST_REQUEST, jsonData)
    }

    /**
     * 删除好友
     */
    suspend fun deleteFriend(friendUserId: String) {
        val request = FriendDeleteRequest(friendUserId = friendUserId)
        val jsonData = gson.toJson(request)
        client.sendMessage(MessageType.FRIEND_DELETE_REQUEST, jsonData)
    }

    /**
     * 拉黑 / 解除拉黑好友
     */
    suspend fun blockFriend(targetUserId: String, block: Boolean) {
        val request = FriendBlockRequest(targetUserId = targetUserId, block = block)
        val jsonData = gson.toJson(request)
        client.sendMessage(MessageType.FRIEND_BLOCK_REQUEST, jsonData)
    }
    
    /**
     * 请求用户列表
     */
    suspend fun requestUserList() {
        val jsonData = "{}"
        client.sendMessage(MessageType.USER_LIST_REQUEST, jsonData)
    }
    
    /**
     * 清除指定好友的未读消息数
     */
    fun clearUnreadCount(userId: String) {
        val currentCounts = _unreadMessageCounts.value
        if (currentCounts.containsKey(userId)) {
            val newCounts = currentCounts.toMutableMap()
            newCounts.remove(userId)
            _unreadMessageCounts.value = newCounts
            Log.d("IMRepository", "[未读] 清除未读消息数: $userId")
        }
    }
    
    /**
     * 登出
     */
    suspend fun logout() {
        val jsonData = "{}"
        client.sendMessage(MessageType.LOGOUT, jsonData)
        disconnect()
        // 清掉上一次的登录/注册响应，避免登出后 LoginActivity 读到旧的“登录成功”而跳转死循环
        resetLoginResponse()
        // 清除所有未读消息数
        _unreadMessageCounts.value = emptyMap()
        // 清除当前用户信息
        _currentUser.value = null
        Log.d("IMRepository", "[登出] 已清除用户信息和未读消息数")
    }
    
    // ========== 群聊相关方法 ==========
    
    // 群列表
    private val _groupList = MutableStateFlow<List<GroupListItem>>(emptyList())
    val groupList: StateFlow<List<GroupListItem>> = _groupList.asStateFlow()
    
    // 创建群响应
    private val _groupCreateResponse = MutableStateFlow<GroupCreateResponse?>(null)
    val groupCreateResponse: StateFlow<GroupCreateResponse?> = _groupCreateResponse.asStateFlow()
    
    // 群成员列表（按群ID存储）
    private val _groupMembers = MutableStateFlow<Map<String, List<GroupMember>>>(emptyMap())
    val groupMembers: StateFlow<Map<String, List<GroupMember>>> = _groupMembers.asStateFlow()
    
    // 群详细信息（按群ID存储，包含公告等完整信息）
    private val _groupInfoMap = MutableStateFlow<Map<String, GroupInfo>>(emptyMap())
    val groupInfoMap: StateFlow<Map<String, GroupInfo>> = _groupInfoMap.asStateFlow()
    
    /**
     * 创建群
     */
    suspend fun createGroup(groupName: String, memberUserIds: List<String>, avatarUrl: String? = null) {
        // 清空之前的响应，确保能触发新的响应
        _groupCreateResponse.value = null
        val request = GroupCreateRequest(
            groupName = groupName,
            avatarUrl = avatarUrl,
            memberUserIds = memberUserIds
        )
        val jsonData = gson.toJson(request)
        Log.d("IMRepository", "[群聊] 发送创建群请求: groupName=$groupName, memberCount=${memberUserIds.size}")
        val success = client.sendMessage(MessageType.GROUP_CREATE_REQUEST, jsonData)
        Log.d("IMRepository", "[群聊] 创建群请求发送结果: success=$success")
    }
    
    /**
     * 请求群列表
     */
    suspend fun requestGroupList() {
        val jsonData = "{}"
        client.sendMessage(MessageType.GROUP_LIST_REQUEST, jsonData)
    }
    
    /**
     * 请求群成员列表
     */
    suspend fun requestGroupMemberList(groupId: String) {
        val request = GroupMemberListRequest(groupId = groupId)
        val jsonData = gson.toJson(request)
        client.sendMessage(MessageType.GROUP_MEMBER_LIST_REQUEST, jsonData)
    }
    
    /**
     * 发送群聊消息
     */
    suspend fun sendGroupMessage(groupId: String, content: String): Boolean {
        val request = mapOf(
            "conversation_type" to "group",
            "group_id" to groupId,
            "content" to content,
            "message_type" to "text"
        )
        val jsonData = gson.toJson(request)
        val success = client.sendMessage(MessageType.SEND_MESSAGE, jsonData)
        
        // 乐观更新：发送成功后立即在本地显示消息
        if (success) {
            val currentUser = _currentUser.value
            val localMessage = ChatMessage(
                fromUserId = currentUser?.userId ?: "unknown",
                fromUsername = currentUser?.username ?: "我",
                content = content,
                messageType = "text",
                timestamp = System.currentTimeMillis() / 1000,
                conversationType = "group",
                groupId = groupId
            )
            
            val currentMessages = _messages.value
            _messages.value = currentMessages + localMessage
            Log.d("IMRepository", "[群聊] ✅ 群消息已添加到本地列表（乐观更新）")
        }
        
        return success
    }
    
    // 群操作响应
    private val _groupUpdateInfoResponse = MutableStateFlow<GroupUpdateInfoResponse?>(null)
    val groupUpdateInfoResponse: StateFlow<GroupUpdateInfoResponse?> = _groupUpdateInfoResponse.asStateFlow()
    
    private val _groupDismissResponse = MutableStateFlow<GroupDismissResponse?>(null)
    val groupDismissResponse: StateFlow<GroupDismissResponse?> = _groupDismissResponse.asStateFlow()
    
    private val _groupInviteResponse = MutableStateFlow<GroupInviteResponse?>(null)
    val groupInviteResponse: StateFlow<GroupInviteResponse?> = _groupInviteResponse.asStateFlow()
    
    private val _groupKickResponse = MutableStateFlow<GroupKickResponse?>(null)
    val groupKickResponse: StateFlow<GroupKickResponse?> = _groupKickResponse.asStateFlow()
    
    private val _groupQuitResponse = MutableStateFlow<GroupQuitResponse?>(null)
    val groupQuitResponse: StateFlow<GroupQuitResponse?> = _groupQuitResponse.asStateFlow()
    
    // 清空响应的方法，避免重复触发
    fun clearGroupInviteResponse() {
        _groupInviteResponse.value = null
    }
    
    fun clearGroupKickResponse() {
        _groupKickResponse.value = null
    }
    
    fun clearGroupQuitResponse() {
        _groupQuitResponse.value = null
    }
    
    fun clearGroupDismissResponse() {
        _groupDismissResponse.value = null
    }
    
    fun clearGroupUpdateInfoResponse() {
        _groupUpdateInfoResponse.value = null
    }
    
    /**
     * 更新群信息
     */
    suspend fun updateGroupInfo(groupId: String, groupName: String? = null, avatarUrl: String? = null, announcement: String? = null) {
        _groupUpdateInfoResponse.value = null
        val request = GroupUpdateInfoRequest(
            groupId = groupId,
            groupName = groupName,
            avatarUrl = avatarUrl,
            announcement = announcement
        )
        val jsonData = gson.toJson(request)
        Log.d("IMRepository", "[群聊] 发送更新群信息请求: groupId=$groupId")
        client.sendMessage(MessageType.GROUP_UPDATE_INFO_REQUEST, jsonData)
    }
    
    /**
     * 解散群（仅群主）
     */
    suspend fun dismissGroup(groupId: String) {
        _groupDismissResponse.value = null
        val request = GroupDismissRequest(groupId = groupId)
        val jsonData = gson.toJson(request)
        Log.d("IMRepository", "[群聊] 发送解散群请求: groupId=$groupId")
        client.sendMessage(MessageType.GROUP_DISMISS_REQUEST, jsonData)
    }
    
    /**
     * 邀请成员加入群
     */
    suspend fun inviteGroupMembers(groupId: String, memberUserIds: List<String>) {
        _groupInviteResponse.value = null
        val request = GroupInviteRequest(
            groupId = groupId,
            memberUserIds = memberUserIds
        )
        val jsonData = gson.toJson(request)
        Log.d("IMRepository", "[群聊] 发送邀请成员请求: groupId=$groupId, memberCount=${memberUserIds.size}")
        client.sendMessage(MessageType.GROUP_INVITE_REQUEST, jsonData)
    }
    
    /**
     * 踢出群成员（群主/管理员）
     */
    suspend fun kickGroupMember(groupId: String, targetUserId: String) {
        _groupKickResponse.value = null
        val request = GroupKickRequest(
            groupId = groupId,
            memberUserIds = listOf(targetUserId)  // 使用数组格式
        )
        val jsonData = gson.toJson(request)
        Log.d("IMRepository", "[群聊] 发送踢人请求: groupId=$groupId, targetUserId=$targetUserId")
        client.sendMessage(MessageType.GROUP_KICK_REQUEST, jsonData)
    }
    
    /**
     * 退出群
     */
    suspend fun quitGroup(groupId: String) {
        _groupQuitResponse.value = null
        val request = GroupQuitRequest(groupId = groupId)
        val jsonData = gson.toJson(request)
        Log.d("IMRepository", "[群聊] 发送退群请求: groupId=$groupId")
        client.sendMessage(MessageType.GROUP_QUIT_REQUEST, jsonData)
    }
    
    /**
     * 处理接收到的消息
     */
    private fun handleMessage(type: Short, jsonData: String) {
        try {
            val typeHex = String.format("0x%04X", type.toInt() and 0xFFFF)
            Log.d("IMRepository", "[处理] 📨 处理消息: type=$type ($typeHex), data=$jsonData")
            
            // 检查是否是群聊相关消息
            if (type == MessageType.GROUP_CREATE_RESPONSE) {
                Log.d("IMRepository", "[处理] 🔍 匹配到 GROUP_CREATE_RESPONSE")
            }
            
            when (type) {
                MessageType.LOGIN_RESPONSE -> {
                    Log.d("IMRepository", "[处理] ✅ 登录响应")
                    val response = gson.fromJson(jsonData, LoginResponse::class.java)
                    // 使用新的对象实例，确保 StateFlow 能检测到变化（即使内容相同）
                    _loginResponse.value = LoginResponse(
                        success = response.success,
                        message = response.message,
                        userId = response.userId,
                        username = response.username
                    )
                    if (response.success) {
                        _currentUser.value = UserInfo(
                            userId = response.userId ?: "",
                            username = response.username ?: ""
                        )
                    }
                }
                MessageType.REGISTER_RESPONSE -> {
                    Log.d("IMRepository", "[处理] ✅ 注册响应")
                    val response = gson.fromJson(jsonData, RegisterResponse::class.java)
                    // 使用新的对象实例，确保 StateFlow 能检测到变化
                    _registerResponse.value = RegisterResponse(
                        success = response.success,
                        message = response.message,
                        userId = response.userId
                    )
                }
                MessageType.RECEIVE_MESSAGE -> {
                    Log.d("IMRepository", "[处理] ✅ 接收消息")
                    val message = gson.fromJson(jsonData, ChatMessage::class.java)
                    
                    // 检查是否已存在相同的消息（避免重复显示乐观更新的消息）
                    val existingMessages = _messages.value
                    val isDuplicate = existingMessages.any { 
                        it.fromUserId == message.fromUserId && 
                        it.content == message.content && 
                        Math.abs(it.timestamp - message.timestamp) < 5  // 5秒内的相同消息视为重复
                    }
                    
                    if (!isDuplicate) {
                        _messages.value = existingMessages + message
                        val conversationType = message.conversationType ?: "single"
                        if (conversationType == "group") {
                            Log.d("IMRepository", "[处理] ✅ 群聊消息已添加到列表: groupId=${message.groupId}")
                        } else {
                            Log.d("IMRepository", "[处理] ✅ 单聊消息已添加到列表")
                            
                            // 增加未读消息数（只有单聊来自好友的消息才增加未读数）
                            val currentUser = _currentUser.value
                            if (currentUser != null && message.fromUserId != currentUser.userId) {
                                val currentCounts = _unreadMessageCounts.value
                                val newCount = (currentCounts[message.fromUserId] ?: 0) + 1
                                _unreadMessageCounts.value = currentCounts + (message.fromUserId to newCount)
                                Log.d("IMRepository", "[处理] 📬 未读消息数更新: ${message.fromUserId} = $newCount")
                                
                                // 发送通知（如果应用不在前台或不在聊天界面）
                                try {
                                    val context = IMApplication.getInstance()
                                    IMNotificationManager.showMessageNotification(
                                        context,
                                        message.fromUserId,
                                        message.fromUsername,
                                        message.content
                                    )
                                    Log.d("IMRepository", "[处理] 📢 已发送通知")
                                } catch (e: Exception) {
                                    Log.e("IMRepository", "[处理] ❌ 发送通知失败: ${e.message}")
                                }
                            }
                        }
                    } else {
                        Log.d("IMRepository", "[处理] ⚠️ 消息已存在，跳过（可能是乐观更新的消息）")
                    }
                }
                MessageType.USER_LIST_RESPONSE -> {
                    Log.d("IMRepository", "[处理] ✅ 用户列表响应")
                    Log.d("IMRepository", "[处理] 原始JSON数据: $jsonData")
                    val response = gson.fromJson(jsonData, UserListResponse::class.java)
                    Log.d("IMRepository", "[处理] 解析后的用户数量: ${response.users.size}")
                    response.users.forEachIndexed { index, user ->
                        Log.d("IMRepository", "[处理] 用户[$index]: userId=${user.userId}, username=${user.username}, nickname=${user.nickname}, online=${user.online}")
                    }
                    _userList.value = response.users
                }
                MessageType.FRIEND_APPLY_RESPONSE -> {
                    Log.d("IMRepository", "[处理] ✅ 好友申请响应")
                    val response = gson.fromJson(jsonData, FriendApplyResponse::class.java)
                    _friendApplyResponse.value = response
                }
                MessageType.FRIEND_APPLY_NOTIFY -> {
                    Log.d("IMRepository", "[处理] ✅ 收到好友申请通知")
                    val notify = gson.fromJson(jsonData, FriendApplyNotify::class.java)
                    val current = _friendApplyNotifications.value
                    _friendApplyNotifications.value = current + notify
                }
                MessageType.FRIEND_HANDLE_RESPONSE -> {
                    Log.d("IMRepository", "[处理] ✅ 好友申请处理响应")
                    val response = gson.fromJson(jsonData, FriendHandleResponse::class.java)
                    _friendHandleResponse.value = response
                    // 如果成功且返回了新的好友，更新好友列表（追加或替换同 userId）
                    if (response.success && response.friend != null) {
                        val friend = response.friend
                        val current = _friendList.value
                        val filtered = current.filterNot { it.userId == friend.userId }
                        _friendList.value = filtered + friend
                    }
                }
                MessageType.FRIEND_HANDLE_NOTIFY -> {
                    Log.d("IMRepository", "[处理] ✅ 好友申请处理通知")
                    val response = gson.fromJson(jsonData, FriendHandleResponse::class.java)
                    // 通知中也会带 friend，可用于更新好友列表
                    if (response.success && response.friend != null) {
                        val friend = response.friend
                        val current = _friendList.value
                        val filtered = current.filterNot { it.userId == friend.userId }
                        _friendList.value = filtered + friend
                    }
                }
                MessageType.FRIEND_LIST_RESPONSE -> {
                    Log.d("IMRepository", "[处理] ✅ 好友列表响应")
                    val response = gson.fromJson(jsonData, FriendListResponse::class.java)
                    _friendList.value = response.friends
                }
                MessageType.FRIEND_DELETE_RESPONSE -> {
                    Log.d("IMRepository", "[处理] ✅ 删除好友响应")
                    val response = gson.fromJson(jsonData, FriendDeleteResponse::class.java)
                    if (response.success) {
                        // 删除成功后，刷新好友列表
                        repositoryScope.launch {
                            requestFriendList()
                        }
                    }
                }
                MessageType.HEARTBEAT_RESPONSE -> {
                    // 心跳响应，无需处理
                    Log.d("IMRepository", "[处理] 💓 收到心跳响应 (type=8)")
                }
                // 群聊相关消息处理
                MessageType.GROUP_CREATE_RESPONSE -> {
                    Log.d("IMRepository", "[处理] ✅ 创建群响应")
                    Log.d("IMRepository", "[处理] 响应数据: $jsonData")
                    try {
                        val response = gson.fromJson(jsonData, GroupCreateResponse::class.java)
                        Log.d("IMRepository", "[处理] 解析成功: success=${response.success}, group=${response.group}, errorCode=${response.errorCode}, errorMessage=${response.errorMessage}")
                        _groupCreateResponse.value = response
                        // 创建成功后，保存群信息并刷新群列表
                        if (response.success && response.group != null) {
                            val groupInfo = response.group
                            val currentInfoMap = _groupInfoMap.value.toMutableMap()
                            currentInfoMap[groupInfo.groupId] = groupInfo
                            _groupInfoMap.value = currentInfoMap
                            Log.d("IMRepository", "[处理] 创建群成功，已保存群信息，刷新群列表")
                            repositoryScope.launch {
                                requestGroupList()
                            }
                        } else {
                            Log.w("IMRepository", "[处理] 创建群失败: success=${response.success}, errorCode=${response.errorCode}, errorMessage=${response.errorMessage}")
                        }
                    } catch (e: Exception) {
                        Log.e("IMRepository", "[处理] ❌ 解析创建群响应失败: ${e.message}", e)
                    }
                }
                MessageType.GROUP_LIST_RESPONSE -> {
                    Log.d("IMRepository", "[处理] ✅ 群列表响应")
                    val response = gson.fromJson(jsonData, GroupListResponse::class.java)
                    if (response.success) {
                        _groupList.value = response.groups
                    }
                }
                MessageType.GROUP_MEMBER_LIST_RESPONSE -> {
                    Log.d("IMRepository", "[处理] ✅ 群成员列表响应")
                    Log.d("IMRepository", "[处理] 响应数据: $jsonData")
                    val response = gson.fromJson(jsonData, GroupMemberListResponse::class.java)
                    if (response.success) {
                        val currentMembers = _groupMembers.value.toMutableMap()
                        currentMembers[response.groupId] = response.members
                        _groupMembers.value = currentMembers
                        
                        // 如果响应中包含群信息（包括公告），保存到 groupInfoMap
                        response.group?.let { groupInfo ->
                            val currentInfoMap = _groupInfoMap.value.toMutableMap()
                            currentInfoMap[groupInfo.groupId] = groupInfo
                            _groupInfoMap.value = currentInfoMap
                            Log.d("IMRepository", "[处理] 已保存群信息（包含公告）: groupId=${groupInfo.groupId}, announcement=${groupInfo.announcement}")
                        }
                    }
                }
                MessageType.GROUP_MESSAGE_RECEIVE -> {
                    Log.d("IMRepository", "[处理] ✅ 收到群聊消息")
                    // 群聊消息可能复用RECEIVE_MESSAGE，这里单独处理GROUP_MESSAGE_RECEIVE类型
                    // 如果服务端使用RECEIVE_MESSAGE但conversation_type为group，则在RECEIVE_MESSAGE中处理
                    try {
                        val message = gson.fromJson(jsonData, ChatMessage::class.java)
                        val existingMessages = _messages.value
                        val isDuplicate = existingMessages.any {
                            it.fromUserId == message.fromUserId &&
                            it.content == message.content &&
                            Math.abs(it.timestamp - message.timestamp) < 5
                        }
                        if (!isDuplicate) {
                            _messages.value = existingMessages + message
                            Log.d("IMRepository", "[处理] ✅ 群聊消息已添加到列表")
                        }
                    } catch (e: Exception) {
                        Log.e("IMRepository", "[处理] ❌ 解析群聊消息失败: ${e.message}")
                    }
                }
                MessageType.GROUP_UPDATE_INFO_RESPONSE -> {
                    Log.d("IMRepository", "[处理] ✅ 更新群信息响应")
                    Log.d("IMRepository", "[处理] 响应数据: $jsonData")
                    try {
                        val response = gson.fromJson(jsonData, GroupUpdateInfoResponse::class.java)
                        Log.d("IMRepository", "[处理] 解析成功: success=${response.success}, group=${response.group}")
                        _groupUpdateInfoResponse.value = response
                        if (response.success && response.group != null) {
                            // 保存更新后的群信息（包含公告）
                            val groupInfo = response.group
                            val currentInfoMap = _groupInfoMap.value.toMutableMap()
                            currentInfoMap[groupInfo.groupId] = groupInfo
                            _groupInfoMap.value = currentInfoMap
                            Log.d("IMRepository", "[处理] 更新群信息成功，已保存群信息（包含公告）")
                            // 刷新群列表
                            repositoryScope.launch {
                                requestGroupList()
                            }
                        } else {
                            Log.w("IMRepository", "[处理] 更新群信息失败: ${response.errorMessage}")
                        }
                    } catch (e: Exception) {
                        Log.e("IMRepository", "[处理] ❌ 解析更新群信息响应失败: ${e.message}", e)
                    }
                }
                MessageType.GROUP_UPDATE_INFO_NOTIFY -> {
                    Log.d("IMRepository", "[处理] ✅ 收到群信息更新通知")
                    Log.d("IMRepository", "[处理] 通知数据: $jsonData")
                    // 群信息更新通知可能包含新的群信息，刷新群列表
                    repositoryScope.launch {
                        requestGroupList()
                    }
                }
                MessageType.GROUP_DISMISS_RESPONSE -> {
                    Log.d("IMRepository", "[处理] ✅ 解散群响应")
                    Log.d("IMRepository", "[处理] 响应数据: $jsonData")
                    try {
                        val response = gson.fromJson(jsonData, GroupDismissResponse::class.java)
                        Log.d("IMRepository", "[处理] 解析成功: success=${response.success}, errorMessage=${response.errorMessage}")
                        _groupDismissResponse.value = response
                        if (response.success) {
                            Log.d("IMRepository", "[处理] 解散群成功，刷新群列表")
                            // 解散群成功后，立即刷新群列表，从列表中移除已解散的群
                            repositoryScope.launch {
                                requestGroupList()
                            }
                        } else {
                            Log.w("IMRepository", "[处理] 解散群失败: ${response.errorMessage}")
                        }
                    } catch (e: Exception) {
                        Log.e("IMRepository", "[处理] ❌ 解析解散群响应失败: ${e.message}", e)
                    }
                }
                MessageType.GROUP_INVITE_RESPONSE -> {
                    Log.d("IMRepository", "[处理] ✅ 邀请成员响应")
                    Log.d("IMRepository", "[处理] 响应数据: $jsonData")
                    try {
                        val response = gson.fromJson(jsonData, GroupInviteResponse::class.java)
                        Log.d("IMRepository", "[处理] 解析成功: success=${response.success}, errorMessage=${response.errorMessage}")
                        _groupInviteResponse.value = response
                        if (response.success) {
                            Log.d("IMRepository", "[处理] 邀请成员成功，刷新群列表和群成员列表")
                            // 刷新群列表（邀请成功后，被邀请人应该能看到新群）
                            repositoryScope.launch {
                                requestGroupList()
                            }
                        } else {
                            Log.w("IMRepository", "[处理] 邀请成员失败: ${response.errorMessage}")
                        }
                    } catch (e: Exception) {
                        Log.e("IMRepository", "[处理] ❌ 解析邀请成员响应失败: ${e.message}", e)
                    }
                }
                MessageType.GROUP_KICK_RESPONSE -> {
                    Log.d("IMRepository", "[处理] ✅ 踢人响应")
                    try {
                        val response = gson.fromJson(jsonData, GroupKickResponse::class.java)
                        _groupKickResponse.value = response
                        if (response.success) {
                            Log.d("IMRepository", "[处理] 踢人成功")
                        }
                    } catch (e: Exception) {
                        Log.e("IMRepository", "[处理] ❌ 解析踢人响应失败: ${e.message}")
                    }
                }
                MessageType.GROUP_QUIT_RESPONSE -> {
                    Log.d("IMRepository", "[处理] ✅ 退群响应")
                    try {
                        val response = gson.fromJson(jsonData, GroupQuitResponse::class.java)
                        _groupQuitResponse.value = response
                        if (response.success) {
                            Log.d("IMRepository", "[处理] 退群成功，刷新群列表")
                            repositoryScope.launch {
                                requestGroupList()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("IMRepository", "[处理] ❌ 解析退群响应失败: ${e.message}")
                    }
                }
                // 群聊通知类型（服务端主动推送）
                MessageType.GROUP_INVITE_NOTIFY -> {
                    Log.d("IMRepository", "[处理] ✅ 收到邀请入群通知")
                    Log.d("IMRepository", "[处理] 通知数据: $jsonData")
                    // 被邀请人收到通知后，应该刷新群列表，以便看到新加入的群
                    repositoryScope.launch {
                        requestGroupList()
                    }
                }
                MessageType.GROUP_KICK_NOTIFY -> {
                    Log.d("IMRepository", "[处理] ✅ 收到被踢出群通知")
                    // TODO: 可以显示通知或更新群列表
                    repositoryScope.launch {
                        requestGroupList()
                    }
                }
                MessageType.GROUP_QUIT_NOTIFY -> {
                    Log.d("IMRepository", "[处理] ✅ 收到成员退群通知")
                    // TODO: 可以刷新群成员列表
                }
                MessageType.GROUP_DISMISS_NOTIFY -> {
                    Log.d("IMRepository", "[处理] ✅ 收到群解散通知")
                    // TODO: 可以显示通知或更新群列表
                    repositoryScope.launch {
                        requestGroupList()
                    }
                }
                MessageType.GROUP_UPDATE_INFO_NOTIFY -> {
                    Log.d("IMRepository", "[处理] ✅ 收到群信息更新通知")
                    // TODO: 可以刷新群列表或群详情
                    repositoryScope.launch {
                        requestGroupList()
                    }
                }
                MessageType.ERROR -> {
                    Log.e("IMRepository", "[处理] ❌ 错误消息: $jsonData")
                    val error = gson.fromJson(jsonData, ErrorResponse::class.java)
                    Log.e("IMRepository", "[处理] 错误详情: code=${error.errorCode}, message=${error.errorMessage}")
                    // code=1001: 请先登录
                    if (error.errorCode == 1001) {
                        // 如果本来就未登录（比如刚登出），直接忽略，避免重复弹窗
                        if (_currentUser.value == null) {
                            Log.w("IMRepository", "[处理] ⚠️ 已未登录，忽略 1001 错误提示")
                            return
                        }
                        // 保护：同类提示做节流，避免短时间重复展示
                        val now = System.currentTimeMillis()
                        if (now - lastAuthErrorAtMs < 1500) {
                            Log.w("IMRepository", "[处理] ⚠️ 1001 错误过于频繁，已节流")
                            return
                        }
                        lastAuthErrorAtMs = now
                        // 清空用户并断开连接，交由 UI 跳回登录页
                        _currentUser.value = null
                        disconnect()
                    }
                    // 将错误转为登录响应，交给上层统一展示（LoginActivity 会 Toast）
                    _loginResponse.value = LoginResponse(
                        success = false,
                        message = error.errorMessage,
                        userId = null,
                        username = null
                    )
                }
                else -> {
                    val typeHex = String.format("0x%04X", type.toInt() and 0xFFFF)
                    Log.w("IMRepository", "[处理] ⚠️ 未知消息类型: type=$type ($typeHex), data=$jsonData")
                    // 如果是群聊相关的未知类型，特别标注
                    if (type >= 0x0200 && type <= 0x0210) {
                        Log.w("IMRepository", "[处理] ⚠️ 这是群聊相关消息类型，但未在when中处理")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("IMRepository", "[处理] ❌ 处理消息失败: type=$type, error=${e.message}", e)
        }
    }
}


