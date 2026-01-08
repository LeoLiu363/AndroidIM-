package com.example.androidim.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 消息编码器
 * 将消息类型和 JSON 数据编码为协议数据包
 */
object MessageEncoder {
    
    /**
     * 编码消息
     * 
     * @param type 消息类型
     * @param jsonData JSON 字符串
     * @return 编码后的字节数组
     */
    fun encode(type: Short, jsonData: String): ByteArray {
        val jsonBytes = jsonData.toByteArray(Charsets.UTF_8)
        val length = jsonBytes.size
        
        // 构造数据包：Magic(4) + Type(2) + Length(4) + Data(N)
        val buffer = ByteBuffer.allocate(10 + length)
        buffer.order(ByteOrder.BIG_ENDIAN)  // 网络字节序（大端序）
        
        // Magic: 0x494D494D
        buffer.putInt(MessageType.MAGIC)
        
        // Type: 2 字节
        buffer.putShort(type)
        
        // Length: 4 字节
        buffer.putInt(length)
        
        // Data: JSON 字节数组
        buffer.put(jsonBytes)
        
        return buffer.array()
    }
}







