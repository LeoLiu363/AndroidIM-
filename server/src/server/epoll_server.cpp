#include "epoll_server.h"
#include "protocol/encoder.h"
#include "handler/login_handler.h"
#include "handler/message_handler.h"
#include "handler/user_handler.h"
#include "handler/friend_handler.h"
#include "handler/group_handler.h"
#include "utils/logger.h"
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/epoll.h>
#include <cstring>
#include <iostream>
#include <ctime>
#include <vector>
#include <errno.h>

namespace im {

EpollServer::EpollServer(int port) 
    : port_(port), serverFd_(-1), epollFd_(-1), running_(false) {
}

EpollServer::~EpollServer() {
    stop();
}

bool EpollServer::createServerSocket() {
    serverFd_ = socket(AF_INET, SOCK_STREAM, 0);
    if (serverFd_ < 0) {
        Logger::error("创建 Socket 失败: " + std::string(strerror(errno)));
        return false;
    }
    
    // 设置 Socket 选项
    int opt = 1;
    setsockopt(serverFd_, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));
    
    // 设置为非阻塞
    if (!setNonBlocking(serverFd_)) {
        return false;
    }
    
    // 绑定地址
    sockaddr_in addr{};
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = INADDR_ANY;
    addr.sin_port = htons(port_);
    
    if (bind(serverFd_, reinterpret_cast<sockaddr*>(&addr), sizeof(addr)) < 0) {
        Logger::error("绑定地址失败: " + std::string(strerror(errno)) + " (端口: " + std::to_string(port_) + ")");
        return false;
    }
    
    // 监听
    if (listen(serverFd_, 128) < 0) {
        Logger::error("监听失败: " + std::string(strerror(errno)));
        return false;
    }
    
    return true;
}

bool EpollServer::setNonBlocking(int fd) {
    int flags = fcntl(fd, F_GETFL, 0);
    if (flags < 0) {
        return false;
    }
    return fcntl(fd, F_SETFL, flags | O_NONBLOCK) >= 0;
}

bool EpollServer::start() {
    if (!createServerSocket()) {
        return false;
    }
    
    // 创建 epoll
    epollFd_ = epoll_create1(0);
    if (epollFd_ < 0) {
        Logger::error("创建 epoll 失败");
        return false;
    }
    
    // 添加服务器 Socket 到 epoll
    epoll_event ev{};
    ev.events = EPOLLIN | EPOLLET;  // 边缘触发模式
    ev.data.fd = serverFd_;
    if (epoll_ctl(epollFd_, EPOLL_CTL_ADD, serverFd_, &ev) < 0) {
        Logger::error("添加服务器 Socket 到 epoll 失败");
        return false;
    }
    
    running_ = true;
    Logger::info("服务器启动成功，监听端口: " + std::to_string(port_));
    return true;
}

void EpollServer::stop() {
    if (!running_) {
        return;  // 已经停止，避免重复调用
    }
    
    Logger::info("正在停止服务器...");
    running_ = false;
    
    // 先停止线程池，避免新任务提交
    threadPool_.stop();
    Logger::info("线程池已停止");
    
    // 关闭所有客户端连接
    {
        std::lock_guard<std::mutex> lock(clientsMutex_);
        Logger::info("正在关闭 " + std::to_string(clients_.size()) + " 个客户端连接");
        for (auto& [fd, client] : clients_) {
            close(fd);
        }
        clients_.clear();
    }
    
    // 关闭 epoll（这会让 epoll_wait 返回 EBADF，从而退出循环）
    if (epollFd_ >= 0) {
        close(epollFd_);
        epollFd_ = -1;
    }
    
    // 关闭服务器 Socket
    if (serverFd_ >= 0) {
        close(serverFd_);
        serverFd_ = -1;
    }
    
    Logger::info("服务器已完全停止");
}

void EpollServer::run() {
    const int MAX_EVENTS = 64;
    epoll_event events[MAX_EVENTS];
    
    while (running_) {
        // 使用 1000ms 超时，以便定期检查 running_ 状态
        int numEvents = epoll_wait(epollFd_, events, MAX_EVENTS, 1000);
        
        if (numEvents < 0) {
            if (errno == EINTR) {
                // 被信号中断，检查是否需要退出
                if (!running_) {
                    break;
                }
                continue;
            }
            if (errno == EBADF) {
                // epollFd_ 已被关闭，正常退出
                Logger::info("epoll 文件描述符已关闭，退出事件循环");
                break;
            }
            Logger::error("epoll_wait 失败: " + std::string(strerror(errno)));
            break;
        }
        
        // 超时返回 0，检查是否需要退出
        if (numEvents == 0) {
            if (!running_) {
                break;
            }
            continue;
        }
        
        for (int i = 0; i < numEvents; ++i) {
            int fd = events[i].data.fd;
            
            if (fd == serverFd_) {
                // 新连接
                acceptConnection();
            } else {
                // 检查连接是否关闭
                if (events[i].events & (EPOLLRDHUP | EPOLLHUP | EPOLLERR)) {
                    closeConnection(fd);
                } else {
                    // 客户端数据
                    threadPool_.submit([this, fd] {
                        handleClientData(fd);
                    });
                }
            }
        }
    }
}

void EpollServer::acceptConnection() {
    while (true) {
        sockaddr_in clientAddr{};
        socklen_t addrLen = sizeof(clientAddr);
        int clientFd = accept(serverFd_, 
                             reinterpret_cast<sockaddr*>(&clientAddr),
                             &addrLen);
        
        if (clientFd < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                break;  // 没有更多连接
            }
            Logger::error("接受连接失败");
            continue;
        }
        
        // 设置为非阻塞
        setNonBlocking(clientFd);
        
        // 添加到 epoll
        epoll_event ev{};
        ev.events = EPOLLIN | EPOLLET | EPOLLRDHUP;
        ev.data.fd = clientFd;
        if (epoll_ctl(epollFd_, EPOLL_CTL_ADD, clientFd, &ev) < 0) {
            close(clientFd);
            continue;
        }
        
        // 创建客户端连接
        auto client = std::make_unique<ClientConnection>();
        client->fd = clientFd;
        client->authenticated = false;
        
        {
            std::lock_guard<std::mutex> lock(clientsMutex_);
            clients_[clientFd] = std::move(client);
        }
        
        Logger::info("新客户端连接: " + std::string(inet_ntoa(clientAddr.sin_addr)) 
                  + ":" + std::to_string(ntohs(clientAddr.sin_port)));
    }
}

void EpollServer::handleClientData(int fd) {
    std::vector<uint8_t> buffer(4096);
    ssize_t bytesRead = recv(fd, buffer.data(), buffer.size(), 0);
    
    if (bytesRead <= 0) {
        if (bytesRead == 0) {
            // 客户端主动关闭连接
            closeConnection(fd);
        } else if (errno != EAGAIN && errno != EWOULDBLOCK) {
            Logger::warn("读取客户端数据失败: fd=" + std::to_string(fd) +
                         ", bytesRead=" + std::to_string(bytesRead) +
                         ", errno=" + std::to_string(errno) +
                         ", msg=" + std::string(strerror(errno)));
            closeConnection(fd);
        }
        return;
    }
    
    // 记录收到的原始数据（只显示前32字节的十六进制）
    std::string hexDump;
    size_t dumpSize = std::min(static_cast<size_t>(bytesRead), size_t(32));
    for (size_t i = 0; i < dumpSize; ++i) {
        char buf[4];
        snprintf(buf, sizeof(buf), "%02X ", static_cast<unsigned char>(buffer[i]));
        hexDump += buf;
    }
    Logger::info("收到客户端数据: fd=" + std::to_string(fd) +
                 ", bytes=" + std::to_string(bytesRead) +
                 ", 前" + std::to_string(dumpSize) + "字节(hex): " + hexDump);
    std::cout.flush();  // 强制刷新输出
    
    buffer.resize(bytesRead);
    Logger::info("缓冲区已调整大小: " + std::to_string(buffer.size()) + " 字节");
    std::cout.flush();
    
    // 解码消息（需要加锁访问 clients_）
    std::queue<Packet> messagesCopy;
    {
        Logger::info("准备获取客户端连接锁: fd=" + std::to_string(fd));
        std::cout.flush();
        std::lock_guard<std::mutex> lock(clientsMutex_);
        Logger::info("已获取客户端连接锁: fd=" + std::to_string(fd));
        std::cout.flush();
        auto it = clients_.find(fd);
        if (it == clients_.end()) {
            Logger::warn("收到数据但客户端连接不存在: fd=" + std::to_string(fd));
            return;
        }
        
        auto& client = it->second;
        Logger::info("调用解码器: fd=" + std::to_string(fd) + ", 缓冲区大小=" + std::to_string(buffer.size()));
        auto messages = client->decoder.addData(buffer);
        Logger::info("解码器返回: fd=" + std::to_string(fd) + ", 解码出消息数=" + std::to_string(messages.size()));
        
        // 复制消息队列，准备释放锁
        messagesCopy = messages;
        // lock 在这里自动释放（作用域结束）
    }
    Logger::info("锁已释放，准备处理消息: fd=" + std::to_string(fd));
    std::cout.flush();
    
    if (messagesCopy.empty()) {
        Logger::warn("⚠ 收到数据但未解码出任何消息: fd=" + std::to_string(fd) + 
                     ", bytes=" + std::to_string(bytesRead) +
                     ", 这可能表示数据格式错误或数据不完整");
    } else {
        Logger::info("✓ 成功解码出 " + std::to_string(messagesCopy.size()) + " 条消息，开始处理");
    }
    
    while (!messagesCopy.empty()) {
        Logger::info("处理消息队列中的一条消息: fd=" + std::to_string(fd));
        std::cout.flush();
        processMessage(fd, messagesCopy.front());
        messagesCopy.pop();
    }
    Logger::info("消息处理完成: fd=" + std::to_string(fd));
}

void EpollServer::processMessage(int fd, const Packet& packet) {
    uint16_t msgType = static_cast<uint16_t>(packet.type);
    
    // 区分心跳和业务请求的日志级别
    bool isHeartbeat = (msgType == static_cast<uint16_t>(MessageType::HEARTBEAT) ||
                        msgType == static_cast<uint16_t>(MessageType::HEARTBEAT_RESPONSE));
    
    Logger::info("[processMessage] 开始处理消息: fd=" + std::to_string(fd) + 
                 ", type=" + std::to_string(msgType));
    std::cout.flush();
    
    if (isHeartbeat) {
        // 心跳包也使用info级别以便排查问题
        Logger::info("[processMessage] 处理心跳: fd=" + std::to_string(fd) + ", type=" + std::to_string(msgType));
    } else {
        // 业务请求记录info级别，便于排查问题
        Logger::info("[processMessage] 处理业务消息: fd=" + std::to_string(fd) +
                     ", type=" + std::to_string(msgType) +
                     ", data_length=" + std::to_string(packet.data.length()) +
                     ", data=" + packet.data.substr(0, 100));  // 只显示前100字符
    }
    std::cout.flush();
    
    // 先检查客户端是否存在，然后快速释放锁
    Logger::info("[processMessage] 准备获取锁检查客户端: fd=" + std::to_string(fd));
    std::cout.flush();
    bool clientExists = false;
    bool authenticated = false;
    {
        std::lock_guard<std::mutex> lock(clientsMutex_);
        Logger::info("[processMessage] 已获取锁，查找客户端: fd=" + std::to_string(fd));
        std::cout.flush();
        auto it = clients_.find(fd);
        if (it == clients_.end()) {
            Logger::warn("处理消息时客户端连接不存在: fd=" + std::to_string(fd));
            return;
        }
        clientExists = true;
        authenticated = it->second->authenticated;
        Logger::info("[processMessage] 找到客户端，authenticated=" + std::string(authenticated ? "true" : "false"));
        std::cout.flush();
    }
    // 锁在这里释放，避免在发送消息时持有锁
    Logger::info("[processMessage] 锁已释放，开始处理消息逻辑: fd=" + std::to_string(fd));
    std::cout.flush();
    
    switch (msgType) {
        case static_cast<uint16_t>(MessageType::LOGIN_REQUEST):
            Logger::info(">>> 处理登录请求: fd=" + std::to_string(fd) + ", data=" + packet.data);
            LoginHandler::handle(*this, fd, packet.data);
            break;
        case static_cast<uint16_t>(MessageType::REGISTER_REQUEST):
            Logger::info(">>> 处理注册请求: fd=" + std::to_string(fd) + ", data=" + packet.data);
            LoginHandler::handleRegister(*this, fd, packet.data);
            break;
        case static_cast<uint16_t>(MessageType::SEND_MESSAGE):
            if (authenticated) {
                MessageHandler::handle(*this, fd, packet.data);
            } else {
                sendMessage(fd, MessageType::ERROR, 
                           R"({"error_code":1001,"error_message":"请先登录"})");
            }
            break;
        case static_cast<uint16_t>(MessageType::HEARTBEAT): {
            // 心跳包处理（使用info级别以便排查问题）
            Logger::info("[心跳处理] >>> 收到心跳请求: fd=" + std::to_string(fd));
            std::cout.flush();
            std::string heartbeatResponse = R"({"timestamp":)" + std::to_string(time(nullptr)) + "}";
            Logger::info("[心跳处理] 准备发送心跳响应: fd=" + std::to_string(fd) + ", json=" + heartbeatResponse);
            std::cout.flush();
            sendMessage(fd, MessageType::HEARTBEAT_RESPONSE, heartbeatResponse);
            Logger::info("[心跳处理] <<< 心跳响应发送完成: fd=" + std::to_string(fd));
            std::cout.flush();
            break;
        }
        case static_cast<uint16_t>(MessageType::FRIEND_APPLY_REQUEST):
            if (authenticated) {
                FriendHandler::handleApply(*this, fd, packet.data);
            } else {
                sendMessage(fd, MessageType::ERROR,
                           R"({"error_code":1001,"error_message":"请先登录"})");
            }
            break;
        case static_cast<uint16_t>(MessageType::FRIEND_HANDLE_REQUEST):
            if (authenticated) {
                FriendHandler::handleApplyAction(*this, fd, packet.data);
            } else {
                sendMessage(fd, MessageType::ERROR,
                           R"({"error_code":1001,"error_message":"请先登录"})");
            }
            break;
        case static_cast<uint16_t>(MessageType::FRIEND_LIST_REQUEST):
            if (authenticated) {
                FriendHandler::handleFriendList(*this, fd, packet.data);
            } else {
                sendMessage(fd, MessageType::ERROR,
                           R"({"error_code":1001,"error_message":"请先登录"})");
            }
            break;
        case static_cast<uint16_t>(MessageType::FRIEND_DELETE_REQUEST):
            if (authenticated) {
                FriendHandler::handleDelete(*this, fd, packet.data);
            } else {
                sendMessage(fd, MessageType::ERROR,
                           R"({"error_code":1001,"error_message":"请先登录"})");
            }
            break;
        case static_cast<uint16_t>(MessageType::FRIEND_BLOCK_REQUEST):
            if (authenticated) {
                FriendHandler::handleBlock(*this, fd, packet.data);
            } else {
                sendMessage(fd, MessageType::ERROR,
                           R"({"error_code":1001,"error_message":"请先登录"})");
            }
            break;
        case static_cast<uint16_t>(MessageType::GROUP_CREATE_REQUEST):
            if (authenticated) {
                GroupHandler::handleCreate(*this, fd, packet.data);
            } else {
                sendMessage(fd, MessageType::ERROR,
                           R"({"error_code":1001,"error_message":"请先登录"})");
            }
            break;
        case static_cast<uint16_t>(MessageType::GROUP_LIST_REQUEST):
            if (authenticated) {
                GroupHandler::handleGroupList(*this, fd, packet.data);
            } else {
                sendMessage(fd, MessageType::ERROR,
                           R"({"error_code":1001,"error_message":"请先登录"})");
            }
            break;
        case static_cast<uint16_t>(MessageType::GROUP_MEMBER_LIST_REQUEST):
            if (authenticated) {
                GroupHandler::handleMemberList(*this, fd, packet.data);
            } else {
                sendMessage(fd, MessageType::ERROR,
                           R"({"error_code":1001,"error_message":"请先登录"})");
            }
            break;
        case static_cast<uint16_t>(MessageType::GROUP_INVITE_REQUEST):
            if (authenticated) {
                GroupHandler::handleInvite(*this, fd, packet.data);
            } else {
                sendMessage(fd, MessageType::ERROR,
                           R"({"error_code":1001,"error_message":"请先登录"})");
            }
            break;
        case static_cast<uint16_t>(MessageType::GROUP_KICK_REQUEST):
            if (authenticated) {
                GroupHandler::handleKick(*this, fd, packet.data);
            } else {
                sendMessage(fd, MessageType::ERROR,
                           R"({"error_code":1001,"error_message":"请先登录"})");
            }
            break;
        case static_cast<uint16_t>(MessageType::GROUP_QUIT_REQUEST):
            if (authenticated) {
                GroupHandler::handleQuit(*this, fd, packet.data);
            } else {
                sendMessage(fd, MessageType::ERROR,
                           R"({"error_code":1001,"error_message":"请先登录"})");
            }
            break;
        case static_cast<uint16_t>(MessageType::GROUP_DISMISS_REQUEST):
            if (authenticated) {
                GroupHandler::handleDismiss(*this, fd, packet.data);
            } else {
                sendMessage(fd, MessageType::ERROR,
                           R"({"error_code":1001,"error_message":"请先登录"})");
            }
            break;
        case static_cast<uint16_t>(MessageType::GROUP_UPDATE_INFO_REQUEST):
            if (authenticated) {
                GroupHandler::handleUpdateInfo(*this, fd, packet.data);
            } else {
                sendMessage(fd, MessageType::ERROR,
                           R"({"error_code":1001,"error_message":"请先登录"})");
            }
            break;
        case static_cast<uint16_t>(MessageType::USER_LIST_REQUEST):
            if (authenticated) {
                UserHandler::handleUserList(*this, fd);
            } else {
                sendMessage(fd, MessageType::ERROR, 
                           R"({"error_code":1001,"error_message":"请先登录"})");
            }
            break;
        case static_cast<uint16_t>(MessageType::LOGOUT):
            closeConnection(fd);
            break;
        default:
            Logger::warn("未知消息类型: " + std::to_string(static_cast<uint16_t>(packet.type)));
            break;
    }
}

void EpollServer::sendMessage(int fd, MessageType type, const std::string& jsonData) {
    uint16_t msgType = static_cast<uint16_t>(type);
    bool isHeartbeat = (msgType == static_cast<uint16_t>(MessageType::HEARTBEAT_RESPONSE));
    
    // 先检查客户端连接是否存在
    {
        std::lock_guard<std::mutex> lock(clientsMutex_);
        auto it = clients_.find(fd);
        if (it == clients_.end()) {
            Logger::error("[发送消息] ✗ 客户端连接不存在: fd=" + std::to_string(fd) +
                         ", type=" + std::to_string(msgType) +
                         ", 无法发送消息");
            return;
        }
    }
    
    Logger::info("[发送消息] 开始编码: fd=" + std::to_string(fd) +
                 ", type=" + std::to_string(msgType) +
                 ", json_length=" + std::to_string(jsonData.length()) +
                 ", json=" + jsonData);
    std::cout.flush();
    
    auto packet = MessageEncoder::encode(type, jsonData);
    
    Logger::info("[发送消息] 编码完成: fd=" + std::to_string(fd) +
                 ", packet_size=" + std::to_string(packet.size()) +
                 " (头部10字节 + 数据" + std::to_string(jsonData.length()) + "字节)");
    std::cout.flush();
    
    // 输出前16字节的十六进制，便于调试
    std::string hexDump;
    size_t dumpSize = std::min(packet.size(), size_t(16));
    for (size_t i = 0; i < dumpSize; ++i) {
        char buf[4];
        snprintf(buf, sizeof(buf), "%02X ", static_cast<unsigned char>(packet[i]));
        hexDump += buf;
    }
    Logger::info("[发送消息] 数据包前" + std::to_string(dumpSize) + "字节(hex): " + hexDump);
    std::cout.flush();
    
    ssize_t sent = send(fd, packet.data(), packet.size(), 0);
    
    if (sent < 0) {
        Logger::error("[发送消息] ✗ 发送失败: fd=" + std::to_string(fd) + 
                     ", type=" + std::to_string(msgType) +
                     ", errno=" + std::to_string(errno) +
                     ", msg=" + std::string(strerror(errno)) +
                     ", 期望发送=" + std::to_string(packet.size()) + "字节");
        std::cout.flush();
        
        // 发送失败时，如果是连接错误，关闭连接
        if (errno == EPIPE || errno == ECONNRESET || errno == EBADF) {
            Logger::warn("[发送消息] 检测到连接错误，关闭连接: fd=" + std::to_string(fd));
            closeConnection(fd);
        }
    } else if (sent != static_cast<ssize_t>(packet.size())) {
        Logger::warn("[发送消息] ⚠ 部分发送: fd=" + std::to_string(fd) +
                     ", type=" + std::to_string(msgType) +
                     ", 期望=" + std::to_string(packet.size()) +
                     "字节, 实际=" + std::to_string(sent) + "字节");
        std::cout.flush();
        // 部分发送时，尝试发送剩余数据
        if (sent > 0 && sent < static_cast<ssize_t>(packet.size())) {
            ssize_t remaining = packet.size() - sent;
            ssize_t retrySent = send(fd, packet.data() + sent, remaining, 0);
            if (retrySent < 0) {
                Logger::error("[发送消息] ✗ 重试发送失败: fd=" + std::to_string(fd) +
                             ", errno=" + std::to_string(errno));
                if (errno == EPIPE || errno == ECONNRESET || errno == EBADF) {
                    closeConnection(fd);
                }
            } else if (retrySent == remaining) {
                Logger::info("[发送消息] ✓ 重试发送成功: fd=" + std::to_string(fd) +
                             ", 补发=" + std::to_string(retrySent) + "字节");
            } else {
                Logger::warn("[发送消息] ⚠ 重试仍部分发送: fd=" + std::to_string(fd) +
                             ", 期望=" + std::to_string(remaining) +
                             "字节, 实际=" + std::to_string(retrySent) + "字节");
            }
        }
    } else {
        // 心跳响应使用debug级别，其他消息使用info级别
        if (isHeartbeat) {
            Logger::debug("[发送消息] ✓ 心跳响应已发送: fd=" + std::to_string(fd) + 
                         ", bytes=" + std::to_string(sent));
        } else {
            Logger::info("[发送消息] ✓ 消息发送成功: fd=" + std::to_string(fd) +
                         ", type=" + std::to_string(msgType) +
                         ", bytes=" + std::to_string(sent) +
                         ", json=" + jsonData);
        }
        std::cout.flush();
    }
}

void EpollServer::setClientAuthenticated(int fd, const std::string& userId, const std::string& username) {
    std::lock_guard<std::mutex> lock(clientsMutex_);
    auto it = clients_.find(fd);
    if (it != clients_.end()) {
        it->second->authenticated = true;
        it->second->userId = userId;
        it->second->username = username.empty() ? userId : username;
        Logger::info("客户端认证成功: fd=" + std::to_string(fd) + ", userId=" + userId);
    }
}

std::unique_ptr<ClientInfo> EpollServer::getClientInfo(int fd) {
    std::lock_guard<std::mutex> lock(clientsMutex_);
    auto it = clients_.find(fd);
    if (it != clients_.end() && it->second->authenticated) {
        auto info = std::make_unique<ClientInfo>();
        info->userId = it->second->userId;
        info->username = it->second->username;
        info->authenticated = it->second->authenticated;
        return info;
    }
    return nullptr;
}

void EpollServer::sendMessageToUser(const std::string& userId, MessageType type, const std::string& jsonData) {
    // 先找到目标用户的 fd，然后释放锁再发送消息（避免死锁）
    int targetFd = -1;
    {
        std::lock_guard<std::mutex> lock(clientsMutex_);
        for (auto& [fd, client] : clients_) {
            if (client->authenticated && client->userId == userId) {
                targetFd = fd;
                break;
            }
        }
    }
    
    if (targetFd >= 0) {
        sendMessage(targetFd, type, jsonData);
        Logger::info("[转发消息] 发送给用户: userId=" + userId + ", fd=" + std::to_string(targetFd));
    } else {
        Logger::warn("[转发消息] ✗ 用户不在线: userId=" + userId);
    }
}

void EpollServer::broadcastMessage(MessageType type, const std::string& jsonData, int excludeFd) {
    // 先收集所有目标 fd，然后释放锁再发送消息（避免死锁）
    std::vector<int> targetFds;
    {
        std::lock_guard<std::mutex> lock(clientsMutex_);
        for (auto& [fd, client] : clients_) {
            if (client->authenticated && fd != excludeFd) {
                targetFds.push_back(fd);
            }
        }
    }
    
    // 在锁外发送消息
    for (int fd : targetFds) {
        sendMessage(fd, type, jsonData);
    }
    
    Logger::info("[广播消息] 发送给 " + std::to_string(targetFds.size()) + " 个用户" +
                 (excludeFd >= 0 ? " (排除 fd=" + std::to_string(excludeFd) + ")" : ""));
}

std::vector<std::string> EpollServer::getOnlineUsers() {
    std::vector<std::string> users;
    std::lock_guard<std::mutex> lock(clientsMutex_);
    for (auto& [fd, client] : clients_) {
        if (client->authenticated) {
            users.push_back(client->userId);
        }
    }
    return users;
}

std::vector<std::pair<std::string, std::string>> EpollServer::getOnlineUsersWithInfo() {
    std::vector<std::pair<std::string, std::string>> users;
    std::lock_guard<std::mutex> lock(clientsMutex_);
    for (auto& [fd, client] : clients_) {
        if (client->authenticated) {
            users.push_back({client->userId, client->username});
        }
    }
    return users;
}

void EpollServer::closeConnection(int fd) {
    std::lock_guard<std::mutex> lock(clientsMutex_);
    auto it = clients_.find(fd);
    if (it != clients_.end()) {
        std::string userId = it->second->userId;
        std::string username = it->second->username;
        bool authenticated = it->second->authenticated;
        
        // 先删除连接记录，避免重复处理
        clients_.erase(it);
        
        if (authenticated && !userId.empty()) {
            // 已登录用户断开，记录 info 级别日志
            Logger::info("客户端断开连接: fd=" + std::to_string(fd) + 
                        ", userId=" + userId + 
                        ", username=" + username);
        } else {
            // 未登录连接断开，使用 debug 级别，减少日志量
            Logger::debug("客户端断开连接: fd=" + std::to_string(fd) + 
                         " (未登录)");
        }
    } else {
        // 连接记录已不存在，可能是重复调用，不记录日志避免重复
        // 但仍然需要清理 epoll 和关闭 fd
    }
    
    // 从 epoll 中移除（如果还在的话）
    if (epollFd_ >= 0) {
        epoll_ctl(epollFd_, EPOLL_CTL_DEL, fd, nullptr);
    }
    
    // 关闭文件描述符
    if (fd >= 0) {
    close(fd);
    }
}

}  // namespace im

