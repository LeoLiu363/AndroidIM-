#ifndef USER_HANDLER_H
#define USER_HANDLER_H

namespace im {

class EpollServer;

class UserHandler {
public:
    /**
     * 处理用户列表请求
     */
    static void handleUserList(EpollServer& server, int fd);
};

}  // namespace im

#endif  // USER_HANDLER_H

