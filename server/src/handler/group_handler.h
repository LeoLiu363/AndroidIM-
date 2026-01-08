#ifndef GROUP_HANDLER_H
#define GROUP_HANDLER_H

#include <string>

namespace im {

class EpollServer;

class GroupHandler {
public:
    /**
     * 处理创建群请求
     */
    static void handleCreate(EpollServer& server, int fd, const std::string& jsonData);

    /**
     * 处理获取群列表请求
     */
    static void handleGroupList(EpollServer& server, int fd, const std::string& jsonData);

    /**
     * 处理获取群成员列表请求
     */
    static void handleMemberList(EpollServer& server, int fd, const std::string& jsonData);

    /**
     * 处理邀请成员入群请求
     */
    static void handleInvite(EpollServer& server, int fd, const std::string& jsonData);

    /**
     * 处理踢人请求
     */
    static void handleKick(EpollServer& server, int fd, const std::string& jsonData);

    /**
     * 处理退群请求
     */
    static void handleQuit(EpollServer& server, int fd, const std::string& jsonData);

    /**
     * 处理解散群请求
     */
    static void handleDismiss(EpollServer& server, int fd, const std::string& jsonData);

    /**
     * 处理更新群信息请求
     */
    static void handleUpdateInfo(EpollServer& server, int fd, const std::string& jsonData);
};

}  // namespace im

#endif  // GROUP_HANDLER_H


