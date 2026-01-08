-- 群聊功能数据库更新脚本
-- 用于在已有数据库上添加群聊相关表

USE im_server;

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


