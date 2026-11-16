-- ============================================================
-- RAG 长文档知识库 — 文档表 + 切片表（父子块）
-- 目标数据库：mkp_db
-- 执行方式：连接 MySQL 后 source 本文件（可重复执行）
-- ============================================================

CREATE TABLE IF NOT EXISTS `mkp_chatbot_document` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '文档ID',
    `title`       VARCHAR(200) NOT NULL                COMMENT '文档标题',
    `category`    VARCHAR(50)  DEFAULT NULL            COMMENT '分类：feeding/care/health/behavior/general',
    `source_type` VARCHAR(20)  NOT NULL DEFAULT 'text' COMMENT '来源：text/file/bootstrap',
    `file_name`   VARCHAR(255) DEFAULT NULL            COMMENT '原始文件名（上传时记录）',
    `char_count`  INT          NOT NULL DEFAULT 0      COMMENT '正文字符数',
    `chunk_count` INT          NOT NULL DEFAULT 0      COMMENT '子块数量',
    `status`      TINYINT      NOT NULL DEFAULT 0      COMMENT '状态：0=处理中，1=可用，2=失败',
    `deleted`     TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0=正常，1=已删除',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_document_title` (`title`),
    KEY `idx_document_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='RAG 长文档表';

CREATE TABLE IF NOT EXISTS `mkp_chatbot_chunk` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '切片ID',
    `document_id`   BIGINT       NOT NULL                COMMENT '所属文档ID',
    `parent_id`     BIGINT       NOT NULL DEFAULT 0      COMMENT '父块ID：0=本条是父块，>0=本条是子块',
    `seq`           INT          NOT NULL DEFAULT 1      COMMENT '同层级内的顺序号',
    `section_title` VARCHAR(200) DEFAULT NULL            COMMENT '所属小节标题',
    `content`       TEXT         NOT NULL                COMMENT '切片正文',
    `char_count`    INT          NOT NULL DEFAULT 0      COMMENT '字符数',
    `deleted`       TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0=正常，1=已删除',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_chunk_document` (`document_id`, `parent_id`),
    KEY `idx_chunk_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='RAG 文档切片表（父子块）';
