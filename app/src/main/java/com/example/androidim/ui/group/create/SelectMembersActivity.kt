package com.example.androidim.ui.group.create

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.androidim.databinding.ActivitySelectMembersBinding
import kotlinx.coroutines.launch

/**
 * 选择群成员界面（从好友列表中选择）
 */
class SelectMembersActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySelectMembersBinding
    private val viewModel: SelectMembersViewModel by viewModels()
    private lateinit var adapter: SelectMembersAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectMembersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupObservers()
        setupListeners()
    }

    private fun setupRecyclerView() {
        adapter = SelectMembersAdapter(
            onMemberToggle = { friend, isSelected ->
                viewModel.toggleMember(friend.userId, isSelected)
            }
        )
        binding.recyclerViewMembers.adapter = adapter
        binding.recyclerViewMembers.layoutManager = LinearLayoutManager(this)
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.friendList.collect { friends ->
                adapter.submitList(friends)
            }
        }

        lifecycleScope.launch {
            viewModel.selectedMemberIds.collect { selectedIds ->
                binding.textViewSelectedCount.text = "已选择 ${selectedIds.size} 人"
                adapter.updateSelectedIds(selectedIds)
            }
        }
    }

    private fun setupListeners() {
        binding.buttonConfirm.setOnClickListener {
            val selectedIds = viewModel.selectedMemberIds.value
            if (selectedIds.isEmpty()) {
                Toast.makeText(this, "请至少选择一位好友", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 返回选中的成员ID列表
            val resultIntent = Intent().apply {
                putStringArrayListExtra("selected_member_ids", ArrayList(selectedIds))
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }

        binding.buttonCancel.setOnClickListener {
            finish()
        }
    }
}

