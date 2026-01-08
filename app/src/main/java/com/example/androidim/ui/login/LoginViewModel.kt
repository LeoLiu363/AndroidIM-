package com.example.androidim.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidim.model.LoginResponse
import com.example.androidim.repository.IMRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel : ViewModel() {
    
    private val repository = IMRepository.getInstance()
    
    private val _loginResult = MutableStateFlow<LoginResponse?>(null)
    val loginResult: StateFlow<LoginResponse?> = _loginResult.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    val connectionState = repository.connectionState
    private var lastToastAtMs: Long = 0L
    private var lastToastMsg: String? = null
    
    init {
        // 连接到服务端
        viewModelScope.launch {
            repository.connect()
        }
        
        // 监听登录响应
        viewModelScope.launch {
            repository.loginResponse.collect { response ->
                response?.let {
                    // 使用新的对象实例，确保 StateFlow 能检测到变化（即使内容相同）
                    _loginResult.value = LoginResponse(
                        success = it.success,
                        message = it.message,
                        userId = it.userId,
                        username = it.username
                    )
                    _isLoading.value = false
                }
            }
        }
        
        // 监听注册响应
        viewModelScope.launch {
            repository.registerResponse.collect { response ->
                response?.let {
                    // 将注册响应转换为登录响应格式
                    _loginResult.value = LoginResponse(
                        success = it.success,
                        message = it.message,
                        userId = it.userId,
                        username = null
                    )
                    _isLoading.value = false
                }
            }
        }
    }

    /**
     * 避免短时间重复提示同一条消息（例如 1001 “请先登录”）
     */
    fun shouldShowLoginToast(message: String): String? {
        val now = System.currentTimeMillis()
        val lastMsg = lastToastMsg
        if (lastMsg != null && lastMsg == message && now - lastToastAtMs < 1500) {
            return null
        }
        lastToastMsg = message
        lastToastAtMs = now
        return message
    }
    
    fun login(username: String, password: String) {
        if (username.isEmpty() || password.isEmpty()) {
            _loginResult.value = LoginResponse(
                success = false,
                message = "请输入用户名和密码",
                userId = null,
                username = null
            )
            return
        }
        
        // 重置状态，确保每次登录都能触发更新
        _loginResult.value = null
        _isLoading.value = true
        repository.resetLoginResponse()  // 重置 Repository 的响应状态
        
        viewModelScope.launch {
            repository.login(username, password)
            
            // 设置超时：5秒后如果没有收到响应，恢复按钮状态
            val timeoutJob = launch {
                kotlinx.coroutines.delay(5000)
                if (_isLoading.value) {
                    // 如果5秒后还在加载状态，说明没有收到响应
                    _isLoading.value = false
                    _loginResult.value = LoginResponse(
                        success = false,
                        message = "登录请求超时，请检查网络连接",
                        userId = null,
                        username = null
                    )
                }
            }
            
            // 等待响应或超时
            timeoutJob.join()
        }
    }
    
    fun register(username: String, password: String, nickname: String) {
        if (username.isEmpty() || password.isEmpty()) {
            _loginResult.value = LoginResponse(
                success = false,
                message = "请输入用户名和密码",
                userId = null,
                username = null
            )
            return
        }
        
        // 重置状态，确保每次注册都能触发更新
        _loginResult.value = null
        _isLoading.value = true
        repository.resetLoginResponse()  // 重置 Repository 的响应状态
        
        viewModelScope.launch {
            repository.register(username, password, nickname)
            
            // 设置超时：5秒后如果没有收到响应，恢复按钮状态
            launch {
                kotlinx.coroutines.delay(5000)
                if (_isLoading.value) {
                    // 如果5秒后还在加载状态，说明没有收到响应
                    _isLoading.value = false
                    _loginResult.value = LoginResponse(
                        success = false,
                        message = "注册请求超时，请检查网络连接",
                        userId = null,
                        username = null
                    )
                }
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // 不在这里断开连接，让连接在整个应用生命周期中保持
        // 连接会在应用退出时自动断开
    }
}

