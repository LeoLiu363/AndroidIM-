#include "group_handler.h"
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

// JSON 字符串转义
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
                escaped << c;
                break;
        }
    }
    return escaped.str();
}

// SQL 转义辅助
static std::string escapeSql(MYSQL* conn, const std::string& value) {
    if (!conn) return value;
    std::string result;
    result.resize(value.size() * 2 + 1);
    unsigned long len = mysql_real_escape_string(conn, &result[0], value.c_str(), value.size());
    result.resize(len);
    return result;
}

// 获取群成员列表（内部辅助函数）
static std::vector<std::string> getGroupMemberIds(MYSQL* conn, const std::string& groupId) {
    std::vector<std::string> memberIds;
    std::string escapedGroupId = escapeSql(conn, groupId);
    std::string query = "SELECT user_id FROM group_members WHERE group_id = " + escapedGroupId;
    
    if (mysql_query(conn, query.c_str()) == 0) {
        MYSQL_RES* res = mysql_store_result(conn);
        if (res) {
            MYSQL_ROW row;
            while ((row = mysql_fetch_row(res)) != nullptr) {
                if (row[0]) {
                    memberIds.push_back(row[0]);
                }
            }
            mysql_free_result(res);
        }
    }
    return memberIds;
}

// 检查用户是否为群成员
static bool isGroupMember(MYSQL* conn, const std::string& groupId, const std::string& userId) {
    std::string escapedGroupId = escapeSql(conn, groupId);
    std::string escapedUserId = escapeSql(conn, userId);
    std::string query = "SELECT COUNT(*) FROM group_members WHERE group_id = " + escapedGroupId + " AND user_id = " + escapedUserId;
    
    if (mysql_query(conn, query.c_str()) != 0) return false;
    MYSQL_RES* res = mysql_store_result(conn);
    if (!res) return false;
    MYSQL_ROW row = mysql_fetch_row(res);
    bool result = row && std::atoi(row[0]) > 0;
    mysql_free_result(res);
    return result;
}

// 获取用户在群中的角色
static std::string getMemberRole(MYSQL* conn, const std::string& groupId, const std::string& userId) {
    std::string escapedGroupId = escapeSql(conn, groupId);
    std::string escapedUserId = escapeSql(conn, userId);
    std::string query = "SELECT role FROM group_members WHERE group_id = " + escapedGroupId + " AND user_id = " + escapedUserId;
    
    if (mysql_query(conn, query.c_str()) != 0) return "";
    MYSQL_RES* res = mysql_store_result(conn);
    if (!res) return "";
    MYSQL_ROW row = mysql_fetch_row(res);
    std::string role = row && row[0] ? row[0] : "";
    mysql_free_result(res);
    return role;
}

void GroupHandler::handleCreate(EpollServer& server, int fd, const std::string& jsonData) {
    auto creatorInfo = server.getClientInfo(fd);
    if (!creatorInfo || !creatorInfo->authenticated) {
        server.sendMessage(fd, MessageType::ERROR,
                           R"({"success":false,"error_code":1001,"error_message":"请先登录"})");
        return;
    }

    // 解析 JSON：group_name, avatar_url, member_user_ids
    std::regex nameRegex(R"(\"group_name\"\s*:\s*\"([^\"]+)\")");
    std::regex avatarRegex(R"(\"avatar_url\"\s*:\s*\"([^\"]*)\")");
    std::regex membersRegex(R"(\"member_user_ids\"\s*:\s*\[([^\]]*)\])");
    
    std::smatch nameMatch, avatarMatch, membersMatch;
    std::string groupName, avatarUrl;
    std::vector<std::string> memberIds;

    if (std::regex_search(jsonData, nameMatch, nameRegex)) {
        groupName = nameMatch[1].str();
    }
    if (std::regex_search(jsonData, avatarMatch, avatarRegex)) {
        avatarUrl = avatarMatch[1].str();
    }
    if (std::regex_search(jsonData, membersMatch, membersRegex)) {
        std::string membersStr = membersMatch[1].str();
        // 简单解析 user_id 列表（格式: "u_1", "u_2"）
        std::regex idRegex(R"(\"([^\"]+)\")");
        std::sregex_iterator iter(membersStr.begin(), membersStr.end(), idRegex);
        std::sregex_iterator end;
        for (; iter != end; ++iter) {
            memberIds.push_back((*iter)[1].str());
        }
    }

    if (groupName.empty()) {
        server.sendMessage(fd, MessageType::GROUP_CREATE_RESPONSE,
                           R"({"success":false,"error_code":3001,"error_message":"群名称不能为空"})");
        return;
    }

    Database& db = Database::getInstance();
    if (!db.isConnected()) {
        server.sendMessage(fd, MessageType::GROUP_CREATE_RESPONSE,
                           R"({"success":false,"error_code":5000,"error_message":"服务器数据库未连接"})");
        return;
    }
    MYSQL* conn = db.getConnection();

    // 创建群
    std::string escapedName = escapeSql(conn, groupName);
    std::string escapedAvatar = avatarUrl.empty() ? "NULL" : ("'" + escapeSql(conn, avatarUrl) + "'");
    std::string escapedOwnerId = escapeSql(conn, creatorInfo->userId);

    std::ostringstream insertGroup;
    insertGroup << "INSERT INTO groups (group_name, owner_id, avatar_url) VALUES ('"
                << escapedName << "', " << escapedOwnerId << ", " << escapedAvatar << ")";

    if (mysql_query(conn, insertGroup.str().c_str()) != 0) {
        Logger::error("创建群失败: " + std::string(mysql_error(conn)));
        server.sendMessage(fd, MessageType::GROUP_CREATE_RESPONSE,
                           R"({"success":false,"error_code":5001,"error_message":"创建群失败"})");
        return;
    }

    unsigned long groupId = mysql_insert_id(conn);
    std::string groupIdStr = std::to_string(groupId);

    // 添加创建者为群主
    std::ostringstream insertOwner;
    insertOwner << "INSERT INTO group_members (group_id, user_id, role) VALUES ("
                << groupIdStr << ", " << escapedOwnerId << ", 'owner')";
    if (mysql_query(conn, insertOwner.str().c_str()) != 0) {
        Logger::error("添加群主失败: " + std::string(mysql_error(conn)));
    }

    // 添加其他成员
    for (const auto& memberId : memberIds) {
        if (memberId == creatorInfo->userId) continue; // 跳过创建者自己
        
        // 验证用户是否存在
        std::string escapedMemberId = escapeSql(conn, memberId);
        std::string checkQuery = "SELECT COUNT(*) FROM users WHERE user_id = " + escapedMemberId;
        if (mysql_query(conn, checkQuery.c_str()) == 0) {
            MYSQL_RES* res = mysql_store_result(conn);
            if (res) {
                MYSQL_ROW row = mysql_fetch_row(res);
                if (row && std::atoi(row[0]) > 0) {
                    std::ostringstream insertMember;
                    insertMember << "INSERT INTO group_members (group_id, user_id, role) VALUES ("
                                 << groupIdStr << ", " << escapedMemberId << ", 'member')";
                    mysql_query(conn, insertMember.str().c_str());
                }
                mysql_free_result(res);
            }
        }
    }

    // 返回成功响应
    std::ostringstream resp;
    resp << R"({"success":true,"group":{"group_id":")" << groupIdStr
         << R"(","group_name":")" << escapeJsonString(groupName)
         << R"(","owner_id":")" << escapeJsonString(creatorInfo->userId)
         << R"(","avatar_url":")" << escapeJsonString(avatarUrl)
         << R"(","announcement":"","created_at":)" << std::time(nullptr) << "}}";
    server.sendMessage(fd, MessageType::GROUP_CREATE_RESPONSE, resp.str());
    Logger::info("[群聊] 创建群成功: group_id=" + groupIdStr + ", creator=" + creatorInfo->username);
}

void GroupHandler::handleGroupList(EpollServer& server, int fd, const std::string& /*jsonData*/) {
    auto userInfo = server.getClientInfo(fd);
    if (!userInfo || !userInfo->authenticated) {
        server.sendMessage(fd, MessageType::ERROR,
                           R"({"success":false,"error_code":1001,"error_message":"请先登录"})");
        return;
    }

    Database& db = Database::getInstance();
    if (!db.isConnected()) {
        server.sendMessage(fd, MessageType::GROUP_LIST_RESPONSE,
                           R"({"success":false,"error_code":5000,"error_message":"服务器数据库未连接"})");
        return;
    }
    MYSQL* conn = db.getConnection();

    std::string escapedUserId = escapeSql(conn, userInfo->userId);
    std::string query =
        "SELECT g.group_id, g.group_name, g.avatar_url, g.announcement, gm.role "
        "FROM groups g "
        "JOIN group_members gm ON g.group_id = gm.group_id "
        "WHERE gm.user_id = " + escapedUserId;

    if (mysql_query(conn, query.c_str()) != 0) {
        Logger::error("查询群列表失败: " + std::string(mysql_error(conn)));
        server.sendMessage(fd, MessageType::GROUP_LIST_RESPONSE,
                           R"({"success":false,"error_code":5002,"error_message":"查询群列表失败"})");
        return;
    }

    MYSQL_RES* res = mysql_store_result(conn);
    if (!res) {
        server.sendMessage(fd, MessageType::GROUP_LIST_RESPONSE,
                           R"({"success":false,"error_code":5002,"error_message":"查询群列表失败"})");
        return;
    }

    std::ostringstream resp;
    resp << R"({"success":true,"groups":[)";

    bool first = true;
    MYSQL_ROW row;
    while ((row = mysql_fetch_row(res)) != nullptr) {
        if (!first) resp << ",";
        first = false;

        std::string groupId = row[0] ? row[0] : "";
        std::string groupName = row[1] ? row[1] : "";
        std::string avatarUrl = row[2] ? row[2] : "";
        std::string announcement = row[3] ? row[3] : "";
        std::string role = row[4] ? row[4] : "";

        resp << R"({"group_id":")" << escapeJsonString(groupId)
             << R"(","group_name":")" << escapeJsonString(groupName)
             << R"(","avatar_url":")" << escapeJsonString(avatarUrl)
             << R"(","announcement":)";
        
        // announcement 如果为空，返回 null，否则返回字符串
        if (announcement.empty()) {
            resp << "null";
        } else {
            resp << "\"" << escapeJsonString(announcement) << "\"";
        }
        
        resp << R"(,"role":")" << escapeJsonString(role) << "\"}";
    }
    mysql_free_result(res);

    resp << "]}";
    server.sendMessage(fd, MessageType::GROUP_LIST_RESPONSE, resp.str());
}

void GroupHandler::handleMemberList(EpollServer& server, int fd, const std::string& jsonData) {
    auto userInfo = server.getClientInfo(fd);
    if (!userInfo || !userInfo->authenticated) {
        server.sendMessage(fd, MessageType::ERROR,
                           R"({"success":false,"error_code":1001,"error_message":"请先登录"})");
        return;
    }

    // 解析 group_id
    std::regex groupIdRegex(R"(\"group_id\"\s*:\s*\"([^\"]+)\")");
    std::smatch match;
    std::string groupId;
    if (std::regex_search(jsonData, match, groupIdRegex)) {
        groupId = match[1].str();
    }

    if (groupId.empty()) {
        server.sendMessage(fd, MessageType::GROUP_MEMBER_LIST_RESPONSE,
                           R"({"success":false,"error_code":3002,"error_message":"group_id 不能为空"})");
        return;
    }

    Database& db = Database::getInstance();
    if (!db.isConnected()) {
        server.sendMessage(fd, MessageType::GROUP_MEMBER_LIST_RESPONSE,
                           R"({"success":false,"error_code":5000,"error_message":"服务器数据库未连接"})");
        return;
    }
    MYSQL* conn = db.getConnection();

    // 检查用户是否为群成员
    if (!isGroupMember(conn, groupId, userInfo->userId)) {
        server.sendMessage(fd, MessageType::GROUP_MEMBER_LIST_RESPONSE,
                           R"({"success":false,"error_code":3003,"error_message":"您不是该群成员"})");
        return;
    }

    // 查询群信息
    std::string escapedGroupId = escapeSql(conn, groupId);
    std::string groupQuery = 
        "SELECT group_id, group_name, owner_id, avatar_url, announcement, UNIX_TIMESTAMP(created_at) "
        "FROM groups WHERE group_id = " + escapedGroupId;
    
    std::string groupIdStr, groupName, ownerId, avatarUrl, announcement;
    time_t createdAt = 0;
    
    if (mysql_query(conn, groupQuery.c_str()) == 0) {
        MYSQL_RES* groupRes = mysql_store_result(conn);
        if (groupRes) {
            MYSQL_ROW groupRow = mysql_fetch_row(groupRes);
            if (groupRow) {
                groupIdStr = groupRow[0] ? groupRow[0] : "";
                groupName = groupRow[1] ? groupRow[1] : "";
                ownerId = groupRow[2] ? groupRow[2] : "";
                avatarUrl = groupRow[3] ? groupRow[3] : "";
                announcement = groupRow[4] ? groupRow[4] : "";
                if (groupRow[5]) {
                    createdAt = std::atoi(groupRow[5]);
                }
            }
            mysql_free_result(groupRes);
        }
    }

    // 查询群成员列表
    std::string query =
        "SELECT gm.user_id, gm.nickname_in_group, gm.role, u.nickname "
        "FROM group_members gm "
        "JOIN users u ON gm.user_id = u.user_id "
        "WHERE gm.group_id = " + escapedGroupId;

    if (mysql_query(conn, query.c_str()) != 0) {
        Logger::error("查询群成员列表失败: " + std::string(mysql_error(conn)));
        server.sendMessage(fd, MessageType::GROUP_MEMBER_LIST_RESPONSE,
                           R"({"success":false,"error_code":5003,"error_message":"查询群成员列表失败"})");
        return;
    }

    MYSQL_RES* res = mysql_store_result(conn);
    if (!res) {
        server.sendMessage(fd, MessageType::GROUP_MEMBER_LIST_RESPONSE,
                           R"({"success":false,"error_code":5003,"error_message":"查询群成员列表失败"})");
        return;
    }

    auto onlineUsers = server.getOnlineUsers();

    std::ostringstream resp;
    resp << R"({"success":true,"group_id":")" << escapeJsonString(groupId) << R"(","members":[)";

    bool first = true;
    MYSQL_ROW row;
    while ((row = mysql_fetch_row(res)) != nullptr) {
        if (!first) resp << ",";
        first = false;

        std::string userId = row[0] ? row[0] : "";
        std::string nicknameInGroup = row[1] ? row[1] : "";
        std::string role = row[2] ? row[2] : "";
        std::string nickname = row[3] ? row[3] : "";

        bool online = false;
        for (const auto& uid : onlineUsers) {
            if (uid == userId) {
                online = true;
                break;
            }
        }

        resp << R"({"user_id":")" << escapeJsonString(userId)
             << R"(","nickname_in_group":")" << escapeJsonString(nicknameInGroup.empty() ? nickname : nicknameInGroup)
             << R"(","role":")" << escapeJsonString(role)
             << R"(","online":)" << (online ? "true" : "false") << "}";
    }
    mysql_free_result(res);

    resp << "],\"group\":{"
         << R"("group_id":")" << escapeJsonString(groupIdStr.empty() ? groupId : groupIdStr)
         << R"(","group_name":")" << escapeJsonString(groupName)
         << R"(","owner_id":")" << escapeJsonString(ownerId)
         << R"(","avatar_url":")" << escapeJsonString(avatarUrl)
         << R"(","announcement":)";
    
    // announcement 如果为空，返回 null，否则返回字符串
    if (announcement.empty()) {
        resp << "null";
    } else {
        resp << "\"" << escapeJsonString(announcement) << "\"";
    }
    
    resp << R"(,"created_at":)" << (createdAt > 0 ? std::to_string(createdAt) : std::to_string(std::time(nullptr)));
    resp << "}}";
    
    server.sendMessage(fd, MessageType::GROUP_MEMBER_LIST_RESPONSE, resp.str());
}

void GroupHandler::handleInvite(EpollServer& server, int fd, const std::string& jsonData) {
    auto inviterInfo = server.getClientInfo(fd);
    if (!inviterInfo || !inviterInfo->authenticated) {
        server.sendMessage(fd, MessageType::ERROR,
                           R"({"success":false,"error_code":1001,"error_message":"请先登录"})");
        return;
    }

    // 解析 group_id, member_user_ids
    std::regex groupIdRegex(R"(\"group_id\"\s*:\s*\"([^\"]+)\")");
    std::regex membersRegex(R"(\"member_user_ids\"\s*:\s*\[([^\]]*)\])");
    
    std::smatch groupMatch, membersMatch;
    std::string groupId;
    std::vector<std::string> memberIds;

    if (std::regex_search(jsonData, groupMatch, groupIdRegex)) {
        groupId = groupMatch[1].str();
    }
    if (std::regex_search(jsonData, membersMatch, membersRegex)) {
        std::string membersStr = membersMatch[1].str();
        std::regex idRegex(R"(\"([^\"]+)\")");
        std::sregex_iterator iter(membersStr.begin(), membersStr.end(), idRegex);
        std::sregex_iterator end;
        for (; iter != end; ++iter) {
            memberIds.push_back((*iter)[1].str());
        }
    }

    if (groupId.empty() || memberIds.empty()) {
        server.sendMessage(fd, MessageType::GROUP_INVITE_RESPONSE,
                           R"({"success":false,"error_code":3004,"error_message":"group_id 和 member_user_ids 不能为空"})");
        return;
    }

    Database& db = Database::getInstance();
    if (!db.isConnected()) {
        server.sendMessage(fd, MessageType::GROUP_INVITE_RESPONSE,
                           R"({"success":false,"error_code":5000,"error_message":"服务器数据库未连接"})");
        return;
    }
    MYSQL* conn = db.getConnection();

    // 检查邀请者是否为群成员（且不是被拉黑的）
    std::string inviterRole = getMemberRole(conn, groupId, inviterInfo->userId);
    if (inviterRole.empty()) {
        server.sendMessage(fd, MessageType::GROUP_INVITE_RESPONSE,
                           R"({"success":false,"error_code":3005,"error_message":"您不是该群成员"})");
        return;
    }

    std::string escapedGroupId = escapeSql(conn, groupId);
    int successCount = 0;

    // 添加成员
    for (const auto& memberId : memberIds) {
        if (memberId == inviterInfo->userId) continue;

        // 检查是否已是成员
        if (isGroupMember(conn, groupId, memberId)) continue;

        // 验证用户是否存在
        std::string escapedMemberId = escapeSql(conn, memberId);
        std::string checkQuery = "SELECT COUNT(*) FROM users WHERE user_id = " + escapedMemberId;
        if (mysql_query(conn, checkQuery.c_str()) == 0) {
            MYSQL_RES* res = mysql_store_result(conn);
            if (res) {
                MYSQL_ROW row = mysql_fetch_row(res);
                if (row && std::atoi(row[0]) > 0) {
                    std::ostringstream insertMember;
                    insertMember << "INSERT INTO group_members (group_id, user_id, role) VALUES ("
                                 << escapedGroupId << ", " << escapedMemberId << ", 'member')";
                    if (mysql_query(conn, insertMember.str().c_str()) == 0) {
                        successCount++;
                        
                        // 如果用户在线，发送通知
                        auto onlineUsers = server.getOnlineUsers();
                        for (const auto& uid : onlineUsers) {
                            if (uid == memberId) {
                                std::ostringstream notify;
                                notify << R"({"group_id":")" << escapeJsonString(groupId)
                                       << R"(","inviter_id":")" << escapeJsonString(inviterInfo->userId)
                                       << R"(","inviter_username":")" << escapeJsonString(inviterInfo->username)
                                       << "\"}";
                                server.sendMessageToUser(memberId, MessageType::GROUP_INVITE_NOTIFY, notify.str());
                                break;
                            }
                        }
                    }
                }
                mysql_free_result(res);
            }
        }
    }

    std::ostringstream resp;
    resp << R"({"success":true,"invited_count":)" << successCount << "}";
    server.sendMessage(fd, MessageType::GROUP_INVITE_RESPONSE, resp.str());
    Logger::info("[群聊] 邀请成员: group_id=" + groupId + ", inviter=" + inviterInfo->username + ", invited=" + std::to_string(successCount));
}

void GroupHandler::handleKick(EpollServer& server, int fd, const std::string& jsonData) {
    auto kickerInfo = server.getClientInfo(fd);
    if (!kickerInfo || !kickerInfo->authenticated) {
        server.sendMessage(fd, MessageType::ERROR,
                           R"({"success":false,"error_code":1001,"error_message":"请先登录"})");
        return;
    }

    // 解析 group_id, member_user_ids
    std::regex groupIdRegex(R"(\"group_id\"\s*:\s*\"([^\"]+)\")");
    std::regex membersRegex(R"(\"member_user_ids\"\s*:\s*\[([^\]]*)\])");
    
    std::smatch groupMatch, membersMatch;
    std::string groupId;
    std::vector<std::string> memberIds;

    if (std::regex_search(jsonData, groupMatch, groupIdRegex)) {
        groupId = groupMatch[1].str();
    }
    if (std::regex_search(jsonData, membersMatch, membersRegex)) {
        std::string membersStr = membersMatch[1].str();
        std::regex idRegex(R"(\"([^\"]+)\")");
        std::sregex_iterator iter(membersStr.begin(), membersStr.end(), idRegex);
        std::sregex_iterator end;
        for (; iter != end; ++iter) {
            memberIds.push_back((*iter)[1].str());
        }
    }

    if (groupId.empty() || memberIds.empty()) {
        server.sendMessage(fd, MessageType::GROUP_KICK_RESPONSE,
                           R"({"success":false,"error_code":3006,"error_message":"group_id 和 member_user_ids 不能为空"})");
        return;
    }

    Database& db = Database::getInstance();
    if (!db.isConnected()) {
        server.sendMessage(fd, MessageType::GROUP_KICK_RESPONSE,
                           R"({"success":false,"error_code":5000,"error_message":"服务器数据库未连接"})");
        return;
    }
    MYSQL* conn = db.getConnection();

    // 检查操作者权限（群主或管理员）
    std::string kickerRole = getMemberRole(conn, groupId, kickerInfo->userId);
    if (kickerRole != "owner" && kickerRole != "admin") {
        server.sendMessage(fd, MessageType::GROUP_KICK_RESPONSE,
                           R"({"success":false,"error_code":3007,"error_message":"权限不足，只有群主或管理员可以踢人"})");
        return;
    }

    std::string escapedGroupId = escapeSql(conn, groupId);
    int kickCount = 0;

    // 踢人
    for (const auto& memberId : memberIds) {
        if (memberId == kickerInfo->userId) continue; // 不能踢自己

        std::string memberRole = getMemberRole(conn, groupId, memberId);
        if (memberRole.empty()) continue; // 不是成员

        // 群主不能踢群主
        if (memberRole == "owner") continue;
        // 管理员只能由群主踢
        if (memberRole == "admin" && kickerRole != "owner") continue;

        std::string escapedMemberId = escapeSql(conn, memberId);
        std::ostringstream deleteMember;
        deleteMember << "DELETE FROM group_members WHERE group_id = " << escapedGroupId
                     << " AND user_id = " << escapedMemberId;
        
        if (mysql_query(conn, deleteMember.str().c_str()) == 0) {
            kickCount++;
            
            // 如果用户在线，发送通知
            auto onlineUsers = server.getOnlineUsers();
            for (const auto& uid : onlineUsers) {
                if (uid == memberId) {
                    std::ostringstream notify;
                    notify << R"({"group_id":")" << escapeJsonString(groupId)
                           << R"(","kicker_id":")" << escapeJsonString(kickerInfo->userId)
                           << "\"}";
                    server.sendMessageToUser(memberId, MessageType::GROUP_KICK_NOTIFY, notify.str());
                    break;
                }
            }
        }
    }

    std::ostringstream resp;
    resp << R"({"success":true,"kicked_count":)" << kickCount << "}";
    server.sendMessage(fd, MessageType::GROUP_KICK_RESPONSE, resp.str());
    Logger::info("[群聊] 踢人: group_id=" + groupId + ", kicker=" + kickerInfo->username + ", kicked=" + std::to_string(kickCount));
}

void GroupHandler::handleQuit(EpollServer& server, int fd, const std::string& jsonData) {
    auto userInfo = server.getClientInfo(fd);
    if (!userInfo || !userInfo->authenticated) {
        server.sendMessage(fd, MessageType::ERROR,
                           R"({"success":false,"error_code":1001,"error_message":"请先登录"})");
        return;
    }

    // 解析 group_id
    std::regex groupIdRegex(R"(\"group_id\"\s*:\s*\"([^\"]+)\")");
    std::smatch match;
    std::string groupId;
    if (std::regex_search(jsonData, match, groupIdRegex)) {
        groupId = match[1].str();
    }

    if (groupId.empty()) {
        server.sendMessage(fd, MessageType::GROUP_QUIT_RESPONSE,
                           R"({"success":false,"error_code":3008,"error_message":"group_id 不能为空"})");
        return;
    }

    Database& db = Database::getInstance();
    if (!db.isConnected()) {
        server.sendMessage(fd, MessageType::GROUP_QUIT_RESPONSE,
                           R"({"success":false,"error_code":5000,"error_message":"服务器数据库未连接"})");
        return;
    }
    MYSQL* conn = db.getConnection();

    // 检查用户是否为群成员
    std::string role = getMemberRole(conn, groupId, userInfo->userId);
    if (role.empty()) {
        server.sendMessage(fd, MessageType::GROUP_QUIT_RESPONSE,
                           R"({"success":false,"error_code":3009,"error_message":"您不是该群成员"})");
        return;
    }

    // 群主不能直接退群，需要先解散
    if (role == "owner") {
        server.sendMessage(fd, MessageType::GROUP_QUIT_RESPONSE,
                           R"({"success":false,"error_code":3010,"error_message":"群主不能退群，请先解散群"})");
        return;
    }

    std::string escapedGroupId = escapeSql(conn, groupId);
    std::string escapedUserId = escapeSql(conn, userInfo->userId);

    std::ostringstream deleteMember;
    deleteMember << "DELETE FROM group_members WHERE group_id = " << escapedGroupId
                 << " AND user_id = " << escapedUserId;

    if (mysql_query(conn, deleteMember.str().c_str()) != 0) {
        Logger::error("退群失败: " + std::string(mysql_error(conn)));
        server.sendMessage(fd, MessageType::GROUP_QUIT_RESPONSE,
                           R"({"success":false,"error_code":5004,"error_message":"退群失败"})");
        return;
    }

    // 通知群成员
    auto memberIds = getGroupMemberIds(conn, groupId);
    for (const auto& memberId : memberIds) {
        auto onlineUsers = server.getOnlineUsers();
        for (const auto& uid : onlineUsers) {
            if (uid == memberId) {
                std::ostringstream notify;
                notify << R"({"group_id":")" << escapeJsonString(groupId)
                       << R"(","quit_user_id":")" << escapeJsonString(userInfo->userId)
                       << R"(","quit_username":")" << escapeJsonString(userInfo->username)
                       << "\"}";
                server.sendMessageToUser(memberId, MessageType::GROUP_QUIT_NOTIFY, notify.str());
                break;
            }
        }
    }

    server.sendMessage(fd, MessageType::GROUP_QUIT_RESPONSE,
                       R"({"success":true,"message":"已退出群聊"})");
    Logger::info("[群聊] 退群: group_id=" + groupId + ", user=" + userInfo->username);
}

void GroupHandler::handleDismiss(EpollServer& server, int fd, const std::string& jsonData) {
    auto userInfo = server.getClientInfo(fd);
    if (!userInfo || !userInfo->authenticated) {
        server.sendMessage(fd, MessageType::ERROR,
                           R"({"success":false,"error_code":1001,"error_message":"请先登录"})");
        return;
    }

    // 解析 group_id
    std::regex groupIdRegex(R"(\"group_id\"\s*:\s*\"([^\"]+)\")");
    std::smatch match;
    std::string groupId;
    if (std::regex_search(jsonData, match, groupIdRegex)) {
        groupId = match[1].str();
    }

    if (groupId.empty()) {
        server.sendMessage(fd, MessageType::GROUP_DISMISS_RESPONSE,
                           R"({"success":false,"error_code":3011,"error_message":"group_id 不能为空"})");
        return;
    }

    Database& db = Database::getInstance();
    if (!db.isConnected()) {
        server.sendMessage(fd, MessageType::GROUP_DISMISS_RESPONSE,
                           R"({"success":false,"error_code":5000,"error_message":"服务器数据库未连接"})");
        return;
    }
    MYSQL* conn = db.getConnection();

    // 检查是否为群主
    std::string query = "SELECT owner_id FROM groups WHERE group_id = " + escapeSql(conn, groupId);
    if (mysql_query(conn, query.c_str()) != 0) {
        server.sendMessage(fd, MessageType::GROUP_DISMISS_RESPONSE,
                           R"({"success":false,"error_code":5005,"error_message":"查询群信息失败"})");
        return;
    }

    MYSQL_RES* res = mysql_store_result(conn);
    if (!res) {
        server.sendMessage(fd, MessageType::GROUP_DISMISS_RESPONSE,
                           R"({"success":false,"error_code":5005,"error_message":"查询群信息失败"})");
        return;
    }

    MYSQL_ROW row = mysql_fetch_row(res);
    if (!row || !row[0]) {
        mysql_free_result(res);
        server.sendMessage(fd, MessageType::GROUP_DISMISS_RESPONSE,
                           R"({"success":false,"error_code":3012,"error_message":"群不存在"})");
        return;
    }

    std::string ownerId = row[0];
    mysql_free_result(res);

    if (ownerId != userInfo->userId) {
        server.sendMessage(fd, MessageType::GROUP_DISMISS_RESPONSE,
                           R"({"success":false,"error_code":3013,"error_message":"只有群主可以解散群"})");
        return;
    }

    // 获取所有成员ID（用于通知）
    auto memberIds = getGroupMemberIds(conn, groupId);

    // 删除群成员
    std::string escapedGroupId = escapeSql(conn, groupId);
    std::ostringstream deleteMembers;
    deleteMembers << "DELETE FROM group_members WHERE group_id = " << escapedGroupId;
    if (mysql_query(conn, deleteMembers.str().c_str()) != 0) {
        Logger::error("删除群成员失败: " + std::string(mysql_error(conn)));
    }

    // 删除群
    std::ostringstream deleteGroup;
    deleteGroup << "DELETE FROM groups WHERE group_id = " << escapedGroupId;
    if (mysql_query(conn, deleteGroup.str().c_str()) != 0) {
        Logger::error("解散群失败: " + std::string(mysql_error(conn)));
        server.sendMessage(fd, MessageType::GROUP_DISMISS_RESPONSE,
                           R"({"success":false,"error_code":5006,"error_message":"解散群失败"})");
        return;
    }

    // 通知所有成员
    for (const auto& memberId : memberIds) {
        if (memberId == userInfo->userId) continue; // 跳过自己
        auto onlineUsers = server.getOnlineUsers();
        for (const auto& uid : onlineUsers) {
            if (uid == memberId) {
                std::ostringstream notify;
                notify << R"({"group_id":")" << escapeJsonString(groupId) << "\"}";
                server.sendMessageToUser(memberId, MessageType::GROUP_DISMISS_NOTIFY, notify.str());
                break;
            }
        }
    }

    server.sendMessage(fd, MessageType::GROUP_DISMISS_RESPONSE,
                       R"({"success":true,"message":"群已解散"})");
    Logger::info("[群聊] 解散群: group_id=" + groupId + ", owner=" + userInfo->username);
}

void GroupHandler::handleUpdateInfo(EpollServer& server, int fd, const std::string& jsonData) {
    auto userInfo = server.getClientInfo(fd);
    if (!userInfo || !userInfo->authenticated) {
        server.sendMessage(fd, MessageType::ERROR,
                           R"({"success":false,"error_code":1001,"error_message":"请先登录"})");
        return;
    }

    // 解析 group_id, group_name, announcement
    std::regex groupIdRegex(R"(\"group_id\"\s*:\s*\"([^\"]+)\")");
    std::regex nameRegex(R"(\"group_name\"\s*:\s*\"([^\"]*)\")");
    std::regex announcementRegex(R"(\"announcement\"\s*:\s*\"([^\"]*)\")");
    
    std::smatch groupMatch, nameMatch, announcementMatch;
    std::string groupId, groupName, announcement;

    if (std::regex_search(jsonData, groupMatch, groupIdRegex)) {
        groupId = groupMatch[1].str();
    }
    if (std::regex_search(jsonData, nameMatch, nameRegex)) {
        groupName = nameMatch[1].str();
    }
    if (std::regex_search(jsonData, announcementMatch, announcementRegex)) {
        announcement = announcementMatch[1].str();
    }

    if (groupId.empty()) {
        server.sendMessage(fd, MessageType::GROUP_UPDATE_INFO_RESPONSE,
                           R"({"success":false,"error_code":3014,"error_message":"group_id 不能为空"})");
        return;
    }

    Database& db = Database::getInstance();
    if (!db.isConnected()) {
        server.sendMessage(fd, MessageType::GROUP_UPDATE_INFO_RESPONSE,
                           R"({"success":false,"error_code":5000,"error_message":"服务器数据库未连接"})");
        return;
    }
    MYSQL* conn = db.getConnection();

    // 检查权限（群主或管理员）
    std::string role = getMemberRole(conn, groupId, userInfo->userId);
    if (role != "owner" && role != "admin") {
        server.sendMessage(fd, MessageType::GROUP_UPDATE_INFO_RESPONSE,
                           R"({"success":false,"error_code":3015,"error_message":"权限不足，只有群主或管理员可以更新群信息"})");
        return;
    }

    std::string escapedGroupId = escapeSql(conn, groupId);
    std::ostringstream updateSql;
    updateSql << "UPDATE groups SET ";

    bool hasUpdate = false;
    if (!groupName.empty()) {
        updateSql << "group_name = '" << escapeSql(conn, groupName) << "'";
        hasUpdate = true;
    }
    if (!announcement.empty()) {
        if (hasUpdate) updateSql << ", ";
        updateSql << "announcement = '" << escapeSql(conn, announcement) << "'";
        hasUpdate = true;
    }

    if (!hasUpdate) {
        server.sendMessage(fd, MessageType::GROUP_UPDATE_INFO_RESPONSE,
                           R"({"success":false,"error_code":3016,"error_message":"至少需要更新一个字段"})");
        return;
    }

    updateSql << " WHERE group_id = " << escapedGroupId;

    if (mysql_query(conn, updateSql.str().c_str()) != 0) {
        Logger::error("更新群信息失败: " + std::string(mysql_error(conn)));
        server.sendMessage(fd, MessageType::GROUP_UPDATE_INFO_RESPONSE,
                           R"({"success":false,"error_code":5007,"error_message":"更新群信息失败"})");
        return;
    }

    // 通知群成员
    auto memberIds = getGroupMemberIds(conn, groupId);
    for (const auto& memberId : memberIds) {
        if (memberId == userInfo->userId) continue;
        auto onlineUsers = server.getOnlineUsers();
        for (const auto& uid : onlineUsers) {
            if (uid == memberId) {
                std::ostringstream notify;
                notify << R"({"group_id":")" << escapeJsonString(groupId)
                       << R"(","group_name":")" << escapeJsonString(groupName.empty() ? "" : groupName)
                       << R"(","announcement":")" << escapeJsonString(announcement.empty() ? "" : announcement)
                       << "\"}";
                server.sendMessageToUser(memberId, MessageType::GROUP_UPDATE_INFO_NOTIFY, notify.str());
                break;
            }
        }
    }

    server.sendMessage(fd, MessageType::GROUP_UPDATE_INFO_RESPONSE,
                       R"({"success":true,"message":"群信息已更新"})");
    Logger::info("[群聊] 更新群信息: group_id=" + groupId + ", updater=" + userInfo->username);
}

}  // namespace im
