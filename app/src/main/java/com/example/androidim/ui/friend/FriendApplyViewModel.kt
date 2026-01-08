package com.example.androidim.ui.friend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidim.model.FriendApplyResponse
import com.example.androidim.repository.IMRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class FriendApplyViewModel : ViewModel() {

    private val repository = IMRepository.getInstance()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending

    val applyResult: StateFlow<FriendApplyResponse?> = repository.friendApplyResponse

    fun sendFriendApply(targetUserId: String, greeting: String?, remark: String?) {
        if (_isSending.value) return
        _isSending.value = true

        viewModelScope.launch {
            try {
                repository.sendFriendApply(targetUserId, greeting, remark)
            } finally {
                _isSending.value = false
            }
        }
    }
}






