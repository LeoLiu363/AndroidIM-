package com.example.androidim.ui.friend

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.androidim.databinding.ItemNewFriendBinding
import com.example.androidim.model.FriendApplyNotify

class NewFriendAdapter(
    private val onAccept: (FriendApplyNotify) -> Unit,
    private val onReject: (FriendApplyNotify) -> Unit
) : ListAdapter<FriendApplyNotify, NewFriendAdapter.NewFriendViewHolder>(NewFriendDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewFriendViewHolder {
        val binding = ItemNewFriendBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NewFriendViewHolder(binding, onAccept, onReject)
    }

    override fun onBindViewHolder(holder: NewFriendViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class NewFriendViewHolder(
        private val binding: ItemNewFriendBinding,
        private val onAccept: (FriendApplyNotify) -> Unit,
        private val onReject: (FriendApplyNotify) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FriendApplyNotify) {
            val from = item.fromUser
            val displayName = from.nickname?.takeIf { it.isNotBlank() } ?: from.username
            binding.textViewApplyName.text = displayName
            binding.textViewApplyGreeting.text = item.greeting ?: ""

            binding.buttonAccept.setOnClickListener {
                onAccept(item)
            }
            binding.buttonReject.setOnClickListener {
                onReject(item)
            }
        }
    }

    class NewFriendDiffCallback : DiffUtil.ItemCallback<FriendApplyNotify>() {
        override fun areItemsTheSame(oldItem: FriendApplyNotify, newItem: FriendApplyNotify): Boolean {
            return oldItem.applyId == newItem.applyId
        }

        override fun areContentsTheSame(oldItem: FriendApplyNotify, newItem: FriendApplyNotify): Boolean {
            return oldItem == newItem
        }
    }
}






