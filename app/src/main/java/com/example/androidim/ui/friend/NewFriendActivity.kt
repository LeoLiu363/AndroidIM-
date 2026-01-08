package com.example.androidim.ui.friend

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.androidim.R
import com.example.androidim.databinding.ActivityNewFriendBinding
import kotlinx.coroutines.launch

class NewFriendActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNewFriendBinding
    private val viewModel: NewFriendViewModel by viewModels()
    private lateinit var adapter: NewFriendAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewFriendBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupObservers()
    }

    private fun setupRecyclerView() {
        adapter = NewFriendAdapter(
            onAccept = { notify ->
                viewModel.accept(notify.applyId, null)
            },
            onReject = { notify ->
                viewModel.reject(notify.applyId)
            }
        )
        binding.recyclerViewNewFriends.adapter = adapter
        binding.recyclerViewNewFriends.layoutManager = LinearLayoutManager(this)
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.applyList.collect { list ->
                adapter.submitList(list)
            }
        }

        lifecycleScope.launch {
            viewModel.handleResult.collect { result ->
                result?.let {
                    val msg = if (it.success) {
                        getString(R.string.friend_handle_success)
                    } else {
                        getString(R.string.friend_handle_failed)
                    }
                    Toast.makeText(this@NewFriendActivity, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}






