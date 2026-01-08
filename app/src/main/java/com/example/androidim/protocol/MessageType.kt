package com.example.androidim.protocol

/**
 * 消息类型定义
 * 对应协议文档中的消息类型
 */
object MessageType {
    const val LOGIN_REQUEST: Short = 0x0001
    const val LOGIN_RESPONSE: Short = 0x0002
    const val REGISTER_REQUEST: Short = 0x0003
    const val REGISTER_RESPONSE: Short = 0x0004
    const val SEND_MESSAGE: Short = 0x0005
    const val RECEIVE_MESSAGE: Short = 0x0006
    const val HEARTBEAT: Short = 0x0007
    const val HEARTBEAT_RESPONSE: Short = 0x0008
    const val USER_LIST_REQUEST: Short = 0x0009
    const val USER_LIST_RESPONSE: Short = 0x000A
    const val LOGOUT: Short = 0x000B
    const val ERROR: Short = 0x000C

    // 好友相关消息类型（数值需与服务端保持一致）
    // 按服务端约定：0x0100 为申请请求，0x0101 为申请结果
    const val FRIEND_APPLY_REQUEST: Short = 0x0100
    const val FRIEND_APPLY_RESPONSE: Short = 0x0101
    const val FRIEND_APPLY_NOTIFY: Short = 0x0102

    const val FRIEND_HANDLE_REQUEST: Short = 0x0103
    const val FRIEND_HANDLE_RESPONSE: Short = 0x0104
    const val FRIEND_HANDLE_NOTIFY: Short = 0x0105

    const val FRIEND_LIST_REQUEST: Short = 0x0106
    const val FRIEND_LIST_RESPONSE: Short = 0x0107

    const val FRIEND_DELETE_REQUEST: Short = 0x0108
    const val FRIEND_DELETE_RESPONSE: Short = 0x0109

    const val FRIEND_BLOCK_REQUEST: Short = 0x010A
    const val FRIEND_BLOCK_RESPONSE: Short = 0x010B
    
    // 群聊相关消息类型
    const val GROUP_CREATE_REQUEST: Short = 0x0200
    const val GROUP_CREATE_RESPONSE: Short = 0x0201
    
    const val GROUP_LIST_REQUEST: Short = 0x0202
    const val GROUP_LIST_RESPONSE: Short = 0x0203
    
    const val GROUP_MEMBER_LIST_REQUEST: Short = 0x0204
    const val GROUP_MEMBER_LIST_RESPONSE: Short = 0x0205
    
    // 邀请成员
    const val GROUP_INVITE_REQUEST: Short = 0x0206  // 518 - 邀请成员请求（客户端发送）
    const val GROUP_INVITE_RESPONSE: Short = 0x0207  // 519 - 邀请成员响应（服务端返回）
    const val GROUP_INVITE_NOTIFY: Short = 0x0208   // 520 - 邀请成员通知（服务端推送，给被邀请人）
    
    // 踢人
    const val GROUP_KICK_REQUEST: Short = 0x0209    // 521 - 踢人请求（客户端发送）
    const val GROUP_KICK_RESPONSE: Short = 0x020A   // 522 - 踢人响应（服务端返回）
    const val GROUP_KICK_NOTIFY: Short = 0x020B     // 523 - 踢人通知（服务端推送，给被踢人）
    
    // 退群
    const val GROUP_QUIT_REQUEST: Short = 0x020C    // 524 - 退群请求（客户端发送）
    const val GROUP_QUIT_RESPONSE: Short = 0x020D  // 525 - 退群响应（服务端返回）
    const val GROUP_QUIT_NOTIFY: Short = 0x020E    // 526 - 退群通知（服务端推送，给群成员）
    
    // 解散群
    const val GROUP_DISMISS_REQUEST: Short = 0x020F  // 527 - 解散群请求（客户端发送）
    const val GROUP_DISMISS_RESPONSE: Short = 0x0210 // 528 - 解散群响应（服务端返回）
    const val GROUP_DISMISS_NOTIFY: Short = 0x0211   // 529 - 解散群通知（服务端推送，给群成员）
    
    // 更新群信息
    const val GROUP_UPDATE_INFO_REQUEST: Short = 0x0212  // 530 - 更新群信息请求（客户端发送）
    const val GROUP_UPDATE_INFO_RESPONSE: Short = 0x0213 // 531 - 更新群信息响应（服务端返回）
    const val GROUP_UPDATE_INFO_NOTIFY: Short = 0x0214    // 532 - 更新群信息通知（服务端推送，给群成员）
    
    // 群聊消息接收（复用 RECEIVE_MESSAGE，通过 conversation_type 区分）
    const val GROUP_MESSAGE_RECEIVE: Short = 0x0006  // 复用 RECEIVE_MESSAGE
    
    const val MAGIC: Int = 0x494D494D  // "IMIM"
}



