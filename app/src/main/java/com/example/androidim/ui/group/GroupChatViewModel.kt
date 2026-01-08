package com.example.androidim.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidim.model.ChatMessage
import com.example.androidim.repository.IMRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class GroupChatViewModel : ViewModel() {

    private val repository = IMRepository.getInstance()

    // 当前群ID
    private val _groupId = MutableStateFlow<String?>(null)

    // 过滤后的消息列表（只显示当前群的消息）
    val messages: StateFlow<List<ChatMessage>> = combine(
        repository.messages,
        _groupId
    ) { allMessages, groupId ->
        if (groupId == null) {
            emptyList()
        } else {
            // 只显示群聊消息，且属于当前群
            allMessages.filter { message ->
                message.conversationType == "group" && message.groupId == groupId
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setGroupId(groupId: String) {
        _groupId.value = groupId
    }

    suspend fun sendMessage(groupId: String, content: String): Boolean {
        return repository.sendGroupMessage(groupId, content)
    }
    
    /**
     * 获取当前用户在群中的角色
     */
    fun getUserRoleInGroup(groupId: String): String {
        val currentUserId = repository.currentUser.value?.userId
        if (currentUserId == null) return "member"
        
        // 从群列表中查找当前用户所在的群，获取角色
        val groupList = repository.groupList.value
        val group = groupList.find { it.groupId == groupId }
        return group?.role ?: "member"
    }
}

