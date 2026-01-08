package com.example.androidim.ui.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.androidim.databinding.ItemChatMessageBinding
import com.example.androidim.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter : ListAdapter<ChatMessage, ChatAdapter.MessageViewHolder>(MessageDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemChatMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MessageViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    class MessageViewHolder(
        private val binding: ItemChatMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(message: ChatMessage) {
            binding.textViewUsername.text = message.fromUsername
            binding.textViewContent.text = message.content
            
            val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val timeString = dateFormat.format(Date(message.timestamp * 1000))
            binding.textViewTime.text = timeString
        }
    }
    
    class MessageDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.fromUserId == newItem.fromUserId && 
                   oldItem.timestamp == newItem.timestamp
        }
        
        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem == newItem
        }
    }
}







