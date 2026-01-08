package com.example.androidim.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidim.model.ChatMessage
import com.example.androidim.repository.IMRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {
    
    private val repository = IMRepository.getInstance()
    
    // 当前聊天对象 ID（null 表示群聊模式）
    private val _targetUserId = MutableStateFlow<String?>(null)
    
    // 过滤后的消息列表（只显示与当前聊天对象相关的消息）
    val messages: StateFlow<List<ChatMessage>> = combine(
        repository.messages,
        repository.currentUser,
        _targetUserId
    ) { allMessages, currentUser, targetUserId ->
        if (targetUserId == null) {
            // 群聊模式：显示所有消息
            allMessages
        } else {
            // 私聊模式：只显示与当前聊天对象相关的消息
            // 由于消息模型中只包含 fromUserId（发送者），无法区分不同私聊会话的接收者
            // 这里的策略是：
            // - 显示来自当前聊天对象的所有消息（fromUserId == targetUserId）
            // - 显示当前用户发出的所有消息（fromUserId == currentUserId），作为乐观更新
            val currentUserId = currentUser?.userId
            allMessages.filter { message ->
                message.fromUserId == targetUserId || message.fromUserId == currentUserId
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    val currentUser = repository.currentUser
    
    init {
        // 单聊模式不需要请求用户列表
    }
    
    /**
     * 设置聊天对象 ID
     */
    fun setTargetUserId(userId: String) {
        _targetUserId.value = userId
    }
    
    suspend fun sendMessage(toUserId: String, content: String): Boolean {
        return repository.sendMessage(toUserId, content)
    }
    
    /**
     * 清除指定好友的未读消息数
     */
    fun clearUnreadCount(userId: String) {
        repository.clearUnreadCount(userId)
    }
    
    override fun onCleared() {
        super.onCleared()
        // 不在这里断开连接，让Repository保持连接
    }
}

