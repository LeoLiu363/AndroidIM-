#include "friend_handler.h"
#include "server/epoll_server.h"
#include "protocol/message.h"
#include "database/database.h"
#include "utils/logger.h"
#include <regex>
#include <sstream>
#include <ctime>
#include <mysql/mysql.h>

namespace im {

// 简单 JSON 字符串转义
static std::string escapeJsonStringFriend(const std::string& str) {
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
                escaped << c;
                break;
        }
    }
    return escaped.str();
}

// SQL 转义辅助（使用 Database 的连接）
static std::string escapeSql(MYSQL* conn, const std::string& value) {
    if (!conn) return value;
    std::string result;
    result.resize(value.size() * 2 + 1);
    unsigned long len = mysql_real_escape_string(conn,
                                                 &result[0],
                                                 value.c_str(),
                                                 value.size());
    result.resize(len);
    return result;
}

void FriendHandler::handleApply(EpollServer& server, int fd, const std::string& jsonData) {
    auto senderInfo = server.getClientInfo(fd);
    if (!senderInfo || !senderInfo->authenticated) {
        server.sendMessage(fd, MessageType::ERROR,
                           R"({"success":false,"error_code":1001,"error_message":"请先登录"})");
        return;
    }

    // 解析 JSON：target_username, greeting
    std::regex targetRegex(R"(\"target_username\"\s*:\s*\"([^\"]+)\")");
    std::regex greetingRegex(R"(\"greeting\"\s*:\s*\"([^\"]*)\")");
    std::smatch targetMatch, greetingMatch;

    std::string targetUsername;
    std::string targetUserId;
    std::string greeting;

    if (std::regex_search(jsonData, targetMatch, targetRegex)) {
        targetUsername = targetMatch[1].str();
    }
    if (std::regex_search(jsonData, greetingMatch, greetingRegex)) {
        greeting = greetingMatch[1].str();
    }

    if (targetUsername.empty()) {
        server.sendMessage(fd, MessageType::FRIEND_APPLY_RESPONSE,
                           R"({"success":false,"error_code":2001,"error_message":"target_username 不能为空"})");
        return;
    }

    Database& db = Database::getInstance();
    if (!db.isConnected()) {
        server.sendMessage(fd, MessageType::FRIEND_APPLY_RESPONSE,
                           R"({"success":false,"error_code":5000,"error_message":"服务器数据库未连接"})");
        return;
    }

    MYSQL* conn = db.getConnection();

    // 通过用户名查找目标用户ID
    {
        std::string escapedUsername = escapeSql(conn, targetUsername);
        std::string query = "SELECT user_id FROM users WHERE username = '" + escapedUsername + "' LIMIT 1";
        if (mysql_query(conn, query.c_str()) != 0) {
            Logger::error("查询目标用户名失败: " + std::string(mysql_error(conn)));
            server.sendMessage(fd, MessageType::FRIEND_APPLY_RESPONSE,
                               R"({"success":false,"error_code":5001,"error_message":"查询目标用户失败"})");
            return;
        }
        MYSQL_RES* res = mysql_store_result(conn);
        if (!res) {
            Logger::error("获取目标用户查询结果失败: " + std::string(mysql_error(conn)));
            server.sendMessage(fd, MessageType::FRIEND_APPLY_RESPONSE,
                               R"({"success":false,"error_code":5001,"error_message":"查询目标用户失败"})");
            return;
        }
        MYSQL_ROW row = mysql_fetch_row(res);
        if (!row) {
            mysql_free_result(res);
            server.sendMessage(fd, MessageType::FRIEND_APPLY_RESPONSE,
                               R"({"success":false,"error_code":2001,"error_message":"目标用户名不存在"})");
            return;
        }
        targetUserId = row[0] ? row[0] : "";
        mysql_free_result(res);
    }

    if (targetUserId == senderInfo->userId) {
        server.sendMessage(fd, MessageType::FRIEND_APPLY_RESPONSE,
                           R"({"success":false,"error_code":2002,"error_message":"不能添加自己为好友"})");
        return;
    }

    // 检查是否已是好友
    {
        std::string escapedUserId = escapeSql(conn, senderInfo->userId);
        std::string escapedTargetId = escapeSql(conn, targetUserId);
        std::string query =
            "SELECT COUNT(*) FROM friends WHERE user_id = " + escapedUserId +
            " AND friend_user_id = " + escapedTargetId;
        if (mysql_query(conn, query.c_str()) != 0) {
            Logger::error("查询好友关系失败: " + std::string(mysql_error(conn)));
        } else {
            MYSQL_RES* res = mysql_store_result(conn);
            if (res) {
                MYSQL_ROW row = mysql_fetch_row(res);
                bool alreadyFriend = row && std::atoi(row[0]) > 0;
                mysql_free_result(res);
                if (alreadyFriend) {
                    server.sendMessage(fd, MessageType::FRIEND_APPLY_RESPONSE,
                                       R"({"success":false,"error_code":2003,"error_message":"已经是好友"})");
                    return;
                }
            }
        }
    }

    // 写入好友申请
    std::string escapedFromId = escapeSql(conn, senderInfo->userId);
    std::string escapedToId = escapeSql(conn, targetUserId);
    std::string escapedGreeting = escapeSql(conn, greeting);

    std::ostringstream insertSql;
    insertSql << "INSERT INTO friend_applies (from_user_id, to_user_id, greeting) VALUES ("
              << escapedFromId << ", " << escapedToId << ", "
              << (greeting.empty() ? "NULL" : ("'" + escapedGreeting + "'"))
              << ")";

    if (mysql_query(conn, insertSql.str().c_str()) != 0) {
        Logger::error("插入好友申请失败: " + std::string(mysql_error(conn)));
        server.sendMessage(fd, MessageType::FRIEND_APPLY_RESPONSE,
                           R"({"success":false,"error_code":5002,"error_message":"发送好友申请失败"})");
        return;
    }

    unsigned long applyId = mysql_insert_id(conn);

    // 返回给申请发起方
    {
        std::ostringstream resp;
        resp << R"({"success":true,"apply_id":")" << applyId
             << R"(","message":"好友申请已发送"})";
        server.sendMessage(fd, MessageType::FRIEND_APPLY_RESPONSE, resp.str());
    }

    // 如果对方在线，推送申请通知
    {
        auto onlineUsers = server.getOnlineUsers();
        bool targetOnline = false;
        for (const auto& uid : onlineUsers) {
            if (uid == targetUserId) {
                targetOnline = true;
                break;
            }
        }
        if (targetOnline) {
            std::ostringstream notify;
            notify << R"({"apply_id":")" << applyId << R"(",)"
                   << R"("from_user":{"user_id":")" << escapeJsonStringFriend(senderInfo->userId)
                   << R"(","username":")" << escapeJsonStringFriend(senderInfo->username)
                   << R"("},)"
                   << R"("greeting":")" << escapeJsonStringFriend(greeting) << R"(",)"
                   << R"("created_at":)" << std::time(nullptr) << "}";
            server.sendMessageToUser(targetUserId, MessageType::FRIEND_APPLY_NOTIFY, notify.str());
        }
    }
}

void FriendHandler::handleApplyAction(EpollServer& server, int fd, const std::string& jsonData) {
    auto handlerInfo = server.getClientInfo(fd);
    if (!handlerInfo || !handlerInfo->authenticated) {
        server.sendMessage(fd, MessageType::ERROR,
                           R"({"success":false,"error_code":1001,"error_message":"请先登录"})");
        return;
    }

    std::regex applyIdRegex(R"(\"apply_id\"\s*:\s*\"?([0-9]+)\"?)");
    std::regex actionRegex(R"(\"action\"\s*:\s*\"([a-zA-Z]+)\")");
    std::smatch applyIdMatch, actionMatch;

    std::string applyIdStr;
    std::string action;

    if (std::regex_search(jsonData, applyIdMatch, applyIdRegex)) {
        applyIdStr = applyIdMatch[1].str();
    }
    if (std::regex_search(jsonData, actionMatch, actionRegex)) {
        action = actionMatch[1].str();
    }

    if (applyIdStr.empty() || action.empty()) {
        server.sendMessage(fd, MessageType::FRIEND_HANDLE_RESPONSE,
                           R"({"success":false,"error_code":2003,"error_message":"参数不完整"})");
        return;
    }

    bool accept = (action == "accept" || action == "ACCEPT");

    Database& db = Database::getInstance();
    if (!db.isConnected()) {
        server.sendMessage(fd, MessageType::FRIEND_HANDLE_RESPONSE,
                           R"({"success":false,"error_code":5000,"error_message":"服务器数据库未连接"})");
        return;
    }
    MYSQL* conn = db.getConnection();

    // 查询申请记录，确认是当前用户的待处理申请
    std::string escapedApplyId = escapeSql(conn, applyIdStr);
    std::string escapedHandlerId = escapeSql(conn, handlerInfo->userId);

    std::string query =
        "SELECT from_user_id, to_user_id, status FROM friend_applies "
        "WHERE apply_id = " + escapedApplyId + " AND to_user_id = " + escapedHandlerId;

    if (mysql_query(conn, query.c_str()) != 0) {
        Logger::error("查询好友申请失败: " + std::string(mysql_error(conn)));
        server.sendMessage(fd, MessageType::FRIEND_HANDLE_RESPONSE,
                           R"({"success":false,"error_code":5003,"error_message":"查询好友申请失败"})");
        return;
    }

    MYSQL_RES* res = mysql_store_result(conn);
    if (!res) {
        Logger::error("获取好友申请结果失败: " + std::string(mysql_error(conn)));
        server.sendMessage(fd, MessageType::FRIEND_HANDLE_RESPONSE,
                           R"({"success":false,"error_code":5003,"error_message":"查询好友申请失败"})");
        return;
    }

    MYSQL_ROW row = mysql_fetch_row(res);
    if (!row) {
        mysql_free_result(res);
        server.sendMessage(fd, MessageType::FRIEND_HANDLE_RESPONSE,
                           R"({"success":false,"error_code":2004,"error_message":"好友申请不存在或无权限处理"})");
        return;
    }

    std::string fromUserId = row[0] ? row[0] : "";
    std::string toUserId = row[1] ? row[1] : "";
    int status = row[2] ? std::atoi(row[2]) : 0;
    mysql_free_result(res);

    if (status != 0) {
        server.sendMessage(fd, MessageType::FRIEND_HANDLE_RESPONSE,
                           R"({"success":false,"error_code":2005,"error_message":"该申请已处理"})");
        return;
    }

    // 更新申请状态
    int newStatus = accept ? 1 : 2;
    std::ostringstream updateSql;
    updateSql << "UPDATE friend_applies SET status = " << newStatus
              << ", handled_at = NOW() WHERE apply_id = " << escapedApplyId;

    if (mysql_query(conn, updateSql.str().c_str()) != 0) {
        Logger::error("更新好友申请状态失败: " + std::string(mysql_error(conn)));
        server.sendMessage(fd, MessageType::FRIEND_HANDLE_RESPONSE,
                           R"({"success":false,"error_code":5004,"error_message":"更新好友申请失败"})");
        return;
    }

    // 如果同意，写入双向好友关系
    if (accept) {
        std::string escapedFromId = escapeSql(conn, fromUserId);
        std::string escapedToId = escapeSql(conn, toUserId);

        std::string insertFriend1 =
            "INSERT IGNORE INTO friends (user_id, friend_user_id) VALUES (" +
            escapedFromId + ", " + escapedToId + ")";
        std::string insertFriend2 =
            "INSERT IGNORE INTO friends (user_id, friend_user_id) VALUES (" +
            escapedToId + ", " + escapedFromId + ")";

        if (mysql_query(conn, insertFriend1.c_str()) != 0) {
            Logger::error("插入好友关系失败(1): " + std::string(mysql_error(conn)));
        }
        if (mysql_query(conn, insertFriend2.c_str()) != 0) {
            Logger::error("插入好友关系失败(2): " + std::string(mysql_error(conn)));
        }
    }

    // 给处理方响应
    {
        std::ostringstream resp;
        resp << R"({"success":true,"action":")" << (accept ? "accept" : "reject") << R"("})";
        server.sendMessage(fd, MessageType::FRIEND_HANDLE_RESPONSE, resp.str());
    }

    // 通知申请发起方
    {
        std::ostringstream notify;
        notify << R"({"apply_id":")" << applyIdStr
               << R"(","result":")" << (accept ? "accept" : "reject") << R"("})";
        server.sendMessageToUser(fromUserId, MessageType::FRIEND_HANDLE_NOTIFY, notify.str());
    }
}

void FriendHandler::handleFriendList(EpollServer& server, int fd, const std::string& /*jsonData*/) {
    auto userInfo = server.getClientInfo(fd);
    if (!userInfo || !userInfo->authenticated) {
        server.sendMessage(fd, MessageType::ERROR,
                           R"({"success":false,"error_code":1001,"error_message":"请先登录"})");
        return;
    }

    Database& db = Database::getInstance();
    if (!db.isConnected()) {
        server.sendMessage(fd, MessageType::FRIEND_LIST_RESPONSE,
                           R"({"success":false,"error_code":5000,"error_message":"服务器数据库未连接"})");
        return;
    }
    MYSQL* conn = db.getConnection();

    std::string escapedUserId = escapeSql(conn, userInfo->userId);

    std::string query =
        "SELECT f.friend_user_id, f.remark, f.group_name, f.is_blocked, "
        "u.username, u.nickname "
        "FROM friends f "
        "JOIN users u ON f.friend_user_id = u.user_id "
        "WHERE f.user_id = " + escapedUserId;

    if (mysql_query(conn, query.c_str()) != 0) {
        Logger::error("查询好友列表失败: " + std::string(mysql_error(conn)));
        server.sendMessage(fd, MessageType::FRIEND_LIST_RESPONSE,
                           R"({"success":false,"error_code":5005,"error_message":"查询好友列表失败"})");
        return;
    }

    MYSQL_RES* res = mysql_store_result(conn);
    if (!res) {
        Logger::error("获取好友列表结果失败: " + std::string(mysql_error(conn)));
        server.sendMessage(fd, MessageType::FRIEND_LIST_RESPONSE,
                           R"({"success":false,"error_code":5005,"error_message":"查询好友列表失败"})");
        return;
    }

    auto onlineUsers = server.getOnlineUsers();

    std::ostringstream resp;
    resp << R"({"success":true,"friends":[)";

    bool first = true;
    MYSQL_ROW row;
    while ((row = mysql_fetch_row(res)) != nullptr) {
        std::string friendUserId = row[0] ? row[0] : "";
        std::string remark = row[1] ? row[1] : "";
        std::string groupName = row[2] ? row[2] : "";
        bool isBlocked = row[3] && std::atoi(row[3]) != 0;
        std::string username = row[4] ? row[4] : "";
        std::string nickname = row[5] ? row[5] : "";

        bool online = false;
        for (const auto& uid : onlineUsers) {
            if (uid == friendUserId) {
                online = true;
                break;
            }
        }

        if (!first) {
            resp << ",";
        }
        first = false;

        resp << R"({"user_id":")" << escapeJsonStringFriend(friendUserId)
             << R"(","username":")" << escapeJsonStringFriend(username)
             << R"(","nickname":")" << escapeJsonStringFriend(nickname.empty() ? username : nickname)
             << R"(","remark":")" << escapeJsonStringFriend(remark)
             << R"(","group_name":")" << escapeJsonStringFriend(groupName)
             << R"(","is_blocked":)" << (isBlocked ? "true" : "false")
             << R"(,"online":)" << (online ? "true" : "false")
             << "}";
    }
    mysql_free_result(res);

    resp << "]}";

    server.sendMessage(fd, MessageType::FRIEND_LIST_RESPONSE, resp.str());
}

void FriendHandler::handleDelete(EpollServer& server, int fd, const std::string& jsonData) {
    auto userInfo = server.getClientInfo(fd);
    if (!userInfo || !userInfo->authenticated) {
        server.sendMessage(fd, MessageType::ERROR,
                           R"({"success":false,"error_code":1001,"error_message":"请先登录"})");
        return;
    }

    std::regex friendIdRegex(R"(\"friend_user_id\"\s*:\s*\"?([0-9]+)\"?)");
    std::smatch match;
    std::string friendUserId;
    if (std::regex_search(jsonData, match, friendIdRegex)) {
        friendUserId = match[1].str();
    }
    if (friendUserId.empty()) {
        server.sendMessage(fd, MessageType::FRIEND_DELETE_RESPONSE,
                           R"({"success":false,"error_code":2006,"error_message":"friend_user_id 不能为空"})");
        return;
    }

    Database& db = Database::getInstance();
    if (!db.isConnected()) {
        server.sendMessage(fd, MessageType::FRIEND_DELETE_RESPONSE,
                           R"({"success":false,"error_code":5000,"error_message":"服务器数据库未连接"})");
        return;
    }
    MYSQL* conn = db.getConnection();

    std::string escapedUserId = escapeSql(conn, userInfo->userId);
    std::string escapedFriendId = escapeSql(conn, friendUserId);

    std::string sql1 =
        "DELETE FROM friends WHERE user_id = " + escapedUserId +
        " AND friend_user_id = " + escapedFriendId;
    std::string sql2 =
        "DELETE FROM friends WHERE user_id = " + escapedFriendId +
        " AND friend_user_id = " + escapedUserId;

    bool ok = true;
    if (mysql_query(conn, sql1.c_str()) != 0) {
        Logger::error("删除好友关系失败(1): " + std::string(mysql_error(conn)));
        ok = false;
    }
    if (mysql_query(conn, sql2.c_str()) != 0) {
        Logger::error("删除好友关系失败(2): " + std::string(mysql_error(conn)));
        ok = false;
    }

    if (!ok) {
        server.sendMessage(fd, MessageType::FRIEND_DELETE_RESPONSE,
                           R"({"success":false,"error_code":5006,"error_message":"删除好友失败"})");
    } else {
        server.sendMessage(fd, MessageType::FRIEND_DELETE_RESPONSE,
                           R"({"success":true,"message":"已删除好友"})");
    }
}

void FriendHandler::handleBlock(EpollServer& server, int fd, const std::string& jsonData) {
    auto userInfo = server.getClientInfo(fd);
    if (!userInfo || !userInfo->authenticated) {
        server.sendMessage(fd, MessageType::ERROR,
                           R"({"success":false,"error_code":1001,"error_message":"请先登录"})");
        return;
    }

    std::regex targetIdRegex(R"(\"target_user_id\"\s*:\s*\"?([0-9]+)\"?)");
    std::regex blockRegex(R"(\"block\"\s*:\s*(true|false))");
    std::smatch targetMatch, blockMatch;

    std::string targetUserId;
    bool block = false;

    if (std::regex_search(jsonData, targetMatch, targetIdRegex)) {
        targetUserId = targetMatch[1].str();
    }
    if (std::regex_search(jsonData, blockMatch, blockRegex)) {
        block = (blockMatch[1].str() == "true");
    }

    if (targetUserId.empty()) {
        server.sendMessage(fd, MessageType::FRIEND_BLOCK_RESPONSE,
                           R"({"success":false,"error_code":2007,"error_message":"target_user_id 不能为空"})");
        return;
    }

    Database& db = Database::getInstance();
    if (!db.isConnected()) {
        server.sendMessage(fd, MessageType::FRIEND_BLOCK_RESPONSE,
                           R"({"success":false,"error_code":5000,"error_message":"服务器数据库未连接"})");
        return;
    }
    MYSQL* conn = db.getConnection();

    std::string escapedUserId = escapeSql(conn, userInfo->userId);
    std::string escapedTargetId = escapeSql(conn, targetUserId);

    std::ostringstream sql;
    sql << "UPDATE friends SET is_blocked = " << (block ? 1 : 0)
        << " WHERE user_id = " << escapedUserId
        << " AND friend_user_id = " << escapedTargetId;

    if (mysql_query(conn, sql.str().c_str()) != 0) {
        Logger::error("更新拉黑状态失败: " + std::string(mysql_error(conn)));
        server.sendMessage(fd, MessageType::FRIEND_BLOCK_RESPONSE,
                           R"({"success":false,"error_code":5007,"error_message":"更新拉黑状态失败"})");
        return;
    }

    std::ostringstream resp;
    resp << R"({"success":true,"block":)" << (block ? "true" : "false") << "}";
    server.sendMessage(fd, MessageType::FRIEND_BLOCK_RESPONSE, resp.str());
}

}  // namespace im


