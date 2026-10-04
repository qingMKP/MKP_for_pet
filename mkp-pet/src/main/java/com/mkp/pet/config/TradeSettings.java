package com.mkp.pet.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 交易可调参数（改配置即可，不动代码）。
 *
 * payTimeoutMinutes  待支付订单多久没付款 → 定时任务自动关单并释放发布单
 * autoConfirmHours   出售"买家超时不确认收货"、寄养"主人超时不确认结算" → 系统自动确认并结算
 * scanLimit          每轮扫描最多处理多少笔（分页限速，避免一次锁大量行）
 * minStartLeadMinutes 发布寄养时，开始时间至少要距现在多少分钟（否则发布即不可见）
 */
@Component
public class TradeSettings {

    @Value("${mkp.trade.pay-timeout-minutes:30}")
    private int payTimeoutMinutes;

    @Value("${mkp.trade.auto-confirm-hours:72}")
    private int autoConfirmHours;

    @Value("${mkp.trade.scan-limit:200}")
    private int scanLimit;

    @Value("${mkp.trade.min-start-lead-minutes:5}")
    private int minStartLeadMinutes;

    public int getPayTimeoutMinutes() { return payTimeoutMinutes; }
    public int getAutoConfirmHours() { return autoConfirmHours; }
    public int getScanLimit() { return scanLimit; }
    public int getMinStartLeadMinutes() { return minStartLeadMinutes; }
}
