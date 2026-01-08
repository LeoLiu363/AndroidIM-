package com.example.androidim.ui.main

import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.androidim.R
import com.example.androidim.databinding.ActivityMainBinding
import com.example.androidim.ui.chat.ChatActivity
import com.example.androidim.ui.friend.FriendApplyActivity
import com.example.androidim.ui.friend.FriendListAdapter
import com.example.androidim.ui.friend.NewFriendActivity
import com.example.androidim.ui.login.LoginActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 主界面：显示个人信息和好友列表
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: FriendListAdapter

    // 双击退出相关变量
    private var backPressedTime: Long = 0
    private val backPressDelay: Long = 2000 // 2秒内再次点击才退出
    private var backPressedToast: Toast? = null
    private lateinit var backPressedCallback: OnBackPressedCallback
    
    // 定期刷新好友列表的Job
    private var refreshJob: Job? = null
    private val refreshInterval: Long = 5000 // 5秒刷新一次

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 检查登录状态，如果未登录则跳转到登录界面
        checkLoginStatus()

        setupBackPressHandler()
        setupRecyclerView()
        setupObservers()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        // 每次恢复时检查登录状态
        checkLoginStatus()
        // 立即刷新好友列表以获取最新的在线状态
        viewModel.refreshFriendList()
        // 启动定期刷新好友列表
        startPeriodicRefresh()
    }

    override fun onPause() {
        super.onPause()
        // 停止定期刷新
        stopPeriodicRefresh()
    }

    /**
     * 启动定期刷新好友列表
     */
    private fun startPeriodicRefresh() {
        stopPeriodicRefresh() // 先停止之前的刷新任务
        refreshJob = lifecycleScope.launch {
            while (true) {
                delay(refreshInterval)
                viewModel.refreshFriendList()
            }
        }
    }

    /**
     * 停止定期刷新好友列表
     */
    private fun stopPeriodicRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }

    /**
     * 检查登录状态，如果未登录则跳转到登录界面
     */
    private fun checkLoginStatus() {
        lifecycleScope.launch {
            // 只检查一次当前值，如果未登录则跳转
            val user = viewModel.currentUser.value
            if (user == null) {
                // 用户未登录，跳转到登录界面
                val intent = Intent(this@MainActivity, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }
    }

    /**
     * 设置双击返回键退出功能
     */
    private fun setupBackPressHandler() {
        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        }
        onBackPressedDispatcher.addCallback(this, backPressedCallback)
    }

    /**
     * 处理返回键按下事件
     * 双击返回键直接退出应用，不调用logout
     */
    private fun handleBackPress() {
        val currentTime = System.currentTimeMillis()
        if (backPressedTime == 0L || currentTime - backPressedTime > backPressDelay) {
            backPressedTime = currentTime
            backPressedToast?.cancel()
            backPressedToast = Toast.makeText(
                this,
                getString(R.string.press_again_to_exit),
                Toast.LENGTH_SHORT
            )
            backPressedToast?.show()
        } else {
            backPressedToast?.cancel()
            // 双击返回键：直接退出应用，不调用logout
            finishAffinity()
        }
    }

    private fun setupRecyclerView() {
        adapter = FriendListAdapter(
            onFriendClick = { friend ->
                // 点击好友后，跳转到聊天界面
                val intent = ChatActivity.newIntent(
                    this,
                    friend.userId,
                    friend.remark?.takeIf { it.isNotBlank() }
                        ?: friend.nickname?.takeIf { it.isNotBlank() }
                        ?: friend.username
                )
                startActivity(intent)
            },
            onFriendLongClick = { friend ->
                // 长按好友，显示删除确认对话框
                showDeleteFriendDialog(friend)
            },
            unreadCounts = emptyMap()
        )
        binding.recyclerViewFriends.adapter = adapter
        binding.recyclerViewFriends.layoutManager = LinearLayoutManager(this)
    }
    
    private fun showDeleteFriendDialog(friend: com.example.androidim.model.FriendInfo) {
        val displayName = friend.remark?.takeIf { it.isNotBlank() }
            ?: friend.nickname?.takeIf { it.isNotBlank() }
            ?: friend.username
        
        AlertDialog.Builder(this)
            .setTitle("删除好友")
            .setMessage("确定要删除好友 $displayName 吗？")
            .setPositiveButton("确定") { _, _ ->
                lifecycleScope.launch {
                    viewModel.deleteFriend(friend.userId)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun setupObservers() {
        // 显示当前用户信息
        lifecycleScope.launch {
            viewModel.currentUser.collect { user ->
                if (user != null) {
                    binding.textViewUsername.text = user.username
                    // 可以在这里添加更多个人信息显示，如头像、昵称等
                } else {
                    // 用户信息为空，只有在主动退出登录时才跳转到登录界面
                    // 这里不自动跳转，因为双击返回键退出时也会触发这里
                    // 跳转逻辑由退出登录按钮处理
                    binding.textViewUsername.text = ""
                }
            }
        }

        // 显示好友列表
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
        // 添加好友按钮
        binding.buttonAddFriend.setOnClickListener {
            startActivity(Intent(this, FriendApplyActivity::class.java))
        }

        // 新朋友按钮
        binding.buttonNewFriends.setOnClickListener {
            startActivity(Intent(this, NewFriendActivity::class.java))
        }

        // 群聊按钮
        binding.buttonGroupList.setOnClickListener {
            startActivity(Intent(this, com.example.androidim.ui.group.GroupListActivity::class.java))
        }

        // 退出登录按钮
        binding.buttonLogout.setOnClickListener {
            lifecycleScope.launch {
                // 先停掉周期性刷新，避免登出过程中仍发请求导致 1001 重复提示
                stopPeriodicRefresh()
                // 等待登出完成
                viewModel.logout()
                // 返回登录界面
                val intent = Intent(this@MainActivity, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }
    }
}

