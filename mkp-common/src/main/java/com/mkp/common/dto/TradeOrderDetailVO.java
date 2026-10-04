package com.mkp.common.dto;

import com.mkp.common.entity.TradeEvent;

import java.util.List;

/** 订单详情：订单 + 资金动作（支付/退款单）+ 状态流转审计 */
public class TradeOrderDetailVO {
    private TradeOrderVO order;
    private List<PaymentVO> payments;
    private List<TradeEvent> events;

    public TradeOrderVO getOrder() { return order; }
    public void setOrder(TradeOrderVO order) { this.order = order; }
    public List<PaymentVO> getPayments() { return payments; }
    public void setPayments(List<PaymentVO> payments) { this.payments = payments; }
    public List<TradeEvent> getEvents() { return events; }
    public void setEvents(List<TradeEvent> events) { this.events = events; }
}
