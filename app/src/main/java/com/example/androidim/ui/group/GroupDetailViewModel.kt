package com.example.androidim.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidim.model.GroupInviteResponse
import com.example.androidim.model.GroupKickResponse
import com.example.androidim.model.GroupMember
import com.example.androidim.model.GroupQuitResponse
import com.example.androidim.model.GroupDismissResponse
import com.example.androidim.repository.IMRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GroupDetailViewModel : ViewModel() {

    private val repository = IMRepository.getInstance()

    private var currentGroupId: String? = null
    private val _currentGroupId = MutableStateFlow<String?>(null)

    // 当前群的成员列表
    val groupMembers: StateFlow<List<GroupMember>> = combine(
        repository.groupMembers,
        _currentGroupId
    ) { allMembers: Map<String, List<GroupMember>>, groupId: String? ->
        if (groupId == null) {
            emptyList()
        } else {
            allMembers[groupId] ?: emptyList()
        }
    }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // 操作响应
    val inviteResult: StateFlow<GroupInviteResponse?> = repository.groupInviteResponse
    val kickResult: StateFlow<GroupKickResponse?> = repository.groupKickResponse
    val quitResult: StateFlow<GroupQuitResponse?> = repository.groupQuitResponse
    val dismissResult: StateFlow<GroupDismissResponse?> = repository.groupDismissResponse

    fun setGroupId(groupId: String) {
        currentGroupId = groupId
        _currentGroupId.value = groupId
    }

    fun requestGroupMemberList(groupId: String) {
        viewModelScope.launch {
            repository.requestGroupMemberList(groupId)
        }
    }

    fun inviteMembers(groupId: String, memberUserIds: List<String>) {
        viewModelScope.launch {
            repository.inviteGroupMembers(groupId, memberUserIds)
        }
    }

    fun kickMember(groupId: String, targetUserId: String) {
        viewModelScope.launch {
            repository.kickGroupMember(groupId, targetUserId)
        }
    }

    fun quitGroup(groupId: String) {
        viewModelScope.launch {
            repository.quitGroup(groupId)
        }
    }

    fun dismissGroup(groupId: String) {
        viewModelScope.launch {
            repository.dismissGroup(groupId)
        }
    }

    fun getCurrentUserId(): String {
        return repository.currentUser.value?.userId ?: ""
    }
    
    // 获取群信息（从群信息Map中获取完整信息，包括公告）
    fun getGroupInfo(groupId: String): com.example.androidim.model.GroupInfo? {
        val infoMap = repository.groupInfoMap.value
        return infoMap[groupId]
    }
    
    // 群信息Map（包含完整群信息，包括公告）
    val groupInfoMap: StateFlow<Map<String, com.example.androidim.model.GroupInfo>> = repository.groupInfoMap
    
    // 请求群列表
    fun requestGroupList() {
        viewModelScope.launch {
            repository.requestGroupList()
        }
    }
    
    // 更新群信息
    fun updateGroupInfo(groupId: String, groupName: String?, avatarUrl: String?, announcement: String?) {
        viewModelScope.launch {
            repository.updateGroupInfo(groupId, groupName, avatarUrl, announcement)
        }
    }
    
    // 更新群信息响应
    val updateInfoResult: StateFlow<com.example.androidim.model.GroupUpdateInfoResponse?> = repository.groupUpdateInfoResponse
    
    // 清空响应，避免重复触发
    fun clearInviteResult() {
        repository.clearGroupInviteResponse()
    }
    
    fun clearKickResult() {
        repository.clearGroupKickResponse()
    }
    
    fun clearQuitResult() {
        repository.clearGroupQuitResponse()
    }
    
    fun clearDismissResult() {
        repository.clearGroupDismissResponse()
    }
    
    fun clearUpdateInfoResult() {
        repository.clearGroupUpdateInfoResponse()
    }
}

