package com.example.androidim.ui.friend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidim.model.FriendInfo
import com.example.androidim.repository.IMRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 好友列表 ViewModel
 * 负责向仓库请求好友列表并对外暴露好友数据
 */
class FriendListViewModel : ViewModel() {

    private val repository = IMRepository.getInstance()

    val friendList: StateFlow<List<FriendInfo>> = repository.friendList
    val unreadMessageCounts: StateFlow<Map<String, Int>> = repository.unreadMessageCounts

    init {
        // 初始化时请求一次好友列表
        viewModelScope.launch {
            repository.requestFriendList()
        }
    }

    fun refreshFriendList() {
        viewModelScope.launch {
            repository.requestFriendList()
        }
    }
}



