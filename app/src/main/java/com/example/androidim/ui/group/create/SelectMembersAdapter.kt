package com.example.androidim.ui.group.create

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.androidim.databinding.ItemSelectMemberBinding
import com.example.androidim.model.FriendInfo

class SelectMembersAdapter(
    private val onMemberToggle: (FriendInfo, Boolean) -> Unit
) : ListAdapter<FriendInfo, SelectMembersAdapter.MemberViewHolder>(MemberDiffCallback()) {

    private val selectedIds = mutableSetOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val binding = ItemSelectMemberBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MemberViewHolder(binding, onMemberToggle)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        holder.bind(getItem(position), selectedIds.contains(getItem(position).userId))
    }

    fun updateSelectedIds(selectedIds: Set<String>) {
        this.selectedIds.clear()
        this.selectedIds.addAll(selectedIds)
        notifyDataSetChanged()
    }

    class MemberViewHolder(
        private val binding: ItemSelectMemberBinding,
        private val onMemberToggle: (FriendInfo, Boolean) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(friend: FriendInfo, isSelected: Boolean) {
            // 显示优先级：备注 > 昵称 > 用户名
            val displayName = friend.remark?.takeIf { it.isNotBlank() }
                ?: friend.nickname?.takeIf { it.isNotBlank() }
                ?: friend.username
            binding.textViewMemberName.text = displayName

            binding.textViewMemberUsername.text = "用户名: ${friend.username}"
            binding.textViewMemberStatus.text = if (friend.online) "在线" else "离线"

            // 设置选中状态
            binding.checkboxSelect.isChecked = isSelected

            binding.root.setOnClickListener {
                val newSelected = !isSelected
                binding.checkboxSelect.isChecked = newSelected
                onMemberToggle(friend, newSelected)
            }

            binding.checkboxSelect.setOnClickListener {
                onMemberToggle(friend, binding.checkboxSelect.isChecked)
            }
        }
    }

    class MemberDiffCallback : DiffUtil.ItemCallback<FriendInfo>() {
        override fun areItemsTheSame(oldItem: FriendInfo, newItem: FriendInfo): Boolean {
            return oldItem.userId == newItem.userId
        }

        override fun areContentsTheSame(oldItem: FriendInfo, newItem: FriendInfo): Boolean {
            return oldItem == newItem
        }
    }
}




