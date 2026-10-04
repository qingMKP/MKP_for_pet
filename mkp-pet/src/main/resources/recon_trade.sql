-- 交易对账自检（只读，随时可跑）
-- 口径：订单(业务) ↔ 支付单(资金动作) ↔ 钱包流水(账户分录)。任何一条有输出就需要人工核。
-- 说明：第 4 条出现差额不代表代码错，历史数据/手工清库都可能造成，先看第 1~3 条。

-- 1) 已支付及之后的订单：必须恰好 1 张"支付"支付单，且金额与订单一致
SELECT o.id, o.order_no, o.status, o.amount, COUNT(p.id) AS pay_cnt, IFNULL(SUM(p.amount), 0) AS pay_sum
  FROM mkp_trade_order o
  LEFT JOIN mkp_payment p ON p.order_id = o.id AND p.biz_type = 1
 WHERE o.status IN (1, 2, 3, 4) AND o.deleted = 0
 GROUP BY o.id, o.order_no, o.status, o.amount
HAVING pay_cnt <> 1 OR pay_sum <> o.amount;

-- 2) 已退款订单：必须恰好 1 张退款单，金额一致
SELECT o.id, o.order_no, o.status, o.amount, COUNT(p.id) AS refund_cnt, IFNULL(SUM(p.amount), 0) AS refund_sum
  FROM mkp_trade_order o
  LEFT JOIN mkp_payment p ON p.order_id = o.id AND p.biz_type = 2
 WHERE o.status = 6 AND o.deleted = 0
 GROUP BY o.id, o.order_no, o.status, o.amount
HAVING refund_cnt <> 1 OR refund_sum <> o.amount;

-- 3) 钱包 ↔ 流水：最后一条流水的快照必须等于当前余额/冻结（强不变式）
SELECT w.user_id, w.balance, w.frozen, l.balance_after, l.frozen_after
  FROM mkp_wallet w
  JOIN mkp_wallet_log l ON l.id = (SELECT MAX(id) FROM mkp_wallet_log WHERE user_id = w.user_id)
 WHERE l.balance_after <> w.balance OR l.frozen_after <> w.frozen;

-- 3b) 没有任何流水的钱包必须余额/冻结都为 0
SELECT w.user_id, w.balance, w.frozen
  FROM mkp_wallet w LEFT JOIN mkp_wallet_log l ON l.user_id = w.user_id
 WHERE l.id IS NULL AND (w.balance <> 0 OR w.frozen <> 0);

-- 4) 托管口径（人工核对）：各账户冻结合计 应 ≈ 托管中/履约中/待结算订单金额合计
SELECT (SELECT IFNULL(SUM(frozen), 0) FROM mkp_wallet) AS wallet_frozen_total,
       (SELECT IFNULL(SUM(amount), 0) FROM mkp_trade_order WHERE status IN (1, 2, 3) AND deleted = 0) AS escrow_orders_total;

-- 5) 孤儿数据：流水/支付单指向了不存在的订单
SELECT l.id, l.user_id, l.order_id, l.biz_type, l.amount
  FROM mkp_wallet_log l LEFT JOIN mkp_trade_order o ON o.id = l.order_id
 WHERE l.order_id IS NOT NULL AND o.id IS NULL;
SELECT p.id, p.payment_no, p.order_id
  FROM mkp_payment p LEFT JOIN mkp_trade_order o ON o.id = p.order_id
 WHERE o.id IS NULL;

-- 6) 审计自洽：最后一次流转的 to_status 应等于订单当前状态
SELECT o.id, o.order_no, o.status, e.to_status, e.remark
  FROM mkp_trade_order o
  JOIN mkp_trade_event e ON e.id = (SELECT MAX(id) FROM mkp_trade_event WHERE order_id = o.id)
 WHERE e.to_status <> o.status;

-- 7) 关单任务有没有漏：待支付但已过期
SELECT id, order_no, payer_id, amount, expire_time
  FROM mkp_trade_order
 WHERE status = 0 AND expire_time IS NOT NULL AND expire_time < NOW() AND deleted = 0;
