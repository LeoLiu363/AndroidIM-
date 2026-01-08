package com.example.androidim.ui.userlist

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.androidim.databinding.ActivityUserListBinding
import kotlinx.coroutines.launch

class UserListActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityUserListBinding
    private val viewModel: UserListViewModel by viewModels()
    private lateinit var adapter: UserListAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupRecyclerView()
        setupObservers()
    }
    
    private fun setupRecyclerView() {
        adapter = UserListAdapter()
        binding.recyclerViewUsers.adapter = adapter
        binding.recyclerViewUsers.layoutManager = LinearLayoutManager(this)
    }
    
    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.userList.collect { users ->
                adapter.submitList(users)
            }
        }
    }
}







