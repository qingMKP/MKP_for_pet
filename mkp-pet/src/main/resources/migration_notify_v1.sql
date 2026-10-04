-- 过期交易帖子的站内私信提醒（第一阶段）：只加一张台账表，不动现有表/数据
-- 执行方式：在 mkp_db 库执行一次（建议先备份）
--
-- 台账的用法：
--   uk_posting_notify(posting_id, notify_type) 是幂等键 —— 同一帖子同一类通知只会有一行，
--   定时任务靠它保证"只发一次"；status: 0=待发送 1=已发送 2=发送中（卡住超 10 分钟可被重新抢占）。
CREATE TABLE IF NOT EXISTS `mkp_posting_notify` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '台账ID',
  `posting_id` bigint NOT NULL COMMENT '发布单ID',
  `to_user_id` bigint NOT NULL COMMENT '收件人（发布单发布者）',
  `notify_type` tinyint NOT NULL COMMENT '通知类型：1=开始前预告 2=已过期',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0=待发送 1=已发送 2=发送中',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '已尝试发送次数',
  `last_error` varchar(300) NOT NULL DEFAULT '' COMMENT '最后一次失败原因',
  `sent_time` datetime DEFAULT NULL COMMENT '发送成功时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_posting_notify` (`posting_id`, `notify_type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='发布单通知台账';

-- 自查：还没发出去 / 发失败的（重试次数用尽的要人工看 last_error）
-- SELECT id, posting_id, notify_type, status, retry_count, last_error FROM mkp_posting_notify
--  WHERE status <> 1 ORDER BY id;
