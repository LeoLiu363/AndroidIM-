package com.example.androidim.network

import android.util.Log
import com.example.androidim.protocol.MessageDecoder
import com.example.androidim.protocol.MessageEncoder
import com.example.androidim.protocol.MessageType
import kotlinx.coroutines.*
import java.io.IOException
import java.net.Socket

/**
 * IM 客户端
 * 负责与服务端建立连接、发送和接收消息
 */
class IMClient(
    private val serverHost: String = "YOUR_SERVER_IP",  // 服务端 IP
    private val serverPort: Int = 8889                  // 服务端端口
) {
    
    private var socket: Socket? = null
    private var sendJob: Job? = null
    private var receiveJob: Job? = null
    private val decoder = MessageDecoder()
    private val stateLock = Any()
    @Volatile private var connecting: Boolean = false
    
    // 消息回调
    var onMessageReceived: ((Short, String) -> Unit)? = null
    var onConnectionChanged: ((Boolean) -> Unit)? = null
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * 连接到服务端
     */
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        // 防止重复 connect 导致心跳/接收协程叠加
        synchronized(stateLock) {
            val s = socket
            if (s != null && s.isConnected && !s.isClosed) {
                Log.d(TAG, "[连接] 已处于连接状态，跳过重复 connect()")
                return@withContext true
            }
            if (connecting) {
                Log.w(TAG, "[连接] connect() 正在进行中，跳过本次调用")
                return@withContext false
            }
            connecting = true
            // 清理旧协程与旧 socket（如果存在），避免残留心跳
            internalDisconnectLocked(notify = false, reason = "connect() 前清理旧连接")
        }

        try {
            Log.d(TAG, "[连接] 开始建立连接: $serverHost:$serverPort")
            socket = Socket(serverHost, serverPort)
            
            // 设置 Socket 选项
            socket?.apply {
                // 设置读取超时（30秒）
                soTimeout = 30000
                // 启用 TCP_NODELAY（禁用 Nagle 算法，立即发送数据）
                tcpNoDelay = true
                // 设置接收缓冲区大小
                receiveBufferSize = 8192
                // 设置发送缓冲区大小
                sendBufferSize = 8192
                // 保持连接
                keepAlive = true
            }
            
            Log.d(TAG, "[连接] Socket 创建成功，isConnected=${socket?.isConnected}")
            Log.d(TAG, "[连接] Socket 配置: soTimeout=${socket?.soTimeout}, tcpNoDelay=${socket?.tcpNoDelay}, keepAlive=${socket?.keepAlive}")
            
            onConnectionChanged?.invoke(true)
            
            // 启动接收消息的协程
            startReceive()
            Log.d(TAG, "[连接] 已启动接收消息协程")
            
            // 启动心跳
            startHeartbeat()
            Log.d(TAG, "[连接] 已启动心跳协程")
            
            Log.d(TAG, "[连接] ✅ 连接成功: $serverHost:$serverPort")
            true
        } catch (e: Exception) {
            Log.e(TAG, "[连接] ❌ 连接失败: ${e.message}", e)
            onConnectionChanged?.invoke(false)
            false
        } finally {
            synchronized(stateLock) {
                connecting = false
            }
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        synchronized(stateLock) {
            internalDisconnectLocked(notify = true, reason = "disconnect()")
        }
    }

    private fun internalDisconnectLocked(notify: Boolean, reason: String) {
        // 轻量幂等：已经完全断开则直接返回
        if (socket == null && sendJob == null && receiveJob == null) {
            return
        }
        try {
            Log.d(TAG, "[断开] 🔌 开始断开连接（$reason）")
            // 先取消协程，避免 while(isActive) 继续跑
            sendJob?.cancel()
            receiveJob?.cancel()
            sendJob = null
            receiveJob = null

            socket?.runCatching { close() }
            socket = null
            decoder.clear()
            if (notify) onConnectionChanged?.invoke(false)
            Log.d(TAG, "[断开] ✅ 已断开连接（$reason）")
        } catch (e: Exception) {
            Log.e(TAG, "[断开] ❌ 断开连接失败（$reason）: ${e.message}", e)
        }
    }
    
    /**
     * 发送消息
     * 
     * @param type 消息类型
     * @param jsonData JSON 数据
     */
    suspend fun sendMessage(type: Short, jsonData: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val socket = this@IMClient.socket
            if (socket == null) {
                Log.w(TAG, "[发送] ⚠️ Socket 为 null，无法发送消息 type=$type")
                return@withContext false
            }
            if (!socket.isConnected) {
                Log.w(TAG, "[发送] ⚠️ Socket 未连接，无法发送消息 type=$type")
                return@withContext false
            }
            
            val output = socket.getOutputStream()
            val packet = MessageEncoder.encode(type, jsonData)
            
            Log.d(TAG, "[发送] 📤 发送消息: type=$type (0x${type.toString(16)}), length=${packet.size}, data=$jsonData")
            
            output.write(packet)
            output.flush()
            
            Log.d(TAG, "[发送] ✅ 消息发送成功: type=$type")
            true
        } catch (e: java.net.SocketException) {
            val errorMsg = e.message ?: "Unknown"
            if (errorMsg.contains("Broken pipe") || errorMsg.contains("Connection reset")) {
                Log.w(TAG, "[发送] ⚠️ 连接已断开，无法发送消息 type=$type: $errorMsg")
            } else {
                Log.e(TAG, "[发送] ❌ Socket 异常: type=$type, error=$errorMsg", e)
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "[发送] ❌ 发送消息失败: type=$type, error=${e.message}", e)
            false
        }
    }
    
    /**
     * 启动接收消息
     */
    private fun startReceive() {
        // 避免重复启动接收协程
        receiveJob?.cancel()
        receiveJob = scope.launch {
            val socket = this@IMClient.socket
            if (socket == null) {
                Log.e(TAG, "[接收] ❌ Socket 为 null，无法启动接收")
                return@launch
            }
            
            val input = socket.getInputStream()
            val buffer = ByteArray(4096)
            
            Log.d(TAG, "[接收] 📥 开始接收消息循环")
            Log.d(TAG, "[接收] Socket 状态: isConnected=${socket.isConnected}, isClosed=${socket.isClosed}, isInputShutdown=${socket.isInputShutdown}")
            
            try {
                while (isActive) {
                    // 检查 Socket 状态
                    if (!socket.isConnected || socket.isClosed) {
                        Log.w(TAG, "[接收] ⚠️ Socket 已断开: isConnected=${socket.isConnected}, isClosed=${socket.isClosed}")
                        break
                    }
                    
                    // 检查是否有可用数据（仅用于日志，不影响读取逻辑）
                    val available = try {
                        input.available()
                    } catch (e: Exception) {
                        Log.e(TAG, "[接收] 检查可用数据失败: ${e.message}")
                        -1
                    }
                    
                    Log.d(TAG, "[接收] 调用 read() 等待数据... (当前可用字节数: $available)")
                    
                    // 使用协程超时包装阻塞的 read() 调用
                    // 注意：withTimeout 不能直接包装阻塞调用，需要使用 withContext(Dispatchers.IO)
                    val bytesRead = try {
                        withTimeout(30000) {  // 30 秒超时
                            // 在 IO 线程池中执行阻塞的 read()
                            withContext(Dispatchers.IO) {
                                input.read(buffer)
                            }
                        }
                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                        Log.w(TAG, "[接收] ⚠️ 读取超时（30秒无数据，协程超时）")
                        // 检查 Socket 是否仍然连接
                        if (!socket.isConnected || socket.isClosed) {
                            Log.w(TAG, "[接收] Socket 已断开，退出接收循环")
                            break
                        }
                        // 连接仍然活跃，继续等待
                        Log.d(TAG, "[接收] 连接仍然活跃，继续等待数据...")
                        continue
                    } catch (e: java.net.SocketTimeoutException) {
                        Log.w(TAG, "[接收] ⚠️ 读取超时（30秒无数据，Socket 超时）")
                        // 检查 Socket 是否仍然连接
                        if (!socket.isConnected || socket.isClosed) {
                            Log.w(TAG, "[接收] Socket 已断开，退出接收循环")
                            break
                        }
                        // 连接仍然活跃，继续等待
                        Log.d(TAG, "[接收] 连接仍然活跃，继续等待数据...")
                        continue
                    } catch (e: java.net.SocketException) {
                        // Socket 异常：连接被关闭（正常或异常）
                        val errorMsg = e.message ?: "Unknown"
                        if (errorMsg.contains("Software caused connection abort") || 
                            errorMsg.contains("Connection reset") ||
                            errorMsg.contains("Broken pipe") ||
                            errorMsg.contains("Socket closed")) {
                            Log.d(TAG, "[接收] ℹ️ 连接已关闭: $errorMsg（正常断开）")
                        } else {
                            Log.e(TAG, "[接收] ❌ Socket 异常: $errorMsg", e)
                        }
                        break
                    } catch (e: IOException) {
                        Log.e(TAG, "[接收] ❌ 读取异常: ${e.message}", e)
                        break
                    } catch (e: Exception) {
                        Log.e(TAG, "[接收] ❌ 未知异常: ${e.message}", e)
                        break
                    }
                    
                    if (bytesRead == -1) {
                        Log.w(TAG, "[接收] ⚠️ read() 返回 -1，连接已关闭（对端主动关闭）")
                        break
                    }
                    
                    if (bytesRead == 0) {
                        Log.d(TAG, "[接收] read() 返回 0，继续等待")
                        continue
                    }
                    
                    Log.d(TAG, "[接收] 📥 收到数据: bytes=$bytesRead")
                    
                    // 打印接收到的原始字节（用于调试）
                    val receivedBytes = buffer.copyOf(bytesRead)
                    val hexString = receivedBytes.joinToString(" ") { "%02X".format(it) }
                    Log.d(TAG, "[接收] 原始字节数据（前64字节）: ${hexString.take(64)}")
                    
                    // 解码消息
                    val messages = decoder.addData(receivedBytes)
                    Log.d(TAG, "[接收] 解码出 ${messages.size} 条消息")
                    
                    messages.forEach { message ->
                        Log.d(TAG, "[接收] 📨 收到消息: type=${message.type} (0x${message.type.toString(16)}), data=${message.jsonData}")
                        onMessageReceived?.invoke(message.type, message.jsonData)
                    }
                }
                
                Log.d(TAG, "[接收] 接收循环退出: isActive=$isActive, isConnected=${socket.isConnected}, isClosed=${socket.isClosed}")
            } catch (e: IOException) {
                Log.e(TAG, "[接收] ❌ 读取失败: ${e.message}", e)
                e.printStackTrace()
            } catch (e: Exception) {
                Log.e(TAG, "[接收] ❌ 未知异常: ${e.message}", e)
                e.printStackTrace()
            } finally {
                Log.d(TAG, "[接收] 🔄 进入 finally，准备断开连接")
                disconnect()
            }
        }
    }
    
    /**
     * 启动心跳
     */
    private fun startHeartbeat() {
        // 避免重复启动心跳协程
        sendJob?.cancel()
        sendJob = scope.launch {
            Log.d(TAG, "[心跳] 💓 心跳协程启动")
            // 立即发送第一个心跳
            delay(1000)  // 等待1秒后发送第一个心跳
            val jsonData = """{"timestamp":${System.currentTimeMillis() / 1000}}"""
            Log.d(TAG, "[心跳] 💓 发送第一个心跳包")
            sendMessage(MessageType.HEARTBEAT, jsonData)
            
            while (isActive) {
                delay(30000)  // 每 30 秒发送一次心跳
                
                val jsonData = """{"timestamp":${System.currentTimeMillis() / 1000}}"""
                Log.d(TAG, "[心跳] 💓 发送心跳包")
                sendMessage(MessageType.HEARTBEAT, jsonData)
            }
            Log.d(TAG, "[心跳] 💓 心跳协程退出")
        }
    }
    
    /**
     * 检查连接状态
     */
    fun isConnected(): Boolean {
        return socket?.isConnected == true
    }
    
    companion object {
        private const val TAG = "IMClient"
    }
}

