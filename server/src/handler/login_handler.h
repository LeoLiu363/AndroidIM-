#ifndef LOGIN_HANDLER_H
#define LOGIN_HANDLER_H

#include <string>

namespace im {

class EpollServer;

class LoginHandler {
public:
    /**
     * 处理登录请求
     */
    static void handle(EpollServer& server, int fd, const std::string& jsonData);
    
    /**
     * 处理注册请求
     */
    static void handleRegister(EpollServer& server, int fd, const std::string& jsonData);
};

}  // namespace im

#endif  // LOGIN_HANDLER_H

