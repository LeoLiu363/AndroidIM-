package com.example.androidim.ui.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.androidim.R
import com.example.androidim.databinding.ActivityChatBinding
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityChatBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var adapter: ChatAdapter
    
    // 聊天对象信息（从 Intent 获取）
    private var targetUserId: String? = null
    private var targetUsername: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 从 Intent 获取聊天对象信息
        targetUserId = intent.getStringExtra(EXTRA_TARGET_USER_ID)
        targetUsername = intent.getStringExtra(EXTRA_TARGET_USERNAME)
        
        // 必须要有聊天对象才能进入单聊界面
        if (targetUserId == null) {
            Toast.makeText(this, "缺少聊天对象信息", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // 设置 ViewModel 的聊天对象
        viewModel.setTargetUserId(targetUserId!!)
        
        // 清除该好友的未读消息数
        viewModel.clearUnreadCount(targetUserId!!)
        
        // 设置返回键处理器（返回到好友列表）
        setupBackPressHandler()
        
        // 设置窗口，让系统自动处理输入法（adjustResize）
        // 然后通过 WindowInsets 监听 IME 高度变化，动态调整输入框位置
        setupWindowInsets()
        
        setupRecyclerView()
        setupObservers()
        setupListeners()
    }
    
    /**
     * 设置返回键处理器：返回到好友列表界面
     */
    private fun setupBackPressHandler() {
        // 使用 OnBackPressedCallback 处理返回键
        val backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 直接返回到上一个 Activity（MainActivity）
                finish()
            }
        }
        // 将回调添加到 dispatcher
        onBackPressedDispatcher.addCallback(this, backPressedCallback)
    }
    
    private fun setupWindowInsets() {
        val rootView = binding.root
        val layoutInput = binding.layoutInput
        val recyclerView = binding.recyclerViewMessages
        
        // 获取初始的 padding 值
        val inputPaddingBottom = 8  // 初始 paddingBottom
        val recyclerPaddingBottom = 8  // 初始 paddingBottom
        
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            // 获取系统导航栏的 insets（底部导航栏）
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // 获取输入法（IME）的 insets - 这是关键！
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            
            Log.d("ChatActivity", "[Insets] 系统导航栏高度: ${systemBars.bottom}, IME高度: ${imeInsets.bottom}")
            
            // 输入框的底部 padding = 系统导航栏高度 + IME 高度 + 初始 padding
            // 这样输入框就会在输入法上方
            val inputBottomPadding = systemBars.bottom + imeInsets.bottom + inputPaddingBottom
            
            // 为输入框布局添加底部padding，确保在输入法上方
            layoutInput.setPadding(
                layoutInput.paddingStart,
                layoutInput.paddingTop,
                layoutInput.paddingEnd,
                inputBottomPadding
            )
            
            // RecyclerView 的底部 padding = 系统导航栏高度 + IME 高度 + 输入框高度 + 初始 padding
            // 这样 RecyclerView 不会被输入法遮挡
            val recyclerBottomPadding = systemBars.bottom + imeInsets.bottom + inputPaddingBottom + recyclerPaddingBottom
            
            // 为RecyclerView添加底部padding，确保最后一条消息可见
            recyclerView.setPadding(
                recyclerView.paddingStart,
                recyclerView.paddingTop,
                recyclerView.paddingEnd,
                recyclerBottomPadding
            )
            
            // 如果输入法显示，滚动到底部
            if (imeInsets.bottom > 0) {
                recyclerView.post {
                    val adapter = recyclerView.adapter
                    if (adapter != null && adapter.itemCount > 0) {
                        recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
                    }
                }
            }
            
            WindowInsetsCompat.CONSUMED
        }
    }
    
    private fun setupRecyclerView() {
        adapter = ChatAdapter()
        binding.recyclerViewMessages.adapter = adapter
        binding.recyclerViewMessages.layoutManager = LinearLayoutManager(this)
    }
    
    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.messages.collect { messages ->
                adapter.submitList(messages)
                if (messages.isNotEmpty()) {
                    binding.recyclerViewMessages.scrollToPosition(messages.size - 1)
                }
            }
        }
        
        lifecycleScope.launch {
            viewModel.currentUser.collect { user ->
                // 如果有聊天对象，显示聊天对象的名字；否则显示当前用户的名字
                val title = if (targetUsername != null) {
                    "与 $targetUsername 的聊天"
                } else {
                    "聊天 - ${user?.username ?: "未知"}"
                }
                supportActionBar?.title = title
            }
        }
    }
    
    private fun setupListeners() {
        binding.buttonSend.setOnClickListener {
            val content = binding.editTextMessage.text.toString()
            if (content.isNotEmpty()) {
                lifecycleScope.launch {
                    // 发送给聊天对象（单聊模式）
                    val success = viewModel.sendMessage(targetUserId!!, content)
                    if (success) {
                        binding.editTextMessage.setText("")
                    } else {
                        Toast.makeText(this@ChatActivity, "发送失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    
    companion object {
        private const val EXTRA_TARGET_USER_ID = "target_user_id"
        private const val EXTRA_TARGET_USERNAME = "target_username"
        
        fun newIntent(context: Context): Intent {
            return Intent(context, ChatActivity::class.java)
        }
        
        /**
         * 创建与指定好友聊天的 Intent
         */
        fun newIntent(context: Context, targetUserId: String, targetUsername: String): Intent {
            return Intent(context, ChatActivity::class.java).apply {
                putExtra(EXTRA_TARGET_USER_ID, targetUserId)
                putExtra(EXTRA_TARGET_USERNAME, targetUsername)
            }
        }
    }
}

