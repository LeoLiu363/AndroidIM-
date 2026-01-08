#ifndef FRIEND_HANDLER_H
#define FRIEND_HANDLER_H

#include <string>

namespace im {

class EpollServer;

class FriendHandler {
public:
    /**
     * 处理发送好友申请
     */
    static void handleApply(EpollServer& server, int fd, const std::string& jsonData);

    /**
     * 处理好友申请的同意 / 拒绝
     */
    static void handleApplyAction(EpollServer& server, int fd, const std::string& jsonData);

    /**
     * 获取好友列表
     */
    static void handleFriendList(EpollServer& server, int fd, const std::string& jsonData);

    /**
     * 删除好友
     */
    static void handleDelete(EpollServer& server, int fd, const std::string& jsonData);

    /**
     * 拉黑 / 取消拉黑好友
     */
    static void handleBlock(EpollServer& server, int fd, const std::string& jsonData);
};

}  // namespace im

#endif  // FRIEND_HANDLER_H


