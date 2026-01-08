package com.example.androidim.ui.friend

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.androidim.databinding.ItemFriendBinding
import com.example.androidim.model.FriendInfo
import com.example.androidim.ui.chat.ChatActivity

class FriendListAdapter(
    private val onFriendClick: (FriendInfo) -> Unit,
    private val onFriendLongClick: ((FriendInfo) -> Unit)? = null,
    private val unreadCounts: Map<String, Int> = emptyMap()
) : ListAdapter<FriendInfo, FriendListAdapter.FriendViewHolder>(FriendDiffCallback()) {
    
    private var _unreadCounts: Map<String, Int> = unreadCounts
    
    fun updateUnreadCounts(counts: Map<String, Int>) {
        _unreadCounts = counts
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val binding = ItemFriendBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FriendViewHolder(binding, onFriendClick, onFriendLongClick)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        holder.bind(getItem(position), _unreadCounts)
    }

    class FriendViewHolder(
        private val binding: ItemFriendBinding,
        private val onFriendClick: (FriendInfo) -> Unit,
        private val onFriendLongClick: ((FriendInfo) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(friend: FriendInfo, unreadCounts: Map<String, Int>) {
            // 显示优先级：备注 > 昵称 > 用户名
            val displayName = friend.remark?.takeIf { it.isNotBlank() }
                ?: friend.nickname?.takeIf { it.isNotBlank() }
                ?: friend.username
            binding.textViewFriendName.text = displayName

            // 次要信息：显示用户名
            binding.textViewFriendUsername.text = "用户名: ${friend.username}"

            // 在线状态
            binding.textViewFriendStatus.text = if (friend.online) "在线" else "离线"
            
            // 显示未读消息数
            val unreadCount = unreadCounts[friend.userId] ?: 0
            if (unreadCount > 0) {
                binding.textViewUnreadCount.text = if (unreadCount > 99) "99+" else unreadCount.toString()
                binding.textViewUnreadCount.visibility = android.view.View.VISIBLE
            } else {
                binding.textViewUnreadCount.visibility = android.view.View.GONE
            }
            
            // 点击整个 item 进入聊天界面
            binding.root.setOnClickListener {
                onFriendClick(friend)
            }
            
            // 长按删除好友（保留长按功能）
            binding.root.setOnLongClickListener {
                onFriendLongClick?.invoke(friend)
                true
            }
        }
    }

    class FriendDiffCallback : DiffUtil.ItemCallback<FriendInfo>() {
        override fun areItemsTheSame(oldItem: FriendInfo, newItem: FriendInfo): Boolean {
            return oldItem.userId == newItem.userId
        }

        override fun areContentsTheSame(oldItem: FriendInfo, newItem: FriendInfo): Boolean {
            return oldItem == newItem
        }
    }
}



