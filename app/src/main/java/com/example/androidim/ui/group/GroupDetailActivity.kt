package com.example.androidim.ui.group

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.androidim.R
import com.example.androidim.databinding.ActivityGroupDetailBinding
import com.example.androidim.ui.group.create.SelectMembersActivity
import kotlinx.coroutines.launch

/**
 * 群详情/设置界面
 */
class GroupDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGroupDetailBinding
    private val viewModel: GroupDetailViewModel by viewModels()

    private var groupId: String? = null
    private var currentUserRole: String? = null  // "owner" / "admin" / "member"

    private val selectMembersLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val selectedIds = result.data?.getStringArrayListExtra("selected_member_ids")
            val memberIds = selectedIds?.toList() ?: emptyList()
            if (memberIds.isNotEmpty() && groupId != null) {
                lifecycleScope.launch {
                    viewModel.inviteMembers(groupId!!, memberIds)
                }
            }
        }
    }

    companion object {
        private const val EXTRA_GROUP_ID = "group_id"
        private const val EXTRA_GROUP_NAME = "group_name"
        private const val EXTRA_USER_ROLE = "user_role"

        fun newIntent(
            context: android.content.Context,
            groupId: String,
            groupName: String,
            userRole: String
        ): Intent {
            return Intent(context, GroupDetailActivity::class.java).apply {
                putExtra(EXTRA_GROUP_ID, groupId)
                putExtra(EXTRA_GROUP_NAME, groupName)
                putExtra(EXTRA_USER_ROLE, userRole)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        groupId = intent.getStringExtra(EXTRA_GROUP_ID)
        val groupName = intent.getStringExtra(EXTRA_GROUP_NAME)
        currentUserRole = intent.getStringExtra(EXTRA_USER_ROLE)

        if (groupId == null) {
            Toast.makeText(this, "缺少群信息", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.textViewGroupName.text = groupName ?: "群聊"
        
        // 设置 Toolbar 为 ActionBar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        
        // 处理返回键
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
        
        // 显示群ID
        binding.textViewGroupId.text = "群ID: $groupId"
        
        // 设置群ID到ViewModel
        viewModel.setGroupId(groupId!!)

        setupRecyclerView()
        setupObservers()
        setupListeners()
        
        // 根据用户角色显示/隐藏按钮
        updateButtonVisibility()

        // 请求群成员列表
        lifecycleScope.launch {
            viewModel.requestGroupMemberList(groupId!!)
        }
        
        // 观察群成员列表，更新成员数量
        lifecycleScope.launch {
            viewModel.groupMembers.collect { members ->
                binding.textViewMemberCount.text = "成员数量: ${members.size}"
            }
        }
        
        // 观察群信息Map，更新公告显示
        lifecycleScope.launch {
            viewModel.groupInfoMap.collect { infoMap ->
                infoMap[groupId]?.let { groupInfo ->
                    updateAnnouncementDisplay(groupInfo.announcement)
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        // 仅群主/管理员显示踢人按钮
        val isOwnerOrAdmin = currentUserRole == "owner" || currentUserRole == "admin"
        if (isOwnerOrAdmin) {
            menuInflater.inflate(R.menu.menu_group_detail, menu)
        }
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_kick_member -> {
                showSelectMemberToKickDialog()
                true
            }
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        // 每次恢复时刷新群成员列表
        groupId?.let {
            lifecycleScope.launch {
                viewModel.requestGroupMemberList(it)
            }
        }
        // 刷新菜单（因为角色可能变化）
        invalidateOptionsMenu()
    }
    
    private fun updateButtonVisibility() {
        val isOwner = currentUserRole == "owner"
        val isAdmin = currentUserRole == "admin"
        val isOwnerOrAdmin = isOwner || isAdmin

        binding.buttonInviteMembers.visibility = if (isOwnerOrAdmin) android.view.View.VISIBLE else android.view.View.GONE
        binding.buttonUpdateInfo.visibility = if (isOwnerOrAdmin) android.view.View.VISIBLE else android.view.View.GONE
        binding.buttonQuitGroup.visibility = if (!isOwner) android.view.View.VISIBLE else android.view.View.GONE
        binding.buttonDismissGroup.visibility = if (isOwner) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun setupRecyclerView() {
        val adapter = GroupMemberAdapter(
            currentUserRole = currentUserRole ?: "member",
            onMemberClick = { } // 不再使用长按功能
        )
        binding.recyclerViewMembers.adapter = adapter
        binding.recyclerViewMembers.layoutManager = LinearLayoutManager(this)
        
        lifecycleScope.launch {
            viewModel.groupMembers.collect { members ->
                adapter.submitList(members)
            }
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.inviteResult.collect { result ->
                result?.let {
                    if (it.success) {
                        Toast.makeText(this@GroupDetailActivity, "邀请成功", Toast.LENGTH_SHORT).show()
                        // 刷新群成员列表
                        groupId?.let { id ->
                            viewModel.requestGroupMemberList(id)
                        }
                        // 清空响应，避免重复触发
                        viewModel.clearInviteResult()
                    } else {
                        Toast.makeText(this@GroupDetailActivity, it.errorMessage ?: "邀请失败", Toast.LENGTH_SHORT).show()
                        // 清空响应，避免重复触发
                        viewModel.clearInviteResult()
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.kickResult.collect { result ->
                result?.let {
                    if (it.success) {
                        Toast.makeText(this@GroupDetailActivity, "已踢出成员", Toast.LENGTH_SHORT).show()
                        // 刷新群成员列表
                        groupId?.let { id ->
                            viewModel.requestGroupMemberList(id)
                        }
                        viewModel.clearKickResult()
                    } else {
                        Toast.makeText(this@GroupDetailActivity, it.errorMessage ?: "踢人失败", Toast.LENGTH_SHORT).show()
                        viewModel.clearKickResult()
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.quitResult.collect { result ->
                result?.let {
                    if (it.success) {
                        Toast.makeText(this@GroupDetailActivity, "已退出群聊", Toast.LENGTH_SHORT).show()
                        viewModel.clearQuitResult()
                        finish()
                    } else {
                        Toast.makeText(this@GroupDetailActivity, it.errorMessage ?: "退群失败", Toast.LENGTH_SHORT).show()
                        viewModel.clearQuitResult()
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.dismissResult.collect { result ->
                result?.let {
                    if (it.success) {
                        Toast.makeText(this@GroupDetailActivity, "已解散群聊", Toast.LENGTH_SHORT).show()
                        viewModel.clearDismissResult()
                        finish()
                    } else {
                        Toast.makeText(this@GroupDetailActivity, it.errorMessage ?: "解散失败", Toast.LENGTH_SHORT).show()
                        viewModel.clearDismissResult()
                    }
                }
            }
        }
        
        lifecycleScope.launch {
            viewModel.updateInfoResult.collect { result ->
                result?.let {
                    if (it.success) {
                        Toast.makeText(this@GroupDetailActivity, "更新群信息成功", Toast.LENGTH_SHORT).show()
                        // 更新标题和公告
                        it.group?.let { groupInfo ->
                            binding.textViewGroupName.text = groupInfo.groupName
                            // 更新公告显示
                            val announcement = groupInfo.announcement
                            if (announcement.isNullOrBlank()) {
                                binding.textViewAnnouncement.text = ""
                                binding.textViewAnnouncement.hint = "暂无群公告"
                            } else {
                                binding.textViewAnnouncement.text = announcement
                            }
                        }
                        // 刷新群列表
                        lifecycleScope.launch {
                            viewModel.requestGroupList()
                        }
                        viewModel.clearUpdateInfoResult()
                    } else {
                        Toast.makeText(this@GroupDetailActivity, it.errorMessage ?: "更新群信息失败", Toast.LENGTH_SHORT).show()
                        viewModel.clearUpdateInfoResult()
                    }
                }
            }
        }
        
        // 从群成员列表响应中获取群信息（如果服务端返回了群信息）
        // 或者从更新群信息响应中获取
        // 暂时在更新群信息时显示公告
    }

    private fun setupListeners() {
        // 邀请成员按钮（群主/管理员可见）
        binding.buttonInviteMembers.setOnClickListener {
            val intent = Intent(this, SelectMembersActivity::class.java)
            selectMembersLauncher.launch(intent)
        }

        // 更新群信息按钮（群主/管理员可见）
        binding.buttonUpdateInfo.setOnClickListener {
            showUpdateInfoDialog()
        }

        // 退群按钮（普通成员可见）
        binding.buttonQuitGroup.setOnClickListener {
            showQuitGroupDialog()
        }

        // 解散群按钮（仅群主可见）
        binding.buttonDismissGroup.setOnClickListener {
            showDismissGroupDialog()
        }
    }

    private fun showSelectMemberToKickDialog() {
        // 获取当前群成员列表
        val members = viewModel.groupMembers.value
        val currentUserId = viewModel.getCurrentUserId()
        
        // 过滤出可以踢出的成员（不是群主，不是当前用户）
        val kickableMembers = members.filter { 
            it.role != "owner" && it.userId != currentUserId 
        }
        
        if (kickableMembers.isEmpty()) {
            Toast.makeText(this, "没有可踢出的成员", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 创建成员名称列表
        val memberNames = kickableMembers.map { 
            it.nicknameInGroup?.takeIf { name -> name.isNotBlank() } ?: it.userId 
        }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle("选择要踢出的成员")
            .setItems(memberNames) { _, which ->
                val selectedMember = kickableMembers[which]
                showKickMemberDialog(selectedMember.userId, memberNames[which])
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showKickMemberDialog(userId: String, memberName: String) {
        AlertDialog.Builder(this)
            .setTitle("踢出成员")
            .setMessage("确定要踢出 $memberName 吗？")
            .setPositiveButton("确定") { _, _ ->
                groupId?.let {
                    lifecycleScope.launch {
                        viewModel.kickMember(it, userId)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showQuitGroupDialog() {
        AlertDialog.Builder(this)
            .setTitle("退出群聊")
            .setMessage("确定要退出此群聊吗？")
            .setPositiveButton("确定") { _, _ ->
                groupId?.let {
                    lifecycleScope.launch {
                        viewModel.quitGroup(it)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDismissGroupDialog() {
        AlertDialog.Builder(this)
            .setTitle("解散群聊")
            .setMessage("确定要解散此群聊吗？解散后所有成员将被移除。")
            .setPositiveButton("确定") { _, _ ->
                groupId?.let {
                    lifecycleScope.launch {
                        viewModel.dismissGroup(it)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showUpdateInfoDialog() {
        val dialogView = layoutInflater.inflate(com.example.androidim.R.layout.dialog_update_group_info, null)
        val editTextGroupName = dialogView.findViewById<android.widget.EditText>(com.example.androidim.R.id.editTextGroupName)
        val editTextAnnouncement = dialogView.findViewById<android.widget.EditText>(com.example.androidim.R.id.editTextAnnouncement)
        
        // 填充当前群名称和公告
        editTextGroupName.setText(binding.textViewGroupName.text.toString())
        val currentAnnouncement = binding.textViewAnnouncement.text.toString()
        if (currentAnnouncement.isNotEmpty() && currentAnnouncement != binding.textViewAnnouncement.hint) {
            editTextAnnouncement.setText(currentAnnouncement)
        } else {
            // 尝试从群信息Map中获取
            viewModel.getGroupInfo(groupId!!)?.let { groupInfo ->
                editTextAnnouncement.setText(groupInfo.announcement ?: "")
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("更新群信息")
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                val groupName = editTextGroupName.text.toString().trim()
                val announcement = editTextAnnouncement.text.toString().trim()
                
                if (groupName.isEmpty()) {
                    Toast.makeText(this, "群名称不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                groupId?.let { id ->
                    lifecycleScope.launch {
                        viewModel.updateGroupInfo(id, groupName, null, announcement.takeIf { it.isNotEmpty() })
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun updateAnnouncementDisplay(announcement: String?) {
        if (announcement.isNullOrBlank()) {
            binding.textViewAnnouncement.text = ""
            binding.textViewAnnouncement.hint = "暂无群公告"
        } else {
            binding.textViewAnnouncement.text = announcement
            binding.textViewAnnouncement.hint = null
        }
    }

}

