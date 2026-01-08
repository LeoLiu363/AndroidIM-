package com.example.androidim.ui.group

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.androidim.R
import com.example.androidim.databinding.ActivityGroupChatBinding
import com.example.androidim.ui.chat.ChatAdapter
import kotlinx.coroutines.launch

/**
 * 群聊界面
 */
class GroupChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGroupChatBinding
    private val viewModel: GroupChatViewModel by viewModels()
    private lateinit var adapter: ChatAdapter

    private var groupId: String? = null
    private var groupName: String? = null

    companion object {
        private const val EXTRA_GROUP_ID = "group_id"
        private const val EXTRA_GROUP_NAME = "group_name"

        fun newIntent(context: Context, groupId: String, groupName: String): Intent {
            return Intent(context, GroupChatActivity::class.java).apply {
                putExtra(EXTRA_GROUP_ID, groupId)
                putExtra(EXTRA_GROUP_NAME, groupName)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        groupId = intent.getStringExtra(EXTRA_GROUP_ID)
        groupName = intent.getStringExtra(EXTRA_GROUP_NAME)

        if (groupId == null) {
            Toast.makeText(this, "缺少群信息", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 设置标题
        binding.textViewGroupName.text = groupName ?: "群聊"
        
        // 设置 Toolbar 为 ActionBar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        
        // 处理返回键
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        // 设置 ViewModel 的群ID
        viewModel.setGroupId(groupId!!)

        setupRecyclerView()
        setupObservers()
        setupListeners()
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
                // 滚动到底部
                if (messages.isNotEmpty()) {
                    binding.recyclerViewMessages.post {
                        binding.recyclerViewMessages.smoothScrollToPosition(messages.size - 1)
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_group_chat, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_group_detail -> {
                // 从群列表中获取当前用户的角色
                val userRole = viewModel.getUserRoleInGroup(groupId!!)
                val intent = GroupDetailActivity.newIntent(
                    this,
                    groupId!!,
                    groupName ?: "群聊",
                    userRole
                )
                startActivity(intent)
                true
            }
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupListeners() {
        binding.buttonSend.setOnClickListener {
            val content = binding.editTextMessage.text.toString()
            if (content.isNotEmpty() && groupId != null) {
                lifecycleScope.launch {
                    val success = viewModel.sendMessage(groupId!!, content)
                    if (success) {
                        binding.editTextMessage.setText("")
                    } else {
                        Toast.makeText(this@GroupChatActivity, "发送失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

