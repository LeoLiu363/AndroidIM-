package com.example.androidim.ui.friend

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.androidim.R
import com.example.androidim.databinding.ActivityFriendApplyBinding
import kotlinx.coroutines.launch

/**
 * 发送好友申请界面
 */
class FriendApplyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFriendApplyBinding
    private val viewModel: FriendApplyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFriendApplyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
        setupObservers()
    }

    private fun setupViews() {
        binding.buttonSendApply.setOnClickListener {
            val targetId = binding.editTextTargetUserId.text.toString().trim()
            val greeting = binding.editTextGreeting.text.toString().trim().ifEmpty { null }
            val remark = binding.editTextRemark.text.toString().trim().ifEmpty { null }

            if (targetId.isEmpty()) {
                Toast.makeText(this, getString(R.string.friend_target_user_id_hint), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.sendFriendApply(targetId, greeting, remark)
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.isSending.collect { sending ->
                binding.buttonSendApply.isEnabled = !sending
            }
        }

        lifecycleScope.launch {
            viewModel.applyResult.collect { result ->
                result?.let {
                    if (it.success) {
                        Toast.makeText(this@FriendApplyActivity, getString(R.string.friend_apply_success), Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(
                            this@FriendApplyActivity,
                            it.message ?: getString(R.string.friend_apply_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }
}






