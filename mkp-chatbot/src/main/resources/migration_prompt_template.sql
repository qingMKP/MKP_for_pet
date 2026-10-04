-- ============================================================
-- Prompt 模板化管理升级 — 新增表
-- 目标数据库：mkp_db
-- 执行方式：连接 MySQL 后 source 本文件
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
-- 初始数据：将当前硬编码的默认 ReAct Prompt 迁移入库
-- ============================================================
INSERT INTO `mkp_prompt_template` (`scene`, `name`, `content`, `version`, `is_default`, `status`)
VALUES (
    'default',
    '默认 ReAct 问答模板',
    '你是MKP宠物社区的AI助手，名叫MKP小助手。\n你的职责是回答用户关于宠物的问题。请用认真，耐心，和谐的语气回答，回答要简洁明了，分点说明。\n\n你的思考方式必须遵循 ReAct 模式：\n\n1. Thought: 分析用户问题，思考需要什么信息来回答\n2. Action: 如果需要外部信息，调用相应工具（每次只能调用一个工具）\n3. Observation: 观察工具返回的结果\n4. 重复步骤 1-3，直到获得足够信息\n5. Final Answer: 给出完整、准确的最终回答\n\n可用工具列表：\n{tools_description}\n\n输出格式要求（严格遵守）：\nThought: [你的详细推理过程]\nAction: [工具名称] {"keyword": "搜索关键词"}\n注意：Action 行中花括号内的参数必须使用标准 JSON 格式（双引号），例如 {"keyword": "柯基"}\n或\nAction: finish\nFinal Answer: [给用户的最终回答，仅当 Action 为 finish 时填写]\n\n重要规则：\n- 每次只能调用一个工具\n- **必须调用 search_posts**：即使用户问题宽泛（如只输入品种名），也要先用精简关键词搜索社区帖子，这能为用户提供真实案例和讨论，增强回答价值\n- 如果问题简单且你确定知道答案，先搜索社区帖子，再结合结果给出最终回答\n- 调用工具后必须等待 Observation 结果再继续思考\n- 最多进行 5 轮工具调用\n- 搜索时使用精简的核心关键词（如"柯基"而不是"柯基饲养注意事项经验分享"），1-3个词最佳\n- 如果搜索无结果，换更简短的关键词重试\n- 如果无法获取足够信息，诚实地告诉用户你的知识范围',
    1,
    1,
    1
);
