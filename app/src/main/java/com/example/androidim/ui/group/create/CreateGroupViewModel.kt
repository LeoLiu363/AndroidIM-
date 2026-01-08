package com.example.androidim.ui.group.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidim.model.GroupCreateResponse
import com.example.androidim.repository.IMRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CreateGroupViewModel : ViewModel() {

    private val repository = IMRepository.getInstance()

    private val _createResult = MutableStateFlow<GroupCreateResponse?>(null)
    val createResult: StateFlow<GroupCreateResponse?> = _createResult.asStateFlow()

    init {
        viewModelScope.launch {
            repository.groupCreateResponse.collect { response ->
                _createResult.value = response
            }
        }
    }

    fun createGroup(groupName: String, memberUserIds: List<String>) {
        // 清空之前的响应，确保能触发新的响应
        _createResult.value = null
        viewModelScope.launch {
            repository.createGroup(groupName, memberUserIds)
        }
    }
}

