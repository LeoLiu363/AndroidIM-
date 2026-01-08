package com.example.androidim.ui.userlist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.androidim.databinding.ItemUserBinding
import com.example.androidim.model.UserInfoItem

class UserListAdapter : ListAdapter<UserInfoItem, UserListAdapter.UserViewHolder>(UserDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return UserViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    class UserViewHolder(
        private val binding: ItemUserBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(user: UserInfoItem) {
            // 显示用户名
            binding.textViewUsername.text = user.username
            
            // 隐藏昵称 TextView（项目中没有昵称）
            binding.textViewNickname.visibility = android.view.View.GONE
            
            binding.textViewStatus.text = if (user.online) "在线" else "离线"
        }
    }
    
    class UserDiffCallback : DiffUtil.ItemCallback<UserInfoItem>() {
        override fun areItemsTheSame(oldItem: UserInfoItem, newItem: UserInfoItem): Boolean {
            return oldItem.userId == newItem.userId
        }
        
        override fun areContentsTheSame(oldItem: UserInfoItem, newItem: UserInfoItem): Boolean {
            return oldItem == newItem
        }
    }
}



