-- 宠物交易平台服务化 表结构（方案 C：发布单/申请/交易订单/评价 四层）
-- 执行方式：在 mkp_db 库中执行本脚本

-- 发布单表（统一承载需求单与服务单）
CREATE TABLE IF NOT EXISTS `mkp_posting` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '发布单ID',
  `user_id` bigint NOT NULL COMMENT '发布者用户ID',
  `direction` tinyint NOT NULL COMMENT '方向：1=需求(求) 2=供给(供)',
  `category` tinyint NOT NULL COMMENT '分类：1=领养 2=寄养 3=出售',
  `pet_name` varchar(50) NOT NULL DEFAULT '' COMMENT '宠物名（需求单可空）',
  `species` varchar(20) NOT NULL DEFAULT '' COMMENT '物种：猫/狗/其他',
  `breed` varchar(50) NOT NULL DEFAULT '' COMMENT '品种',
  `age_month` int NOT NULL DEFAULT '0' COMMENT '月龄',
  `weight_kg` decimal(6,2) DEFAULT NULL COMMENT '体重(kg)',
  `gender` tinyint NOT NULL DEFAULT '0' COMMENT '性别：0=未知 1=公 2=母',
  `price` decimal(10,2) DEFAULT NULL COMMENT '供给：售价/寄养单价；需求：预算',
  `price_unit` tinyint NOT NULL DEFAULT '1' COMMENT '计价单位：1=总价 2=每天 3=每小时 4=每分钟',
  `start_date` datetime DEFAULT NULL COMMENT '寄养开始时间',
  `end_date` datetime DEFAULT NULL COMMENT '寄养结束时间',
  `duration_minutes` int DEFAULT NULL COMMENT '寄养时长(分钟)',
  `description` text COMMENT '描述',
  `images` varchar(2000) NOT NULL DEFAULT '' COMMENT '逗号分隔的图片URL',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1=开放 2=已匹配 3=已下架 4=已关闭',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_category_direction` (`category`, `direction`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物交易发布单表';

-- 申请/报价表（撮合层）
CREATE TABLE IF NOT EXISTS `mkp_apply` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '申请ID',
  `posting_id` bigint NOT NULL COMMENT '发布单ID',
  `applicant_id` bigint NOT NULL COMMENT '申请人用户ID',
  `amount` decimal(10,2) DEFAULT NULL COMMENT '报价金额（可空=按发布单价格）',
  `message` varchar(500) NOT NULL DEFAULT '' COMMENT '申请留言/报价说明',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0=待确认 1=已接受 2=已拒绝 3=已撤回',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_apply_active` (`posting_id`, `applicant_id`, `status`),
  KEY `idx_posting_id` (`posting_id`),
  KEY `idx_applicant_id` (`applicant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物交易申请/报价表';

-- 交易订单表（资金托管）
CREATE TABLE IF NOT EXISTS `mkp_trade_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `order_no` varchar(64) NOT NULL COMMENT '订单号(唯一)',
  `posting_id` bigint NOT NULL COMMENT '发布单ID',
  `apply_id` bigint NOT NULL COMMENT '申请ID',
  `category` tinyint NOT NULL COMMENT '分类：1=领养 2=寄养 3=出售',
  `payer_id` bigint NOT NULL COMMENT '付钱方用户ID',
  `payee_id` bigint NOT NULL COMMENT '收钱方用户ID',
  `amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '总金额',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0=待支付 1=托管中 2=履约中 3=待结算 4=已完成 5=已取消 6=已退款',
  `pay_time` datetime DEFAULT NULL COMMENT '支付时间',
  `complete_time` datetime DEFAULT NULL COMMENT '完成时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_payer_id` (`payer_id`),
  KEY `idx_payee_id` (`payee_id`),
  KEY `idx_posting_id` (`posting_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物交易订单表';

-- 评价表（双向互评）
CREATE TABLE IF NOT EXISTS `mkp_review` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '评价ID',
  `order_id` bigint NOT NULL COMMENT '订单ID',
  `category` tinyint NOT NULL COMMENT '分类：1=领养 2=寄养 3=出售',
  `reviewer_id` bigint NOT NULL COMMENT '评价方用户ID',
  `reviewee_id` bigint NOT NULL COMMENT '被评价方用户ID',
  `rating` tinyint NOT NULL COMMENT '评分 1~5',
  `content` varchar(500) NOT NULL DEFAULT '' COMMENT '评价内容',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review` (`order_id`, `reviewer_id`),
  KEY `idx_reviewee_id` (`reviewee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物交易评价表';
