package com.mkp.common.constant;

public class PetConstants {
    // 宠物类型
    public static final int TYPE_FOSTER = 1;   // 寄养
    public static final int TYPE_SALE = 2;     // 出售

    // 宠物状态
    public static final int STATUS_AVAILABLE = 1;  // 可领养/在售
    public static final int STATUS_TAKEN = 2;      // 已领养/已售
    public static final int STATUS_OFF = 3;        // 已下架

    // 订单状态
    public static final int ORDER_PENDING = 0;     // 待支付
    public static final int ORDER_PAID = 1;        // 已支付/已成交
    public static final int ORDER_CANCELLED = 2;   // 已取消

    private PetConstants() {}
}
