package com.example.androidim.ui.group.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidim.model.FriendInfo
import com.example.androidim.repository.IMRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SelectMembersViewModel : ViewModel() {

    private val repository = IMRepository.getInstance()

    val friendList: StateFlow<List<FriendInfo>> = repository.friendList

    private val _selectedMemberIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedMemberIds: StateFlow<Set<String>> = _selectedMemberIds.asStateFlow()

    init {
        // 请求好友列表
        viewModelScope.launch {
            repository.requestFriendList()
        }
    }

    fun toggleMember(userId: String, isSelected: Boolean) {
        val current = _selectedMemberIds.value.toMutableSet()
        if (isSelected) {
            current.add(userId)
        } else {
            current.remove(userId)
        }
        _selectedMemberIds.value = current
    }
}




