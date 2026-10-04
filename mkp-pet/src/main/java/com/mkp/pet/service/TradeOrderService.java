package com.mkp.pet.service;

import com.mkp.common.dto.TradeOrderVO;
import com.mkp.common.dto.TradeOrderDetailVO;
import com.mkp.common.entity.Apply;
import com.mkp.common.entity.Posting;
import com.mkp.common.result.R;

import java.util.List;

public interface TradeOrderService {
    TradeOrderVO createFromApply(Posting posting, Apply apply);
    R<TradeOrderVO> pay(Long userId, Long orderId);
    R<TradeOrderVO> start(Long userId, Long orderId);
    R<TradeOrderVO> finish(Long userId, Long orderId);
    R<TradeOrderVO> complete(Long userId, Long orderId);
    R<TradeOrderVO> cancel(Long userId, Long orderId);
    R<TradeOrderVO> refund(Long userId, Long orderId);
    R<List<TradeOrderVO>> myOrders(Long userId);

    /** 订单详情：订单 + 支付/退款单 + 状态流转审计（仅订单参与方可看） */
    R<TradeOrderDetailVO> detail(Long userId, Long orderId);

    /** 超时未支付 → 系统关单并释放发布单（定时任务调用，单笔独立事务） */
    boolean cancelExpired(Long orderId);

    /** 超时未确认 → 系统自动确认并结算（定时任务调用，单笔独立事务） */
    boolean autoConfirm(Long orderId);
}
