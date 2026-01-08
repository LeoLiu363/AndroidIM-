#include "message_handler.h"
#include "server/epoll_server.h"
#include "protocol/message.h"
#include "database/database.h"
#include "utils/logger.h"
#include <regex>
#include <sstream>
#include <ctime>
#include <mysql/mysql.h>
#include <vector>

namespace im {

// JSON 转义函数：转义特殊字符
static std::string escapeJsonString(const std::string& str) {
    std::ostringstream escaped;
    for (char c : str) {
        switch (c) {
            case '"':  escaped << "\\\""; break;
            case '\\': escaped << "\\\\"; break;
            case '\b': escaped << "\\b"; break;
            case '\f': escaped << "\\f"; break;
            case '\n': escaped << "\\n"; break;
            case '\r': escaped << "\\r"; break;
            case '\t': escaped << "\\t"; break;
            default:
                // 控制字符（ASCII < 32）转义为 \uXXXX
                if (static_cast<unsigned char>(c) < 32) {
                    char buf[7];
                    snprintf(buf, sizeof(buf), "\\u%04X", static_cast<unsigned char>(c));
                    escaped << buf;
                } else {
                    escaped << c;
                }
                break;
        }
    }
    return escaped.str();
}

// SQL 转义辅助函数
static std::string escapeSql(MYSQL* conn, const std::string& value) {
    if (!conn) return value;
    std::string result;
    result.resize(value.size() * 2 + 1);
    unsigned long len = mysql_real_escape_string(conn, &result[0], value.c_str(), value.size());
    result.resize(len);
    return result;
}

void MessageHandler::handle(EpollServer& server, int fd, const std::string& jsonData) {
    // 解析消息
    std::regex toUserIdRegex(R"(\"to_user_id\"\s*:\s*\"([^\"]+)\")");
    std::regex contentRegex(R"(\"content\"\s*:\s*\"([^\"]+)\")");
    std::regex messageTypeRegex(R"(\"message_type\"\s*:\s*\"([^\"]+)\")");
    std::regex conversationTypeRegex(R"(\"conversation_type\"\s*:\s*\"([^\"]+)\")");
    std::regex groupIdRegex(R"(\"group_id\"\s*:\s*\"([^\"]+)\")");
    
    std::smatch toUserIdMatch, contentMatch, messageTypeMatch, convTypeMatch, groupIdMatch;
    std::string toUserId, content, messageType, conversationType, groupId;
    
    if (std::regex_search(jsonData, toUserIdMatch, toUserIdRegex)) {
        toUserId = toUserIdMatch[1].str();
    }
    if (std::regex_search(jsonData, contentMatch, contentRegex)) {
        content = contentMatch[1].str();
    }
    if (std::regex_search(jsonData, messageTypeMatch, messageTypeRegex)) {
        messageType = messageTypeMatch[1].str();
    }
    if (std::regex_search(jsonData, convTypeMatch, conversationTypeRegex)) {
        conversationType = convTypeMatch[1].str();
    }
    if (std::regex_search(jsonData, groupIdMatch, groupIdRegex)) {
        groupId = groupIdMatch[1].str();
    }
    
    if (content.empty()) {
        server.sendMessage(fd, MessageType::ERROR, 
                         R"({"error_code":1002,"error_message":"消息内容不能为空"})");
        return;
    }
    
    // 获取发送者信息
    auto senderInfo = server.getClientInfo(fd);
    if (!senderInfo) {
        server.sendMessage(fd, MessageType::ERROR, 
                         R"({"error_code":1001,"error_message":"请先登录"})");
        return;
    }

    bool isGroupConversation = (conversationType == "group");
    if (isGroupConversation && groupId.empty()) {
        server.sendMessage(fd, MessageType::ERROR,
                         R"({"error_code":3002,"error_message":"group_id 不能为空"})");
        return;
    }
    
    // 构造接收消息（转义特殊字符）
    std::ostringstream response;
    response << R"({"conversation_type":")" << (isGroupConversation ? "group" : "single") << R"(")"
             << R"(,"from_user_id":")" << escapeJsonString(senderInfo->userId)
             << R"(","from_username":")" << escapeJsonString(senderInfo->username)
             << R"(","content":")" << escapeJsonString(content)
             << R"(","message_type":")" << escapeJsonString(messageType.empty() ? "text" : messageType)
             << R"(","timestamp":)" << time(nullptr);

    if (isGroupConversation) {
        response << R"(,"group_id":")" << escapeJsonString(groupId) << R"(")";
    } else if (!toUserId.empty() && toUserId != "all") {
        response << R"(,"to_user_id":")" << escapeJsonString(toUserId) << R"(")";
    }

    response << "}";
    
    // 转发消息
    if (isGroupConversation) {
        // 群聊消息：检查群成员并向群成员广播
        Database& db = Database::getInstance();
        if (!db.isConnected()) {
            server.sendMessage(fd, MessageType::ERROR,
                             R"({"error_code":5000,"error_message":"服务器数据库未连接"})");
            return;
        }
        MYSQL* conn = db.getConnection();

        // 检查发送者是否是该群成员
        std::string escapedGroupId = escapeSql(conn, groupId);
        std::string escapedSenderId = escapeSql(conn, senderInfo->userId);
        std::string checkMemberSql =
            "SELECT COUNT(*) FROM group_members WHERE group_id = " + escapedGroupId +
            " AND user_id = " + escapedSenderId;
        if (mysql_query(conn, checkMemberSql.c_str()) != 0) {
            Logger::error("[群聊消息] 查询成员失败: " + std::string(mysql_error(conn)));
            server.sendMessage(fd, MessageType::ERROR,
                             R"({"error_code":5001,"error_message":"查询群成员失败"})");
            return;
        }
        MYSQL_RES* res = mysql_store_result(conn);
        if (!res) {
            Logger::error("[群聊消息] 获取查询结果失败: " + std::string(mysql_error(conn)));
            server.sendMessage(fd, MessageType::ERROR,
                             R"({"error_code":5001,"error_message":"查询群成员失败"})");
            return;
        }
        MYSQL_ROW row = mysql_fetch_row(res);
        bool isMember = row && std::atoi(row[0]) > 0;
        mysql_free_result(res);

        if (!isMember) {
            server.sendMessage(fd, MessageType::ERROR,
                             R"({"error_code":3100,"error_message":"您不是该群成员，无法发送群消息"})");
            return;
        }

        // 查询群内所有成员
        std::string membersSql =
            "SELECT user_id FROM group_members WHERE group_id = " + escapedGroupId;
        if (mysql_query(conn, membersSql.c_str()) != 0) {
            Logger::error("[群聊消息] 查询群成员列表失败: " + std::string(mysql_error(conn)));
            server.sendMessage(fd, MessageType::ERROR,
                             R"({"error_code":5002,"error_message":"查询群成员列表失败"})");
            return;
        }
        res = mysql_store_result(conn);
        if (!res) {
            Logger::error("[群聊消息] 获取群成员列表结果失败: " + std::string(mysql_error(conn)));
            server.sendMessage(fd, MessageType::ERROR,
                             R"({"error_code":5002,"error_message":"查询群成员列表失败"})");
            return;
        }

        std::vector<std::string> memberIds;
        while ((row = mysql_fetch_row(res)) != nullptr) {
            if (row[0]) {
                memberIds.emplace_back(row[0]);
            }
        }
        mysql_free_result(res);

        // 给所有成员发送（包括发送者自己，客户端可按需要过滤）
        std::string respStr = response.str();
        for (const auto& uid : memberIds) {
            server.sendMessageToUser(uid, MessageType::RECEIVE_MESSAGE, respStr);
        }
        Logger::info("[群聊消息] 转发群聊消息: group_id=" + groupId +
                     ", from=" + senderInfo->username +
                     ", member_count=" + std::to_string(memberIds.size()));
    } else {
        // 单聊 / 广播：保持兼容旧逻辑
    if (toUserId == "all") {
        // 群发
        server.broadcastMessage(MessageType::RECEIVE_MESSAGE, response.str(), fd);
        Logger::info("[消息转发] 群发消息: " + senderInfo->username + " -> all");
    } else if (toUserId.empty()) {
        // to_user_id 为空
        server.sendMessage(fd, MessageType::ERROR, 
                         R"({"error_code":1003,"error_message":"目标用户ID不能为空"})");
        Logger::warn("[消息转发] ✗ 目标用户ID为空: sender=" + senderInfo->username);
    } else {
        // 单发
        bool userFound = false;
        {
            // 先检查目标用户是否在线
            auto onlineUsers = server.getOnlineUsers();
            for (const auto& uid : onlineUsers) {
                if (uid == toUserId) {
                    userFound = true;
                    break;
                }
            }
        }
        
        if (userFound) {
            server.sendMessageToUser(toUserId, MessageType::RECEIVE_MESSAGE, response.str());
            Logger::info("[消息转发] 私聊消息: " + senderInfo->username + " -> " + toUserId);
        } else {
            // 用户不在线，给发送者返回错误
            server.sendMessage(fd, MessageType::ERROR, 
                             R"({"error_code":1004,"error_message":"目标用户不在线","to_user_id":")" + toUserId + "\"}");
            Logger::warn("[消息转发] ✗ 目标用户不在线: sender=" + senderInfo->username + ", target=" + toUserId);
            }
        }
    }
}

}  // namespace im

