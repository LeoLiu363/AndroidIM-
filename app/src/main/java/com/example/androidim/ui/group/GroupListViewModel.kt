package com.example.androidim.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidim.model.GroupListItem
import com.example.androidim.repository.IMRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 群列表 ViewModel
 */
class GroupListViewModel : ViewModel() {

    private val repository = IMRepository.getInstance()

    val groupList: StateFlow<List<GroupListItem>> = repository.groupList

    fun requestGroupList() {
        viewModelScope.launch {
            repository.requestGroupList()
        }
    }
}




