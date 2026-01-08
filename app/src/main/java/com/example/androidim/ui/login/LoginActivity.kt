package com.example.androidim.ui.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.androidim.R
import com.example.androidim.databinding.ActivityLoginBinding
import com.example.androidim.ui.main.MainActivity
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()
    
    // 双击退出相关变量
    private var backPressedTime: Long = 0
    private val backPressDelay: Long = 2000 // 2秒内再次点击才退出
    private var backPressedToast: Toast? = null
    private lateinit var backPressedCallback: OnBackPressedCallback
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupBackPressHandler()
        setupObservers()
        setupListeners()
    }
    
    /**
     * 设置双击返回键退出功能
     */
    private fun setupBackPressHandler() {
        Log.d("LoginActivity", "设置返回键处理器")
        // 使用 OnBackPressedCallback 处理返回键
        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d("LoginActivity", "OnBackPressedCallback.handleOnBackPressed() 被调用")
                handleBackPress()
            }
        }
        
        // 将回调添加到 dispatcher
        onBackPressedDispatcher.addCallback(this, backPressedCallback)
        Log.d("LoginActivity", "返回键回调已注册，enabled=${backPressedCallback.isEnabled}")
    }
    
    /**
     * 处理返回键按下事件
     */
    private fun handleBackPress() {
        val currentTime = System.currentTimeMillis()
        Log.d("LoginActivity", "返回键被按下，当前时间: $currentTime, 上次时间: $backPressedTime")
        
        // 如果距离上次点击超过2秒，或者是第一次点击，显示提示
        if (backPressedTime == 0L || currentTime - backPressedTime > backPressDelay) {
            Log.d("LoginActivity", "显示退出提示")
            backPressedTime = currentTime
            // 取消之前的 Toast（如果有）
            backPressedToast?.cancel()
            // 显示提示
            backPressedToast = Toast.makeText(
                this,
                getString(R.string.press_again_to_exit),
                Toast.LENGTH_SHORT
            )
            backPressedToast?.show()
        } else {
            // 2秒内再次点击，退出应用
            Log.d("LoginActivity", "2秒内再次点击，退出应用")
            backPressedToast?.cancel()
            finish()
        }
    }
    
    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.loginResult.collect { result ->
                result?.let {
                    if (it.success) {
                        // 登录成功，跳转到主界面
                        val intent = Intent(this@LoginActivity, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    } else {
                        // 简单节流：避免短时间重复 Toast（例如登出后服务端返回 1001）
                        viewModel.shouldShowLoginToast(it.message)?.let { msg ->
                            Toast.makeText(this@LoginActivity, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        
        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.buttonLogin.isEnabled = !isLoading
                binding.buttonRegister.isEnabled = !isLoading
            }
        }
        
        // 记录上一次的连接状态，只在从连接变为断开时提示
        var previousConnected = false
        lifecycleScope.launch {
            viewModel.connectionState.collect { connected ->
                if (previousConnected && !connected) {
                    // 从连接状态变为断开状态时才提示
                    Toast.makeText(this@LoginActivity, "连接断开，请检查网络", Toast.LENGTH_SHORT).show()
                }
                previousConnected = connected
            }
        }
    }
    
    private fun setupListeners() {
        binding.buttonLogin.setOnClickListener {
            val username = binding.editTextUsername.text.toString()
            val password = binding.editTextPassword.text.toString()
            
            viewModel.login(username, password)
        }
        
        binding.buttonRegister.setOnClickListener {
            val username = binding.editTextUsername.text.toString()
            val password = binding.editTextPassword.text.toString()
            val nickname = username  // 默认使用用户名作为昵称
            
            viewModel.register(username, password, nickname)
        }
    }
}

