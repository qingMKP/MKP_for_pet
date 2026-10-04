-- 交易支付系统 第 1 版：存量「托管中」订单的资金补记
-- ============================================================
-- 什么时候跑：第 1 版上线、开始使用钱包之前，跑一次。
-- 为什么要跑：钱包上线前就有订单进了「托管中」（status 1/2/3），
--             那时的付款根本没有扣过任何人的钱 —— 订单说钱到位了，账上没钱。
--             不补记的话点「确认完成 / 退款」会直接报「托管资金异常」。
-- 做了什么：把这些订单的金额，按付款方汇总，一次性记成"冻结"，
--           并补一条流水说明这笔钱是历史迁移来的（保证账本和钱包对得上）。
-- 是否幂等：是。靠 remark 标记，重复执行不会重复加钱。
-- ============================================================

-- 1) 给这些付款方补出 0 元钱包行（已存在则跳过）
INSERT IGNORE INTO `mkp_wallet` (`user_id`, `balance`, `frozen`, `version`)
SELECT DISTINCT `payer_id`, 0.00, 0.00, 0 FROM `mkp_trade_order`
 WHERE `status` IN (1, 2, 3) AND `deleted` = 0;

-- 2) 把订单金额按付款方汇总，补记到「冻结」上（只补还没补过的账户）
UPDATE `mkp_wallet` w
  JOIN (SELECT `payer_id`, SUM(`amount`) AS amt FROM `mkp_trade_order`
         WHERE `status` IN (1, 2, 3) AND `deleted` = 0
         GROUP BY `payer_id`) o
    ON o.`payer_id` = w.`user_id`
   SET w.`frozen` = w.`frozen` + o.amt,
       w.`version` = w.`version` + 1
 WHERE NOT EXISTS (SELECT 1 FROM `mkp_wallet_log` l
                    WHERE l.`user_id` = w.`user_id`
                      AND l.`remark` = '历史数据迁移：存量托管资金补记');

-- 3) 补一条流水，让账本的第一条记录就是这笔历史资金
INSERT INTO `mkp_wallet_log` (`user_id`, `order_id`, `biz_type`, `amount`, `balance_after`, `frozen_after`, `remark`)
SELECT w.`user_id`, NULL, 1, w.`frozen`, w.`balance`, w.`frozen`, '历史数据迁移：存量托管资金补记'
  FROM `mkp_wallet` w
 WHERE w.`frozen` > 0
   AND EXISTS (SELECT 1 FROM `mkp_trade_order` o
                WHERE o.`payer_id` = w.`user_id` AND o.`status` IN (1, 2, 3) AND o.`deleted` = 0)
   AND NOT EXISTS (SELECT 1 FROM `mkp_wallet_log` l
                    WHERE l.`user_id` = w.`user_id`
                      AND l.`remark` = '历史数据迁移：存量托管资金补记');

-- 4) 自检：补记后，每个付款方的冻结金额应当 >= 其托管中订单金额之和
-- SELECT o.payer_id, SUM(o.amount) AS 订单托管合计, w.frozen AS 钱包冻结
--   FROM mkp_trade_order o LEFT JOIN mkp_wallet w ON w.user_id = o.payer_id
--  WHERE o.status IN (1,2,3) AND o.deleted = 0 GROUP BY o.payer_id, w.frozen;
