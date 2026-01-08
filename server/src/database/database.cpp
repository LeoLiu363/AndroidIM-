#include "database.h"
#include "utils/logger.h"
#include <cstring>
#include <sstream>

namespace im {

Database& Database::getInstance() {
    static Database instance;
    return instance;
}

Database::~Database() {
    close();
}

bool Database::init(const std::string& host, 
                   const std::string& user, 
                   const std::string& password,
                   const std::string& database,
                   unsigned int port) {
    mysql_ = mysql_init(nullptr);
    if (!mysql_) {
        Logger::error("初始化 MySQL 失败: " + std::string(mysql_error(mysql_)));
        return false;
    }
    
    // 设置字符集
    mysql_options(mysql_, MYSQL_SET_CHARSET_NAME, "utf8mb4");
    
    // 连接数据库（使用 TCP 连接，不使用 socket）
    // 如果 host 是 "localhost"，MySQL 默认使用 socket，需要明确指定为 "127.0.0.1"
    std::string connectHost = (host == "localhost") ? "127.0.0.1" : host;
    
    MYSQL* result = mysql_real_connect(mysql_,
                                       connectHost.c_str(),
                                       user.c_str(),
                                       password.c_str(),
                                       database.c_str(),
                                       port,
                                       nullptr,
                                       CLIENT_FOUND_ROWS);
    
    if (!result) {
        Logger::error("连接 MySQL 失败: " + std::string(mysql_error(mysql_)));
        mysql_close(mysql_);
        mysql_ = nullptr;
        return false;
    }
    
    // 保存连接参数以便重连
    host_ = host;
    user_ = user;
    password_ = password;
    database_ = database;
    port_ = port;
    
    connected_ = true;
    Logger::info("MySQL 数据库连接成功: " + host + ":" + std::to_string(port) + "/" + database);
    return true;
}

void Database::close() {
    if (mysql_) {
        mysql_close(mysql_);
        mysql_ = nullptr;
        connected_ = false;
        Logger::info("MySQL 数据库连接已关闭");
    }
}

bool Database::ensureConnected() {
    if (!mysql_ || !connected_) {
        // 如果没有连接参数，无法重连
        if (host_.empty()) {
            return false;
        }
        // 尝试重连
        Logger::warn("数据库连接已断开，尝试重连...");
        close();
        return init(host_, user_, password_, database_, port_);
    }
    
    // 检查连接是否有效（使用 ping）
    if (mysql_ping(mysql_) != 0) {
        Logger::warn("数据库连接无效，尝试重连...");
        close();
        return init(host_, user_, password_, database_, port_);
    }
    
    return true;
}

MYSQL* Database::getConnection() {
    ensureConnected();
    return mysql_;
}

std::string Database::escapeString(const std::string& str) {
    if (!mysql_ || !connected_) {
        return str;
    }
    
    char* escaped = new char[str.length() * 2 + 1];
    unsigned long len = mysql_real_escape_string(mysql_, escaped, str.c_str(), str.length());
    std::string result(escaped, len);
    delete[] escaped;
    return result;
}

bool Database::userExists(const std::string& username) {
    if (!mysql_ || !connected_) {
        Logger::error("数据库未连接");
        return false;
    }
    
    std::string escapedUsername = escapeString(username);
    std::string query = "SELECT COUNT(*) FROM users WHERE username = '" + escapedUsername + "'";
    
    if (mysql_query(mysql_, query.c_str()) != 0) {
        Logger::error("查询用户是否存在失败: " + std::string(mysql_error(mysql_)));
        return false;
    }
    
    MYSQL_RES* result = mysql_store_result(mysql_);
    if (!result) {
        Logger::error("获取查询结果失败: " + std::string(mysql_error(mysql_)));
        return false;
    }
    
    MYSQL_ROW row = mysql_fetch_row(result);
    bool exists = (row && atoi(row[0]) > 0);
    mysql_free_result(result);
    
    return exists;
}

bool Database::verifyUser(const std::string& username, 
                         const std::string& password,
                         std::string& userId,
                         std::string& nickname) {
    if (!mysql_ || !connected_) {
        Logger::error("数据库未连接");
        return false;
    }
    
    std::string escapedUsername = escapeString(username);
    std::string escapedPassword = escapeString(password);
    
    // 注意：这里使用明文密码比较，实际生产环境应该使用加密后的密码比较
    // 例如：password_hash() 和 password_verify() 或使用 MD5/SHA256 等
    std::string query = "SELECT user_id, nickname FROM users WHERE username = '" + 
                       escapedUsername + "' AND password = '" + escapedPassword + "'";
    
    if (mysql_query(mysql_, query.c_str()) != 0) {
        Logger::error("验证用户失败: " + std::string(mysql_error(mysql_)));
        return false;
    }
    
    MYSQL_RES* result = mysql_store_result(mysql_);
    if (!result) {
        Logger::error("获取查询结果失败: " + std::string(mysql_error(mysql_)));
        return false;
    }
    
    MYSQL_ROW row = mysql_fetch_row(result);
    if (!row) {
        mysql_free_result(result);
        return false;  // 用户名或密码错误
    }
    
    userId = std::string(row[0]);
    nickname = row[1] ? std::string(row[1]) : username;
    mysql_free_result(result);
    
    return true;
}

bool Database::registerUser(const std::string& username,
                           const std::string& password,
                           const std::string& nickname,
                           std::string& userId) {
    if (!mysql_ || !connected_) {
        Logger::error("数据库未连接");
        return false;
    }
    
    // 检查用户名是否已存在
    if (userExists(username)) {
        return false;
    }
    
    std::string escapedUsername = escapeString(username);
    std::string escapedPassword = escapeString(password);
    std::string escapedNickname = nickname.empty() ? "NULL" : ("'" + escapeString(nickname) + "'");
    
    std::string query = "INSERT INTO users (username, password, nickname) VALUES ('" +
                       escapedUsername + "', '" + escapedPassword + "', " + escapedNickname + ")";
    
    if (mysql_query(mysql_, query.c_str()) != 0) {
        Logger::error("注册用户失败: " + std::string(mysql_error(mysql_)));
        return false;
    }
    
    // 获取插入的用户ID
    unsigned long insertId = mysql_insert_id(mysql_);
    userId = std::to_string(insertId);
    
    Logger::info("用户注册成功: username=" + username + ", user_id=" + userId);
    return true;
}

}  // namespace im

