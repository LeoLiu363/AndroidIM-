package com.example.androidim.ui.userlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidim.model.UserInfoItem
import com.example.androidim.repository.IMRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class UserListViewModel : ViewModel() {
    
    private val repository = IMRepository.getInstance()
    
    val userList: StateFlow<List<UserInfoItem>> = repository.userList
    
    init {
        // 请求用户列表
        viewModelScope.launch {
            repository.requestUserList()
        }
    }
    
    fun refreshUserList() {
        viewModelScope.launch {
            repository.requestUserList()
        }
    }
}


