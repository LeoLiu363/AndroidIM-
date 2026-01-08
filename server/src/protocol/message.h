#ifndef MESSAGE_H
#define MESSAGE_H

#include <cstdint>
#include <string>

namespace im {

// 消息类型
enum class MessageType : uint16_t {
    LOGIN_REQUEST = 0x0001,
    LOGIN_RESPONSE = 0x0002,
    REGISTER_REQUEST = 0x0003,
    REGISTER_RESPONSE = 0x0004,
    SEND_MESSAGE = 0x0005,
    RECEIVE_MESSAGE = 0x0006,
    HEARTBEAT = 0x0007,
    HEARTBEAT_RESPONSE = 0x0008,
    USER_LIST_REQUEST = 0x0009,
    USER_LIST_RESPONSE = 0x000A,
    LOGOUT = 0x000B,
    ERROR = 0x000C,

    // 好友相关
    FRIEND_APPLY_REQUEST   = 0x0100,  // 发送好友申请
    FRIEND_APPLY_RESPONSE  = 0x0101,  // 好友申请发送结果（给申请发起方）
    FRIEND_APPLY_NOTIFY    = 0x0102,  // 好友申请通知（给被申请方）

    FRIEND_HANDLE_REQUEST  = 0x0103,  // 处理好友申请（同意 / 拒绝）
    FRIEND_HANDLE_RESPONSE = 0x0104,  // 处理结果（给处理方）
    FRIEND_HANDLE_NOTIFY   = 0x0105,  // 处理结果通知（给申请发起方）

    FRIEND_LIST_REQUEST    = 0x0106,  // 获取好友列表
    FRIEND_LIST_RESPONSE   = 0x0107,  // 好友列表

    FRIEND_DELETE_REQUEST  = 0x0108,  // 删除好友
    FRIEND_DELETE_RESPONSE = 0x0109,  // 删除好友结果

    FRIEND_BLOCK_REQUEST   = 0x010A,  // 拉黑 / 取消拉黑
    FRIEND_BLOCK_RESPONSE  = 0x010B,  // 拉黑结果

    // 群聊相关
    GROUP_CREATE_REQUEST   = 0x0200,  // 创建群
    GROUP_CREATE_RESPONSE  = 0x0201,  // 创建群结果

    GROUP_LIST_REQUEST     = 0x0202,  // 获取群列表
    GROUP_LIST_RESPONSE    = 0x0203,  // 群列表

    GROUP_MEMBER_LIST_REQUEST  = 0x0204,  // 获取群成员列表
    GROUP_MEMBER_LIST_RESPONSE = 0x0205,  // 群成员列表

    GROUP_INVITE_REQUEST   = 0x0206,  // 邀请成员入群
    GROUP_INVITE_RESPONSE  = 0x0207,  // 邀请结果
    GROUP_INVITE_NOTIFY    = 0x0208,  // 邀请通知（给被邀请人）

    GROUP_KICK_REQUEST     = 0x0209,  // 踢人
    GROUP_KICK_RESPONSE    = 0x020A,  // 踢人结果
    GROUP_KICK_NOTIFY      = 0x020B,  // 踢人通知（给被踢人）

    GROUP_QUIT_REQUEST     = 0x020C,  // 退群
    GROUP_QUIT_RESPONSE    = 0x020D,  // 退群结果
    GROUP_QUIT_NOTIFY      = 0x020E,  // 退群通知（给群成员）

    GROUP_DISMISS_REQUEST  = 0x020F,  // 解散群（仅群主）
    GROUP_DISMISS_RESPONSE = 0x0210,  // 解散群结果
    GROUP_DISMISS_NOTIFY   = 0x0211,  // 解散群通知（给群成员）

    GROUP_UPDATE_INFO_REQUEST  = 0x0212,  // 更新群信息（群名/公告等）
    GROUP_UPDATE_INFO_RESPONSE = 0x0213,  // 更新群信息结果
    GROUP_UPDATE_INFO_NOTIFY   = 0x0214   // 更新群信息通知
};

// 协议常量
constexpr uint32_t MAGIC = 0x494D494D;  // "IMIM"

// 数据包结构
struct Packet {
    uint32_t magic;
    MessageType type;
    uint32_t length;
    std::string data;
};

}  // namespace im

#endif  // MESSAGE_H

