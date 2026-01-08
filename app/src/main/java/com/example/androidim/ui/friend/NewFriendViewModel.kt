package com.example.androidim.ui.friend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidim.model.FriendApplyNotify
import com.example.androidim.model.FriendHandleResponse
import com.example.androidim.repository.IMRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class NewFriendViewModel : ViewModel() {

    private val repository = IMRepository.getInstance()

    val applyList: StateFlow<List<FriendApplyNotify>> = repository.friendApplyNotifications
    val handleResult: StateFlow<FriendHandleResponse?> = repository.friendHandleResponse

    fun accept(applyId: String, remark: String?) {
        handle(applyId, "accept", remark)
    }

    fun reject(applyId: String) {
        handle(applyId, "reject", null)
    }

    private fun handle(applyId: String, action: String, remark: String?) {
        viewModelScope.launch {
            repository.handleFriendApply(applyId, action, remark)
        }
    }
}






