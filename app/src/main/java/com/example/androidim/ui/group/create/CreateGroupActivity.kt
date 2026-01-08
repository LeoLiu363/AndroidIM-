package com.example.androidim.ui.group.create

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.androidim.databinding.ActivityCreateGroupBinding
import kotlinx.coroutines.launch

/**
 * 创建群界面
 */
class CreateGroupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateGroupBinding
    private val viewModel: CreateGroupViewModel by viewModels()
    
    private var selectedMemberIds: List<String> = emptyList()

    private val selectMembersLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val selectedIds = result.data?.getStringArrayListExtra("selected_member_ids")
            selectedMemberIds = selectedIds?.toList() ?: emptyList()
            updateSelectedMembersDisplay()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateGroupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupObservers()
        setupListeners()
        updateSelectedMembersDisplay()
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.createResult.collect { result ->
                android.util.Log.d("CreateGroupActivity", "收到创建群结果: $result")
                result?.let {
                    android.util.Log.d("CreateGroupActivity", "创建群结果: success=${it.success}, group=${it.group}, errorCode=${it.errorCode}, errorMessage=${it.errorMessage}")
                    if (it.success) {
                        Toast.makeText(this@CreateGroupActivity, "创建群成功", Toast.LENGTH_SHORT).show()
                        // 返回群列表
                        finish()
                    } else {
                        // 显示详细的错误信息
                        val errorMsg = it.errorMessage ?: "创建群失败"
                        Toast.makeText(this@CreateGroupActivity, errorMsg, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun setupListeners() {
        // 选择成员按钮
        binding.buttonSelectMembers.setOnClickListener {
            val intent = Intent(this, SelectMembersActivity::class.java)
            selectMembersLauncher.launch(intent)
        }

        // 创建群按钮
        binding.buttonCreate.setOnClickListener {
            val groupName = binding.editTextGroupName.text.toString().trim()

            if (groupName.isEmpty()) {
                Toast.makeText(this, "请输入群名称", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                viewModel.createGroup(groupName, selectedMemberIds)
            }
        }
    }

    private fun updateSelectedMembersDisplay() {
        if (selectedMemberIds.isEmpty()) {
            binding.textViewSelectedMembers.text = "未选择成员（可选）"
        } else {
            binding.textViewSelectedMembers.text = "已选择 ${selectedMemberIds.size} 位好友"
        }
    }
}

