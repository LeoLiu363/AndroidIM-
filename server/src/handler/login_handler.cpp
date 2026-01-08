#include "login_handler.h"
#include "server/epoll_server.h"
#include "protocol/message.h"
#include "database/database.h"
#include "utils/logger.h"
#include <iostream>
#include <sstream>
#include <regex>

namespace im {

void LoginHandler::handle(EpollServer& server, int fd, const std::string& jsonData) {
    Logger::info("[登录处理] 开始处理登录请求: fd=" + std::to_string(fd) + ", jsonData=" + jsonData);
    
    // 简单的 JSON 解析（实际应该使用 JSON 库）
    std::regex usernameRegex(R"(\"username\"\s*:\s*\"([^\"]+)\")");
    std::regex passwordRegex(R"(\"password\"\s*:\s*\"([^\"]+)\")");
    
    std::smatch usernameMatch, passwordMatch;
    std::string username, password;
    
    if (std::regex_search(jsonData, usernameMatch, usernameRegex)) {
        username = usernameMatch[1].str();
    }
    if (std::regex_search(jsonData, passwordMatch, passwordRegex)) {
        password = passwordMatch[1].str();
    }
    
    Logger::info("[登录处理] 解析结果: username=" + username + ", password_length=" + std::to_string(password.length()));
    
    if (username.empty() || password.empty()) {
        std::string response = R"({"success":false,"message":"用户名或密码不能为空","user_id":null,"username":null})";
        Logger::warn("[登录处理] 用户名或密码为空，返回错误响应");
        server.sendMessage(fd, MessageType::LOGIN_RESPONSE, response);
        return;
    }
    
    // 从数据库验证用户
    Logger::info("[登录处理] 开始验证用户: username=" + username);
    std::string userId, nickname;
    Database& db = Database::getInstance();
    
    // 检查数据库连接状态
    if (!db.isConnected()) {
        std::string response = R"({"success":false,"message":"服务器内部错误，请稍后重试","user_id":null,"username":null})";
        Logger::error("[登录处理] ✗ 数据库未连接，无法验证用户: username=" + username + " (fd=" + std::to_string(fd) + ")");
        server.sendMessage(fd, MessageType::LOGIN_RESPONSE, response);
        return;
    }
    
    bool success = db.verifyUser(username, password, userId, nickname);
    
    Logger::info("[登录处理] 验证结果: success=" + std::string(success ? "true" : "false") + 
                 ", userId=" + userId + ", nickname=" + nickname);
    
    std::ostringstream response;
    if (success) {
        response << R"({"success":true,"message":"登录成功","user_id":")"
                 << userId << R"(","username":")" << username << R"("})";
        
        // 标记为已认证
        server.setClientAuthenticated(fd, userId, username);
        Logger::info("[登录处理] ✓ 用户登录成功: username=" + username + ", user_id=" + userId + " (fd=" + std::to_string(fd) + ")");
    } else {
        // 登录失败：用户名或密码错误
        response << R"({"success":false,"message":"用户名或密码错误","user_id":null,"username":null})";
        Logger::warn("[登录处理] ✗ 登录失败: username=" + username + " (fd=" + std::to_string(fd) + ")");
        // 注意：登录失败时不关闭连接，允许客户端重试
    }
    
    std::string responseStr = response.str();
    Logger::info("[登录处理] 准备发送响应: fd=" + std::to_string(fd) + ", response=" + responseStr);
    std::cout.flush();
    server.sendMessage(fd, MessageType::LOGIN_RESPONSE, responseStr);
    Logger::info("[登录处理] 登录请求处理完成: fd=" + std::to_string(fd));
    std::cout.flush();
}

void LoginHandler::handleRegister(EpollServer& server, int fd, const std::string& jsonData) {
    Logger::info("[注册处理] 开始处理注册请求: fd=" + std::to_string(fd) + ", jsonData=" + jsonData);
    
    // 解析 JSON
    std::regex usernameRegex(R"(\"username\"\s*:\s*\"([^\"]+)\")");
    std::regex passwordRegex(R"(\"password\"\s*:\s*\"([^\"]+)\")");
    std::regex nicknameRegex(R"(\"nickname\"\s*:\s*\"([^\"]+)\")");
    
    std::smatch usernameMatch, passwordMatch, nicknameMatch;
    std::string username, password, nickname;
    
    if (std::regex_search(jsonData, usernameMatch, usernameRegex)) {
        username = usernameMatch[1].str();
    }
    if (std::regex_search(jsonData, passwordMatch, passwordRegex)) {
        password = passwordMatch[1].str();
    }
    if (std::regex_search(jsonData, nicknameMatch, nicknameRegex)) {
        nickname = nicknameMatch[1].str();
    }
    
    Logger::info("[注册处理] 解析结果: username=" + username + 
                 ", password_length=" + std::to_string(password.length()) +
                 ", nickname=" + nickname);
    
    if (username.empty() || password.empty()) {
        std::string response = R"({"success":false,"message":"用户名或密码不能为空","user_id":null})";
        Logger::warn("[注册处理] 用户名或密码为空，返回错误响应");
        server.sendMessage(fd, MessageType::REGISTER_RESPONSE, response);
        return;
    }
    
    // 从数据库注册用户
    Logger::info("[注册处理] 开始注册用户: username=" + username);
    std::string userId;
    Database& db = Database::getInstance();
    bool success = db.registerUser(username, password, nickname, userId);
    
    Logger::info("[注册处理] 注册结果: success=" + std::string(success ? "true" : "false") + 
                 ", userId=" + userId);
    
    std::ostringstream response;
    if (success) {
        response << R"({"success":true,"message":"注册成功","user_id":")"
                 << userId << R"("})";
        
        // 自动登录
        server.setClientAuthenticated(fd, userId, username);
        Logger::info("[注册处理] ✓ 用户注册成功: username=" + username + ", user_id=" + userId + " (fd=" + std::to_string(fd) + ")");
    } else {
        // 检查是否是用户名已存在
        bool exists = db.userExists(username);
        Logger::info("[注册处理] 检查用户名是否存在: exists=" + std::string(exists ? "true" : "false"));
        
        if (exists) {
            response << R"({"success":false,"message":"用户名已存在","user_id":null})";
            Logger::warn("[注册处理] ✗ 注册失败: 用户名已存在 - " + username);
        } else {
            response << R"({"success":false,"message":"注册失败，请稍后重试","user_id":null})";
            Logger::error("[注册处理] ✗ 注册失败: username=" + username + " (fd=" + std::to_string(fd) + ")");
        }
    }
    
    std::string responseStr = response.str();
    Logger::info("[注册处理] 准备发送响应: fd=" + std::to_string(fd) + ", response=" + responseStr);
    server.sendMessage(fd, MessageType::REGISTER_RESPONSE, responseStr);
    Logger::info("[注册处理] 注册请求处理完成: fd=" + std::to_string(fd));
}

}  // namespace im

