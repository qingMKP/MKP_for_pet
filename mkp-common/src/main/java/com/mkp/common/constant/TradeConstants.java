package com.mkp.common.constant;

public class TradeConstants {
    // 发布单方向 direction
    public static final int DIRECTION_DEMAND = 1;   // 需求(求)
    public static final int DIRECTION_SUPPLY = 2;   // 供给(供)

    // 业务分类 category
    // 当前只开放「寄养」和「出售」两种；领养先不开放发布（保留常量，避免历史数据无法解读）
    public static final int CATEGORY_ADOPT = 1;     // 领养（预留，当前不可发布）
    public static final int CATEGORY_FOSTER = 2;    // 寄养：我的宠物需要人照顾，我出钱
    public static final int CATEGORY_SALE = 3;      // 出售：我要卖掉这只宠物，买家付钱

    // 计价单位 price_unit
    public static final int PRICE_UNIT_TOTAL = 1;   // 总价
    public static final int PRICE_UNIT_DAY = 2;     // 每天
    public static final int PRICE_UNIT_HOUR = 3;    // 每小时
    public static final int PRICE_UNIT_MINUTE = 4;  // 每分钟

    // 发布单状态
    public static final int POSTING_OPEN = 1;       // 开放中
    public static final int POSTING_MATCHED = 2;    // 已匹配
    public static final int POSTING_OFF = 3;        // 已下架
    public static final int POSTING_CLOSED = 4;     // 已关闭

    // 申请状态
    public static final int APPLY_PENDING = 0;      // 待确认
    public static final int APPLY_ACCEPTED = 1;     // 已接受
    public static final int APPLY_REJECTED = 2;     // 已拒绝
    public static final int APPLY_WITHDRAWN = 3;    // 已撤回

    // 订单状态
    public static final int ORDER_PENDING = 0;      // 待支付
    public static final int ORDER_ESCROW = 1;       // 托管中（已支付，资金冻结）
    public static final int ORDER_SERVING = 2;      // 履约中
    public static final int ORDER_SETTLING = 3;     // 待结算
    public static final int ORDER_COMPLETED = 4;    // 已完成（结算给收款方）
    public static final int ORDER_CANCELLED = 5;    // 已取消
    public static final int ORDER_REFUNDED = 6;     // 已退款

    // 钱包流水业务类型
    public static final int WALLET_BIZ_ADJUST = 1;   // 手动调整（调试）
    public static final int WALLET_BIZ_PAY = 2;      // 支付冻结：可用 -> 冻结
    public static final int WALLET_BIZ_SETTLE = 3;   // 结算入账：付款方冻结 -> 收款方可用
    public static final int WALLET_BIZ_REFUND = 4;   // 退款解冻：冻结 -> 可用

    // 支付单业务类型
    public static final int PAY_BIZ_PAY = 1;         // 支付
    public static final int PAY_BIZ_REFUND = 2;      // 退款

    // 支付单状态
    public static final int PAY_STATUS_PROCESSING = 0;  // 处理中（留给未来接渠道）
    public static final int PAY_STATUS_SUCCESS = 1;     // 成功
    public static final int PAY_STATUS_FAILED = 2;      // 失败

    // 支付渠道
    public static final String PAY_CHANNEL_WALLET = "wallet";

    // 交易事件（审计）的操作者：0 = 系统（定时任务自动流转），正数为用户 ID
    public static final long OPERATOR_SYSTEM = 0L;

    /**
     * 资金方向由「业务分类」推导，不再让用户手选 —— 用户分不清"我需要/我提供"，而且同一个分类下
     * 两种方向混在一起最容易搞错谁付钱。规则（与历史数据一致，所以是纯收敛、不需要迁移）：
     *   寄养 2 => 需求(1)：发布者是宠物主人，出钱请人照顾，申请人收款
     *   出售 3 => 供给(2)：发布者是卖家，收钱，申请人（买家）付款
     */
    public static int deriveDirection(int category) {
        return category == CATEGORY_FOSTER ? DIRECTION_DEMAND : DIRECTION_SUPPLY;
    }

    private TradeConstants() {}
}
