#include "server/epoll_server.h"
#include "database/database.h"
#include "utils/logger.h"
#include <iostream>
#include <signal.h>
#include <unistd.h>
#include <atomic>
#include <cstdlib>

im::EpollServer* g_server = nullptr;
std::atomic<bool> g_shutdown(false);

void signalHandler(int sig) {
    if (g_shutdown.exchange(true)) {
        // 第二次收到信号，强制退出
        im::Logger::warn("收到第二次信号，强制退出");
        _exit(1);
    }
    
    if (g_server) {
        im::Logger::info("收到信号 " + std::to_string(sig) + "，正在关闭服务器...");
        g_server->stop();
    }
}

int main(int argc, char* argv[]) {
    int port = 8888;  // 默认端口
    if (argc > 1) {
        port = std::stoi(argv[1]);
    }
    
    // 初始化数据库连接
    // 从环境变量读取数据库配置，如果没有则使用默认值
    const char* dbHost = std::getenv("DB_HOST");
    const char* dbUser = std::getenv("DB_USER");
    const char* dbPassword = std::getenv("DB_PASSWORD");
    const char* dbName = std::getenv("DB_NAME");
    const char* dbPort = std::getenv("DB_PORT");
    
    // 默认值：使用 127.0.0.1 而不是 localhost，确保使用 TCP 连接
    std::string host = dbHost ? dbHost : "127.0.0.1";
    std::string user = dbUser ? dbUser : "root";
    std::string password = dbPassword ? dbPassword : "";  // 请通过环境变量设置
    std::string database = dbName ? dbName : "im_server";
    unsigned int dbPortNum = dbPort ? std::stoi(dbPort) : 3306;
    
    im::Database& db = im::Database::getInstance();
    if (!db.init(host, user, password, database, dbPortNum)) {
        im::Logger::error("数据库初始化失败，服务器无法启动");
        im::Logger::info("提示: 请设置环境变量 DB_HOST, DB_USER, DB_PASSWORD, DB_NAME");
        im::Logger::info("或确保 MySQL 服务运行在 localhost:3306，数据库名为 im_server");
        return 1;
    }
    
    im::EpollServer server(port);
    g_server = &server;
    
    // 注册信号处理
    signal(SIGINT, signalHandler);
    signal(SIGTERM, signalHandler);
    
    if (!server.start()) {
        im::Logger::error("服务器启动失败");
        db.close();
        return 1;
    }
    
    im::Logger::info("IM 服务器运行中，按 Ctrl+C 停止");
    server.run();
    
    // 清理资源
    db.close();
    
    return 0;
}

