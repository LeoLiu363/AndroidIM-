package com.example.androidim.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidim.model.FriendInfo
import com.example.androidim.repository.IMRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val repository = IMRepository.getInstance()

    val currentUser = repository.currentUser
    val friendList: StateFlow<List<FriendInfo>> = repository.friendList
    val unreadMessageCounts: StateFlow<Map<String, Int>> = repository.unreadMessageCounts

    init {
        // 仅在已登录时才请求好友列表，避免未登录阶段触发服务端 1001
        viewModelScope.launch {
            if (repository.currentUser.value != null) {
            repository.requestFriendList()
            }
        }
    }

    fun refreshFriendList() {
        viewModelScope.launch {
            if (repository.currentUser.value != null) {
            repository.requestFriendList()
            }
        }
    }

    suspend fun logout() {
            repository.logout()
    }
    
    suspend fun deleteFriend(friendUserId: String) {
        repository.deleteFriend(friendUserId)
    }
}

