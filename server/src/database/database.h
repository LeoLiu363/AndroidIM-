#ifndef DATABASE_H
#define DATABASE_H

#include <string>
#include <memory>
#include <mysql/mysql.h>

namespace im {

class Database {
public:
    /**
     * 获取数据库实例（单例模式）
     */
    static Database& getInstance();
    
    /**
     * 初始化数据库连接
     * 
     * @param host MySQL 主机地址
     * @param user MySQL 用户名
     * @param password MySQL 密码
     * @param database 数据库名
     * @param port MySQL 端口（默认3306）
     * @return 是否成功
     */
    bool init(const std::string& host, 
              const std::string& user, 
              const std::string& password,
              const std::string& database,
              unsigned int port = 3306);
    
    /**
     * 关闭数据库连接
     */
    void close();
    
    /**
     * 检查用户名是否存在
     * 
     * @param username 用户名
     * @return 是否存在
     */
    bool userExists(const std::string& username);
    
    /**
     * 验证用户登录
     * 
     * @param username 用户名
     * @param password 密码（明文）
     * @param userId 输出参数：用户ID
     * @param nickname 输出参数：昵称
     * @return 是否验证成功
     */
    bool verifyUser(const std::string& username, 
                    const std::string& password,
                    std::string& userId,
                    std::string& nickname);
    
    /**
     * 注册新用户
     * 
     * @param username 用户名
     * @param password 密码（明文）
     * @param nickname 昵称
     * @param userId 输出参数：新创建的用户ID
     * @return 是否成功
     */
    bool registerUser(const std::string& username,
                     const std::string& password,
                     const std::string& nickname,
                     std::string& userId);
    
    /**
     * 检查数据库是否已连接
     * 
     * @return 是否已连接
     */
    bool isConnected() const { return mysql_ != nullptr && connected_; }
    
    /**
     * 确保数据库连接有效（如果断开则自动重连）
     * 
     * @return 连接是否有效
     */
    bool ensureConnected();
    
    /**
     * 执行 SQL 查询（内部使用）
     */
    MYSQL* getConnection();

private:
    Database() = default;
    ~Database();
    Database(const Database&) = delete;
    Database& operator=(const Database&) = delete;
    
    MYSQL* mysql_;
    bool connected_;
    
    // 保存连接参数以便重连
    std::string host_;
    std::string user_;
    std::string password_;
    std::string database_;
    unsigned int port_;
    
    /**
     * 转义 SQL 字符串，防止 SQL 注入
     */
    std::string escapeString(const std::string& str);
};

}  // namespace im

#endif  // DATABASE_H

