#include "decoder.h"
#include "utils/logger.h"
#include <arpa/inet.h>
#include <cstring>

namespace im {

std::queue<Packet> MessageDecoder::addData(const std::vector<uint8_t>& data) {
    Logger::info("[解码器] addData 被调用: 收到 " + std::to_string(data.size()) + 
                 " 字节，当前缓冲区大小=" + std::to_string(buffer_.size()));
    std::cout.flush();
    buffer_.insert(buffer_.end(), data.begin(), data.end());
    Logger::info("[解码器] 数据已添加到缓冲区，新缓冲区大小=" + std::to_string(buffer_.size()));
    std::cout.flush();
    auto result = decodeMessages();
    Logger::info("[解码器] decodeMessages 返回: " + std::to_string(result.size()) + " 条消息");
    std::cout.flush();
    return result;
}

std::queue<Packet> MessageDecoder::decodeMessages() {
    std::queue<Packet> messages;
    
    if (buffer_.empty()) {
        Logger::debug("解码器: 缓冲区为空，直接返回");
        return messages;
    }
    
    // 记录每次解码的日志
    Logger::info("[解码器] 开始解码，缓冲区大小=" + std::to_string(buffer_.size()));
    
    int magicMismatchCount = 0;
    const int MAX_MAGIC_MISMATCH = 10;  // 最多尝试10次，避免无限循环
    
    int loopCount = 0;
    while (buffer_.size() >= 10) {  // 至少需要 10 字节头部
        loopCount++;
        Logger::info("[解码器] 循环 " + std::to_string(loopCount) + ": 缓冲区大小=" + std::to_string(buffer_.size()));
        
        // 读取头部
        uint32_t magic;
        uint16_t type;
        uint32_t length;
        
        std::memcpy(&magic, buffer_.data(), 4);
        std::memcpy(&type, buffer_.data() + 4, 2);
        std::memcpy(&length, buffer_.data() + 6, 4);
        
        // 转换为主机字节序
        magic = ntohl(magic);
        type = ntohs(type);
        length = ntohl(length);
        
        Logger::info("[解码器] 读取头部: magic=0x" + 
                     std::to_string(magic) + ", type=" + std::to_string(type) + 
                     ", length=" + std::to_string(length));
        
        char magicHex[16];
        snprintf(magicHex, sizeof(magicHex), "0x%08X", magic);
        char expectedHex[16];
        snprintf(expectedHex, sizeof(expectedHex), "0x%08X", MAGIC);
        
        // 心跳包使用debug级别，其他消息使用info级别
        uint16_t msgType = static_cast<uint16_t>(type);
        bool isHeartbeat = (msgType == 7 || msgType == 8);
        if (isHeartbeat) {
            Logger::debug("解析头部: magic=" + std::string(magicHex) + 
                         " (期望" + std::string(expectedHex) + ")" +
                         ", type=" + std::to_string(msgType) +
                         ", length=" + std::to_string(length));
        } else {
            Logger::info("解析头部: magic=" + std::string(magicHex) + 
                         " (期望" + std::string(expectedHex) + ")" +
                         ", type=" + std::to_string(msgType) +
                         ", length=" + std::to_string(length));
        }
        
        // 验证 Magic
        if (magic != MAGIC) {
            magicMismatchCount++;
            if (magicMismatchCount <= MAX_MAGIC_MISMATCH) {
                // Magic 不匹配，输出调试信息，并丢弃第一个字节
                char magicHex[16];
                snprintf(magicHex, sizeof(magicHex), "0x%08X", magic);
                char expectedHex[16];
                snprintf(expectedHex, sizeof(expectedHex), "0x%08X", MAGIC);
                Logger::warn("解码失败: Magic 不匹配 (收到" + std::string(magicHex) + 
                            "，期望" + std::string(expectedHex) + ")" +
                            "，丢弃一个字节，当前缓冲区大小=" +
                            std::to_string(buffer_.size()) +
                            "，已尝试 " + std::to_string(magicMismatchCount) + " 次");
                // 输出前16字节的十六进制，便于调试
                std::string hexDump;
                for (size_t i = 0; i < std::min(buffer_.size(), size_t(16)); ++i) {
                    char buf[4];
                    snprintf(buf, sizeof(buf), "%02X ", static_cast<unsigned char>(buffer_[i]));
                    hexDump += buf;
                }
                Logger::debug("缓冲区前16字节(十六进制): " + hexDump);
            } else if (magicMismatchCount == MAX_MAGIC_MISMATCH + 1) {
                Logger::error("Magic 不匹配次数过多，清空缓冲区");
                buffer_.clear();
                break;
            }
            buffer_.erase(buffer_.begin());
            continue;
        }
        
        // Magic 匹配成功，重置计数器
        magicMismatchCount = 0;
        
        Logger::info("✓ Magic 验证通过，继续解码");
        
        // 检查数据是否完整
        size_t requiredSize = 10 + length;
        if (buffer_.size() < requiredSize) {
            Logger::info("⚠ 数据不完整: 需要 " + std::to_string(requiredSize) +
                         " 字节，当前缓冲区大小=" + std::to_string(buffer_.size()) +
                         "，缺少 " + std::to_string(requiredSize - buffer_.size()) + " 字节，等待更多数据");
            // 输出当前缓冲区的十六进制，便于调试
            std::string hexDump;
            size_t dumpSize = std::min(buffer_.size(), size_t(64));
            for (size_t i = 0; i < dumpSize; ++i) {
                char buf[4];
                snprintf(buf, sizeof(buf), "%02X ", static_cast<unsigned char>(buffer_[i]));
                hexDump += buf;
            }
            Logger::info("当前缓冲区前" + std::to_string(dumpSize) + "字节(十六进制): " + hexDump);
            break;  // 数据不完整，等待更多数据
        }
        
        Logger::info("✓ 数据完整: 需要 " + std::to_string(requiredSize) +
                     " 字节，当前缓冲区大小=" + std::to_string(buffer_.size()) +
                     "，可以解码");
        
        // 读取数据体
        std::string dataStr(buffer_.begin() + 10, buffer_.begin() + 10 + length);
        
        Logger::info("读取数据体: length=" + std::to_string(length) +
                     ", data=" + dataStr.substr(0, 200));  // 显示前200字符
        
        // 构造 Packet
        Packet packet;
        packet.magic = magic;
        packet.type = static_cast<MessageType>(type);
        packet.length = length;
        packet.data = dataStr;
        
        // 心跳包使用debug级别，其他消息使用info级别
        uint16_t msgTypeDecoded = static_cast<uint16_t>(type);
        bool isHeartbeatMsg = (msgTypeDecoded == 7 || msgTypeDecoded == 8);
        if (isHeartbeatMsg) {
            Logger::debug("✓ 成功解码心跳消息: type=" + std::to_string(msgTypeDecoded));
        } else {
            Logger::info("✓ 成功解码消息: type=" + std::to_string(msgTypeDecoded) +
                         ", length=" + std::to_string(length) +
                         ", data=" + packet.data);
        }
        messages.push(packet);
        
        // 移除已处理的数据
        size_t removeSize = 10 + length;
        Logger::info("移除已处理数据: " + std::to_string(removeSize) + " 字节，剩余缓冲区大小=" + 
                     std::to_string(buffer_.size() - removeSize));
        buffer_.erase(buffer_.begin(), buffer_.begin() + removeSize);
    }
    
    return messages;
}

void MessageDecoder::clear() {
    buffer_.clear();
}

}  // namespace im

