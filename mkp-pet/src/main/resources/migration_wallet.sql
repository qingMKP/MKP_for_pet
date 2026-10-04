-- 交易支付系统 第 1 版：虚拟钱包 + 支付单
-- 执行方式：在 mkp_db 库中执行本脚本（纯新增，不动任何现有表/数据）
--
-- 三张表的分工：
--   mkp_wallet      这个账户现在有多少钱（可用 balance + 冻结 frozen）
--   mkp_wallet_log  这个账户的钱是怎么变的（只插不改，不可变账本）
--   mkp_payment     这笔钱到底动没动（一次资金动作的业务主体：收 / 退）

-- 钱包账户表
CREATE TABLE IF NOT EXISTS `mkp_wallet` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '钱包ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `balance` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '可用余额',
  `frozen` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '冻结金额（托管中）',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户钱包账户表';

-- 钱包流水表（不可变账本：只插入，永不更新/删除）
CREATE TABLE IF NOT EXISTS `mkp_wallet_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  `user_id` bigint NOT NULL COMMENT '账户所属用户ID',
  `order_id` bigint DEFAULT NULL COMMENT '关联订单ID（手动调整时为空）',
  `biz_type` tinyint NOT NULL COMMENT '业务类型：1=手动调整 2=支付冻结 3=结算入账 4=退款解冻',
  `amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '本次变动金额（正数）',
  `balance_after` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '变动后可用余额',
  `frozen_after` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '变动后冻结金额',
  `remark` varchar(200) NOT NULL DEFAULT '' COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `create_time`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='钱包流水表（不可变账本）';

-- 支付单表（一次资金动作：收款 / 退款）
CREATE TABLE IF NOT EXISTS `mkp_payment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '支付单ID',
  `payment_no` varchar(64) NOT NULL COMMENT '支付单号（唯一）',
  `order_id` bigint NOT NULL COMMENT '关联订单ID',
  `biz_type` tinyint NOT NULL COMMENT '业务类型：1=支付 2=退款',
  `payer_id` bigint NOT NULL COMMENT '付款方用户ID',
  `payee_id` bigint NOT NULL COMMENT '收款方用户ID',
  `amount` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '本次资金动作金额',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0=处理中 1=成功 2=失败',
  `channel` varchar(20) NOT NULL DEFAULT 'wallet' COMMENT '渠道：wallet=虚拟钱包',
  `out_trade_no` varchar(64) NOT NULL DEFAULT '' COMMENT '渠道单号（虚拟钱包=payment_no；接真渠道后为渠道返回）',
  `error_msg` varchar(200) NOT NULL DEFAULT '' COMMENT '失败原因',
  `pay_time` datetime DEFAULT NULL COMMENT '资金动作完成时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_no` (`payment_no`),
  UNIQUE KEY `uk_order_biz` (`order_id`, `biz_type`),
  KEY `idx_payer_id` (`payer_id`),
  KEY `idx_payee_id` (`payee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付单表（一次资金动作）';

-- 自检查询（执行完可跑一下确认三张表都在）
-- SELECT table_name, table_comment FROM information_schema.tables WHERE table_schema = 'mkp_db' AND table_name LIKE 'mkp_wallet%' OR (table_schema='mkp_db' AND table_name='mkp_payment');
