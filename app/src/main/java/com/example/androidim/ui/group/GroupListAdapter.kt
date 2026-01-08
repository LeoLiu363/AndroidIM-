package com.example.androidim.ui.group

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.androidim.databinding.ItemGroupBinding
import com.example.androidim.model.GroupListItem

class GroupListAdapter(
    private val onGroupClick: (GroupListItem) -> Unit
) : ListAdapter<GroupListItem, GroupListAdapter.GroupViewHolder>(GroupDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val binding = ItemGroupBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return GroupViewHolder(binding, onGroupClick)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class GroupViewHolder(
        private val binding: ItemGroupBinding,
        private val onGroupClick: (GroupListItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(group: GroupListItem) {
            binding.textViewGroupName.text = group.groupName
            binding.textViewGroupRole.text = when (group.role) {
                "owner" -> "群主"
                "admin" -> "管理员"
                else -> "成员"
            }
            
            binding.root.setOnClickListener {
                onGroupClick(group)
            }
        }
    }

    class GroupDiffCallback : DiffUtil.ItemCallback<GroupListItem>() {
        override fun areItemsTheSame(oldItem: GroupListItem, newItem: GroupListItem): Boolean {
            return oldItem.groupId == newItem.groupId
        }

        override fun areContentsTheSame(oldItem: GroupListItem, newItem: GroupListItem): Boolean {
            return oldItem == newItem
        }
    }
}

