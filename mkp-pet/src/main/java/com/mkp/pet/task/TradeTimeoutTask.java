package com.mkp.pet.task;

import com.mkp.pet.config.TradeSettings;
import com.mkp.pet.mapper.TradeOrderMapper;
import com.mkp.pet.service.TradeOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 交易超时任务：让系统自己给订单收尾，避免三件事——
 *   1) 待支付订单永挂、发布单一直被占"已匹配"；
 *   2) 出售单买家不点"确认收货"→ 卖家永远拿不到钱；
 *   3) 寄养单照顾者已完成、主人不点"确认结算"→ 照顾者永远拿不到钱。
 *
 * 每笔走 Service 的独立事务：一笔失败不影响其他笔，也不会把整轮扫描回滚掉。
 * 参数见 TradeSettings / application.yaml 的 mkp.trade.*。
 */
@Component
public class TradeTimeoutTask {

    private static final Logger log = LoggerFactory.getLogger(TradeTimeoutTask.class);

    private final TradeOrderMapper orderMapper;
    private final TradeOrderService tradeOrderService;
    private final TradeSettings tradeSettings;

    public TradeTimeoutTask(TradeOrderMapper orderMapper, TradeOrderService tradeOrderService,
                            TradeSettings tradeSettings) {
        this.orderMapper = orderMapper;
        this.tradeOrderService = tradeOrderService;
        this.tradeSettings = tradeSettings;
    }

    @Scheduled(fixedDelayString = "${mkp.trade.scan-delay-ms:60000}")
    public void scan() {
        cancelExpiredOrders();
        autoConfirmOrders();
    }

    private void cancelExpiredOrders() {
        int limit = tradeSettings.getScanLimit();
        List<Long> ids = orderMapper.findExpiredIds(limit);
        int closed = 0;
        for (Long id : ids) {
            try {
                if (tradeOrderService.cancelExpired(id)) {
                    closed++;
                }
            } catch (Exception e) {
                log.error("超时关单失败：orderId={}", id, e);
            }
        }
        if (closed > 0) {
            log.info("交易超时任务：自动关单 {} 笔（待支付超过 {} 分钟）", closed, tradeSettings.getPayTimeoutMinutes());
        }
    }

    private void autoConfirmOrders() {
        int hours = tradeSettings.getAutoConfirmHours();
        int limit = tradeSettings.getScanLimit();
        List<Long> ids = new ArrayList<>();
        ids.addAll(orderMapper.findSaleToAutoConfirmIds(hours, limit));
        ids.addAll(orderMapper.findSettlingToAutoSettleIds(hours, limit));
        int done = 0;
        for (Long id : ids) {
            try {
                if (tradeOrderService.autoConfirm(id)) {
                    done++;
                }
            } catch (Exception e) {
                log.error("超时自动确认/结算失败：orderId={}", id, e);
            }
        }
        if (done > 0) {
            log.info("交易超时任务：自动确认/结算 {} 笔（超过 {} 小时未确认）", done, hours);
        }
    }
}
