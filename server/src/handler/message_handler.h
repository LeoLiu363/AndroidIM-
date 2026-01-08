#ifndef MESSAGE_HANDLER_H
#define MESSAGE_HANDLER_H

#include <string>

namespace im {

class EpollServer;

class MessageHandler {
public:
    /**
     * 处理发送消息
     */
    static void handle(EpollServer& server, int fd, const std::string& jsonData);
};

}  // namespace im

#endif  // MESSAGE_HANDLER_H

