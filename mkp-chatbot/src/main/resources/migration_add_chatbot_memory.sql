-- ============================================================
-- 对话记忆系统升级 — 新增表
-- 目标数据库：mkp_db
-- 执行方式：连接 MySQL 后 source 本文件
-- ============================================================

CREATE TABLE IF NOT EXISTS `mkp_chatbot_conversation` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Conversation ID',
    `user_id`     BIGINT       NOT NULL                COMMENT 'User ID',
    `title`       VARCHAR(100) DEFAULT NULL            COMMENT 'Conversation title',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Chatbot conversation table';

CREATE TABLE IF NOT EXISTS `mkp_chatbot_message` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT 'Message ID',
    `conversation_id` BIGINT      NOT NULL               COMMENT 'Conversation ID',
    `role`            VARCHAR(20) NOT NULL               COMMENT 'Role: user / assistant',
    `content`         TEXT                               COMMENT 'Message content',
    `create_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
    PRIMARY KEY (`id`),
    INDEX `idx_conversation_id` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Chatbot message table';
