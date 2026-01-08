#include "user_handler.h"
#include "server/epoll_server.h"
#include "protocol/message.h"
#include "utils/logger.h"
#include <sstream>
#include <map>
#include <mutex>

namespace im {

// 用户昵称存储（实际应该从数据库获取）
static std::map<std::string, std::string> userNicknames;  // userId -> nickname
static std::mutex nicknamesMutex_;

void UserHandler::handleUserList(EpollServer& server, int fd) {
    auto onlineUsers = server.getOnlineUsersWithInfo();
    
    std::ostringstream response;
    response << R"({"users":[)";
    
    bool first = true;
    for (const auto& [userId, username] : onlineUsers) {
        if (!first) {
            response << ",";
        }
        first = false;
        
        // 获取昵称（优先使用存储的昵称，否则使用用户名）
        std::string nickname = username;
        {
            std::lock_guard<std::mutex> lock(nicknamesMutex_);
            if (userNicknames.count(userId)) {
                nickname = userNicknames[userId];
            }
        }
        
        response << R"({"user_id":")" << userId
                 << R"(","username":")" << username
                 << R"(","nickname":")" << nickname
                 << R"(","online":true})";
    }
    
    response << "]}";
    
    server.sendMessage(fd, MessageType::USER_LIST_RESPONSE, response.str());
    Logger::info("返回用户列表: " + std::to_string(onlineUsers.size()) + " 个在线用户");
}

}  // namespace im

