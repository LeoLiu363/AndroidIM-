package com.example.androidim.ui.friend

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.androidim.databinding.ActivityFriendListBinding
import com.example.androidim.ui.chat.ChatActivity
import kotlinx.coroutines.launch

/**
 * 好友列表界面
 */
class FriendListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFriendListBinding
    private val viewModel: FriendListViewModel by viewModels()
    private lateinit var adapter: FriendListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFriendListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupObservers()
        setupListeners()
    }

    private fun setupRecyclerView() {
        adapter = FriendListAdapter(
            onFriendClick = { friend ->
                // 点击好友后，跳转到聊天界面
                val intent = ChatActivity.newIntent(
                    this@FriendListActivity,
                    friend.userId,
                    friend.remark?.takeIf { it.isNotBlank() }
                        ?: friend.nickname?.takeIf { it.isNotBlank() }
                        ?: friend.username
                )
                startActivity(intent)
            },
            unreadCounts = emptyMap()
        )
        binding.recyclerViewFriends.adapter = adapter
        binding.recyclerViewFriends.layoutManager = LinearLayoutManager(this)
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.friendList.collect { friends ->
                adapter.submitList(friends)
            }
        }
        
        // 监听未读消息数变化并更新适配器
        lifecycleScope.launch {
            viewModel.unreadMessageCounts.collect { counts ->
                adapter.updateUnreadCounts(counts)
            }
        }
    }

    private fun setupListeners() {
        binding.buttonAddFriend.setOnClickListener {
            startActivity(Intent(this, FriendApplyActivity::class.java))
        }

        binding.buttonNewFriends.setOnClickListener {
            startActivity(Intent(this, NewFriendActivity::class.java))
        }
    }
}


