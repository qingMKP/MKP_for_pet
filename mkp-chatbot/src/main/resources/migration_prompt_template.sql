-- ============================================================
-- Prompt 模板化管理升级 — 新增表 + Spring AI 默认模板
-- 目标数据库：mkp_db
-- 执行方式：连接 MySQL 后 source 本文件（可重复执行）
-- ============================================================

CREATE TABLE IF NOT EXISTS `mkp_prompt_template` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '模板ID',
    `scene`         VARCHAR(50)  NOT NULL                COMMENT '场景标识：qa(问答)/chat(闲聊)/recommend(推荐)/default(默认)',
    `name`          VARCHAR(100) NOT NULL                COMMENT '模板名称（展示用）',
    `content`       TEXT         NOT NULL                COMMENT 'Prompt 模板内容，支持 {tools_description} 等占位符',
    `version`       INT          NOT NULL DEFAULT 1      COMMENT '版本号，每次更新+1',
    `is_default`    TINYINT      NOT NULL DEFAULT 0      COMMENT '是否为该场景默认模板：0=否，1=是',
    `status`        TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0=禁用，1=启用',
    `ab_test_tag`   VARCHAR(20)  DEFAULT NULL            COMMENT 'A/B 测试标签：如 control/test_a/test_b',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_scene_version` (`scene`, `version`),
    KEY `idx_scene_default` (`scene`, `is_default`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI Prompt 模板表';

-- ============================================================
-- 默认模板：Spring AI 原生 function calling 版本
-- 不再要求模型输出 Thought/Action/Observation 文本协议
-- ============================================================
INSERT INTO `mkp_prompt_template` (`scene`, `name`, `content`, `version`, `is_default`, `status`)
VALUES (
    'default',
    '默认 Spring AI 问答模板',
    '你是MKP宠物社区的AI助手，名叫MKP小助手。\n你的职责是回答用户关于宠物的问题。请用认真、耐心、和谐的语气回答，回答要简洁明了，分点说明。\n\n可用工具列表：\n{tools_description}\n\n工具使用规则：\n- 需要社区真实案例、经验分享时，调用 search_posts\n- 需要寄养或购买信息时，调用 search_market_posts\n- 需要专业养护、疾病、喂养知识时，调用 search_knowledge\n- 可以一次调用多个工具，也可以根据工具结果继续调用\n- 工具返回后，结合结果给出最终回答，不要原样复述工具输出\n- 搜索关键词用 1-3 个核心词，不要写成长句\n- 如果无法获取足够信息，诚实说明你的知识范围',
    1,
    1,
    1
)
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `content` = VALUES(`content`),
    `status` = 1;