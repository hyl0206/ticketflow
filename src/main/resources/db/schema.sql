-- TicketFlow 数据库初始化脚本
-- Day 2：核心数据模型与持久层

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS ticketflow
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE ticketflow;

-- =====================================================
-- 1. 用户表
-- =====================================================
CREATE TABLE IF NOT EXISTS `sys_user` (
                                          `id` bigint NOT NULL AUTO_INCREMENT,
                                          `username` varchar(50) NOT NULL,
    `password` varchar(255) NOT NULL,
    `email` varchar(100) DEFAULT NULL,
    `status` tinyint NOT NULL DEFAULT '1',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_0900_ai_ci;


-- =====================================================
-- 2. 角色表
-- =====================================================
CREATE TABLE IF NOT EXISTS `sys_role` (
                                          `id` bigint NOT NULL AUTO_INCREMENT,
                                          `role_code` varchar(50) NOT NULL,
    `role_name` varchar(50) NOT NULL,
    `description` varchar(255) DEFAULT NULL,
    `status` tinyint NOT NULL DEFAULT '1',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`)
    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_0900_ai_ci;


-- =====================================================
-- 3. 用户-角色关联表
-- =====================================================
CREATE TABLE IF NOT EXISTS `sys_user_role` (
                                               `id` bigint NOT NULL AUTO_INCREMENT,
                                               `user_id` bigint NOT NULL,
                                               `role_id` bigint NOT NULL,
                                               `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                               PRIMARY KEY (`id`),

    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),

    KEY `idx_user_id` (`user_id`),
    KEY `idx_role_id` (`role_id`)
    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_0900_ai_ci;


-- =====================================================
-- 4. 工单表
-- =====================================================
CREATE TABLE IF NOT EXISTS `ticket` (
                                        `id` bigint NOT NULL AUTO_INCREMENT,
                                        `ticket_no` varchar(32) NOT NULL,
    `title` varchar(200) NOT NULL,
    `description` text NOT NULL,
    `status` varchar(20) NOT NULL DEFAULT 'OPEN',
    `priority` varchar(20) NOT NULL DEFAULT 'MEDIUM',
    `creator_id` bigint NOT NULL,
    `assignee_id` bigint DEFAULT NULL,
    `due_date` datetime DEFAULT NULL COMMENT '工单截止时间',
    `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP,
    `resolved_at` datetime DEFAULT NULL,
    `closed_at` datetime DEFAULT NULL,

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_ticket_no` (`ticket_no`),

    KEY `idx_creator_id` (`creator_id`),
    KEY `idx_assignee_id` (`assignee_id`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`)
    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_0900_ai_ci;


-- =====================================================
-- 5. 工单评论表
-- =====================================================
CREATE TABLE IF NOT EXISTS `ticket_comment` (
                                                `id` bigint NOT NULL AUTO_INCREMENT COMMENT '评论ID',
                                                `ticket_id` bigint NOT NULL COMMENT '工单ID',
                                                `user_id` bigint NOT NULL COMMENT '评论用户ID',
                                                `content` text NOT NULL COMMENT '评论内容',
                                                `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

                                                PRIMARY KEY (`id`),

    KEY `idx_ticket_id` (`ticket_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_created_at` (`created_at`)
    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_0900_ai_ci
    COMMENT='工单评论表';


-- =====================================================
-- 6. 工单操作日志表
-- =====================================================
CREATE TABLE IF NOT EXISTS `ticket_operation_log` (
                                                      `id` bigint NOT NULL AUTO_INCREMENT COMMENT '操作日志ID',
                                                      `ticket_id` bigint NOT NULL COMMENT '工单ID',
                                                      `operator_id` bigint NOT NULL COMMENT '操作人ID',
                                                      `operation_type` varchar(50) NOT NULL COMMENT '操作类型',
    `old_value` varchar(255) DEFAULT NULL COMMENT '修改前的值',
    `new_value` varchar(255) DEFAULT NULL COMMENT '修改后的值',
    `remark` varchar(500) DEFAULT NULL COMMENT '操作说明',
    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',

    PRIMARY KEY (`id`),

    KEY `idx_ticket_id` (`ticket_id`),
    KEY `idx_operator_id` (`operator_id`),
    KEY `idx_created_at` (`created_at`)
    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_0900_ai_ci
    COMMENT='工单操作日志表';