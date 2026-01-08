package com.example.androidim.protocol

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 消息解码器
 * 从字节流中解码消息
 */
class MessageDecoder {
    
    private val buffer = mutableListOf<Byte>()
    private val TAG = "MessageDecoder"
    
    /**
     * 添加数据到缓冲区
     * 
     * @param data 接收到的字节数组
     * @return 解码出的消息列表
     */
    fun addData(data: ByteArray): List<DecodedMessage> {
        Log.d(TAG, "[解码] 添加数据到缓冲区: size=${data.size}, 当前缓冲区大小=${buffer.size}")
        buffer.addAll(data.toList())
        return decodeMessages()
    }
    
    /**
     * 解码消息
     * 
     * @return 解码出的消息列表
     */
    private fun decodeMessages(): List<DecodedMessage> {
        val messages = mutableListOf<DecodedMessage>()
        
        while (buffer.size >= 10) {  // 至少需要 10 字节头部
            // 读取头部
            val headerBytes = buffer.take(10).toByteArray()
            val headerBuffer = ByteBuffer.wrap(headerBytes)
            headerBuffer.order(ByteOrder.BIG_ENDIAN)
            
            val magic = headerBuffer.int
            val type = headerBuffer.short
            val length = headerBuffer.int
            
            Log.d(TAG, "[解码] 解析头部: magic=0x${magic.toString(16)}, type=$type (0x${type.toString(16)}), length=$length")
            
            // 验证 Magic
            if (magic != MessageType.MAGIC) {
                // Magic 不匹配，丢弃第一个字节，继续解析
                Log.w(TAG, "[解码] ⚠️ Magic 不匹配: 期望=0x${MessageType.MAGIC.toString(16)}, 实际=0x${magic.toString(16)}, 丢弃第一个字节")
                buffer.removeAt(0)
                continue
            }
            
            // 检查数据是否完整
            if (buffer.size < 10 + length) {
                // 数据不完整，等待更多数据
                Log.d(TAG, "[解码] 数据不完整: 需要=${10 + length}, 当前=${buffer.size}，等待更多数据")
                break
            }
            
            // 读取数据体
            val dataBytes = buffer.subList(10, 10 + length).toByteArray()
            val jsonData = String(dataBytes, Charsets.UTF_8)
            
            Log.d(TAG, "[解码] ✅ 成功解码消息: type=$type (0x${type.toString(16)}), length=$length, data=$jsonData")
            
            // 移除已处理的数据
            repeat(10 + length) {
                buffer.removeAt(0)
            }
            
            messages.add(DecodedMessage(type, jsonData))
        }
        
        return messages
    }
    
    /**
     * 清空缓冲区
     */
    fun clear() {
        Log.d(TAG, "[解码] 清空缓冲区")
        buffer.clear()
    }
}

/**
 * 解码后的消息
 */
data class DecodedMessage(
    val type: Short,
    val jsonData: String
)


