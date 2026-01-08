package com.example.androidim.ui.group

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.androidim.databinding.ActivityGroupListBinding
import com.example.androidim.ui.group.create.CreateGroupActivity
import kotlinx.coroutines.launch

/**
 * 群列表界面
 */
class GroupListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGroupListBinding
    private val viewModel: GroupListViewModel by viewModels()
    private lateinit var adapter: GroupListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupObservers()
        setupListeners()
        
        // 请求群列表
        lifecycleScope.launch {
            viewModel.requestGroupList()
        }
    }

    override fun onResume() {
        super.onResume()
        // 每次恢复时刷新群列表
        lifecycleScope.launch {
            viewModel.requestGroupList()
        }
    }

    private fun setupRecyclerView() {
        adapter = GroupListAdapter(
            onGroupClick = { group ->
                // 点击群后，跳转到群聊界面
                val intent = GroupChatActivity.newIntent(
                    this,
                    group.groupId,
                    group.groupName
                )
                startActivity(intent)
            }
        )
        binding.recyclerViewGroups.adapter = adapter
        binding.recyclerViewGroups.layoutManager = LinearLayoutManager(this)
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.groupList.collect { groups ->
                adapter.submitList(groups)
            }
        }
    }

    private fun setupListeners() {
        // 创建群按钮
        binding.buttonCreateGroup.setOnClickListener {
            startActivity(Intent(this, CreateGroupActivity::class.java))
        }
    }
}




