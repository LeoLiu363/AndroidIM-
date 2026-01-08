#ifndef EPOLL_SERVER_H
#define EPOLL_SERVER_H

#include <map>
#include <memory>
#include <mutex>
#include <string>
#include <utility>
#include <vector>
#include "protocol/decoder.h"
#include "protocol/message.h"
#include "thread_pool/thread_pool.h"

namespace im {

// 前向声明
class EpollServer;

// 客户端信息结构
struct ClientInfo {
    std::string userId;
    std::string username;
    bool authenticated;
};

class EpollServer {
public:
    explicit EpollServer(int port = 8888);
    ~EpollServer();
    
    /**
     * 启动服务器
     */
    bool start();
    
    /**
     * 停止服务器
     */
    void stop();
    
    /**
     * 运行事件循环
     */
    void run();
    
    /**
     * 设置客户端认证状态
     */
    void setClientAuthenticated(int fd, const std::string& userId, const std::string& username = "");
    
    /**
     * 获取客户端信息
     */
    std::unique_ptr<ClientInfo> getClientInfo(int fd);
    
    /**
     * 发送消息给客户端
     */
    void sendMessage(int fd, MessageType type, const std::string& jsonData);
    
    /**
     * 发送消息给指定用户
     */
    void sendMessageToUser(const std::string& userId, MessageType type, const std::string& jsonData);
    
    /**
     * 广播消息（排除发送者）
     */
    void broadcastMessage(MessageType type, const std::string& jsonData, int excludeFd = -1);
    
    /**
     * 获取所有在线用户ID
     */
    std::vector<std::string> getOnlineUsers();
    
    /**
     * 获取所有在线用户的完整信息（userId, username）
     */
    std::vector<std::pair<std::string, std::string>> getOnlineUsersWithInfo();

private:
    int port_;
    int serverFd_;
    int epollFd_;
    bool running_;
    
    ThreadPool threadPool_;
    
    // 客户端连接管理
    struct ClientConnection {
        int fd;
        MessageDecoder decoder;
        std::string userId;
        std::string username;
        bool authenticated;
    };
    
    std::map<int, std::unique_ptr<ClientConnection>> clients_;
    std::mutex clientsMutex_;
    
    /**
     * 创建服务器 Socket
     */
    bool createServerSocket();
    
    /**
     * 设置 Socket 为非阻塞
     */
    bool setNonBlocking(int fd);
    
    /**
     * 接受新连接
     */
    void acceptConnection();
    
    /**
     * 处理客户端数据
     */
    void handleClientData(int fd);
    
    /**
     * 关闭客户端连接
     */
    void closeConnection(int fd);
    
    /**
     * 处理消息
     */
    void processMessage(int fd, const Packet& packet);
};

}  // namespace im

#endif  // EPOLL_SERVER_H

