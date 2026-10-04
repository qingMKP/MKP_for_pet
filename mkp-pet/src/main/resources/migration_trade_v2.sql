-- 交易支付系统 第 2 批改造（P0/P1）：超时关单字段、审计事件表、申请唯一键修正
-- 执行方式：在 mkp_db 库执行一次（建议先备份）。本脚本不改历史数据，只加列/索引/表。
-- 注意：ALTER 不是幂等的，重复执行会报 "Duplicate column / Duplicate key name"，可直接忽略。

-- 1) 订单补 expire_time + 关单扫描索引（待支付超时时间）
ALTER TABLE `mkp_trade_order`
  ADD COLUMN `expire_time` datetime DEFAULT NULL COMMENT '待支付超时时间（到点未支付由定时任务关单）' AFTER `pay_time`,
  ADD KEY `idx_status_expire` (`status`, `expire_time`);

-- 2) 历史「待支付」订单补一个到期时间（创建时间 + 30 分钟），让关单任务能收走它们
UPDATE `mkp_trade_order`
   SET `expire_time` = DATE_ADD(`create_time`, INTERVAL 30 MINUTE)
 WHERE `status` = 0 AND `expire_time` IS NULL;

-- 3) 申请唯一键语义修正：
--    原来 uk_apply_active(posting_id, applicant_id, status) 会让「同一人被拒两次 / 撤回两次」
--    在把 status 改成 2 或 3 时撞唯一键（500）。改成一人一单只保留一条记录，
--    撤回/被拒后重新申请由代码复用同一条记录（ApplyMapper.reactivate）。
--    执行前可自查重复：SELECT posting_id, applicant_id, COUNT(*) FROM mkp_apply GROUP BY 1,2 HAVING COUNT(*) > 1;
ALTER TABLE `mkp_apply`
  DROP INDEX `uk_apply_active`,
  ADD UNIQUE KEY `uk_posting_applicant` (`posting_id`, `applicant_id`);

-- 4) 交易事件审计表：每次状态流转（含系统自动关单/自动确认）都留一条
CREATE TABLE IF NOT EXISTS `mkp_trade_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '事件ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `from_status` tinyint DEFAULT NULL COMMENT '流转前状态（下单为 NULL）',
  `to_status` tinyint NOT NULL COMMENT '流转后状态',
  `operator_id` bigint NOT NULL DEFAULT '0' COMMENT '操作者：0=系统（定时任务），正数=用户ID',
  `remark` varchar(200) NOT NULL DEFAULT '' COMMENT '说明',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_event` (`order_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='交易状态流转审计表';
