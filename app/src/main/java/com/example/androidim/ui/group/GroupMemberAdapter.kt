package com.example.androidim.ui.group

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.androidim.databinding.ItemGroupMemberBinding
import com.example.androidim.model.GroupMember

class GroupMemberAdapter(
    private val currentUserRole: String,
    private val onMemberClick: (GroupMember) -> Unit
) : ListAdapter<GroupMember, GroupMemberAdapter.MemberViewHolder>(MemberDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val binding = ItemGroupMemberBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MemberViewHolder(binding, currentUserRole, onMemberClick)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MemberViewHolder(
        private val binding: ItemGroupMemberBinding,
        private val currentUserRole: String,
        private val onMemberClick: (GroupMember) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(member: GroupMember) {
            val displayName = member.nicknameInGroup?.takeIf { it.isNotBlank() }
                ?: member.userId
            binding.textViewMemberName.text = displayName

            binding.textViewMemberRole.text = when (member.role) {
                "owner" -> "群主"
                "admin" -> "管理员"
                else -> "成员"
            }

            binding.textViewMemberStatus.text = if (member.online) "在线" else "离线"
        }
    }

    class MemberDiffCallback : DiffUtil.ItemCallback<GroupMember>() {
        override fun areItemsTheSame(oldItem: GroupMember, newItem: GroupMember): Boolean {
            return oldItem.userId == newItem.userId
        }

        override fun areContentsTheSame(oldItem: GroupMember, newItem: GroupMember): Boolean {
            return oldItem == newItem
        }
    }
}




