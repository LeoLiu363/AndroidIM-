-- IM 服务器数据库初始化脚本
-- 数据库名: im_server
-- 字符集: utf8mb4

CREATE DATABASE IF NOT EXISTS im_server DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE im_server;

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    user_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码（加密后）',
    nickname VARCHAR(100) DEFAULT NULL COMMENT '昵称',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_username (username),
    KEY idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 好友关系表（双向各存一条记录）
CREATE TABLE IF NOT EXISTS friends (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    friend_user_id BIGINT UNSIGNED NOT NULL COMMENT '好友用户ID',
    remark VARCHAR(100) DEFAULT NULL COMMENT '好友备注名',
    group_name VARCHAR(50) DEFAULT '默认分组' COMMENT '好友分组',
    is_blocked TINYINT(1) DEFAULT 0 COMMENT '是否拉黑 0:否 1:是',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_friend (user_id, friend_user_id),
    KEY idx_user_id (user_id),
    KEY idx_friend_user_id (friend_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='好友关系表';

-- 好友申请表
CREATE TABLE IF NOT EXISTS friend_applies (
    apply_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '申请ID',
    from_user_id BIGINT UNSIGNED NOT NULL COMMENT '申请发起人',
    to_user_id BIGINT UNSIGNED NOT NULL COMMENT '接收方',
    greeting VARCHAR(255) DEFAULT NULL COMMENT '打招呼内容',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态 0:待处理 1:已同意 2:已拒绝',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    handled_at TIMESTAMP NULL DEFAULT NULL COMMENT '处理时间',
    PRIMARY KEY (apply_id),
    KEY idx_to_user (to_user_id, status),
    KEY idx_from_user (from_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='好友申请表';

-- 群组表
CREATE TABLE IF NOT EXISTS groups (
    group_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '群ID',
    group_name VARCHAR(100) NOT NULL COMMENT '群名称',
    owner_id BIGINT UNSIGNED NOT NULL COMMENT '群主ID',
    avatar_url VARCHAR(500) DEFAULT NULL COMMENT '群头像URL',
    announcement TEXT DEFAULT NULL COMMENT '群公告',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (group_id),
    KEY idx_owner_id (owner_id),
    KEY idx_group_name (group_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='群组表';

-- 群成员表
CREATE TABLE IF NOT EXISTS group_members (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    group_id BIGINT UNSIGNED NOT NULL COMMENT '群ID',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '成员用户ID',
    role ENUM('owner', 'admin', 'member') DEFAULT 'member' COMMENT '角色：owner=群主，admin=管理员，member=普通成员',
    nickname_in_group VARCHAR(100) DEFAULT NULL COMMENT '群内昵称',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_user (group_id, user_id),
    KEY idx_group_id (group_id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='群成员表';

