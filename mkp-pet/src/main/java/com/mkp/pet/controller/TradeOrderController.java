package com.mkp.pet.controller;

import com.mkp.common.dto.TradeOrderVO;
import com.mkp.common.dto.TradeOrderDetailVO;
import com.mkp.common.result.R;
import com.mkp.pet.service.TradeOrderService;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/trade")
public class TradeOrderController {

    private final TradeOrderService tradeOrderService;

    public TradeOrderController(TradeOrderService tradeOrderService) {
        this.tradeOrderService = tradeOrderService;
    }

    private Long getUserId(HttpServletRequest request) {
        String header = request.getHeader("X-User-Id");
        return header != null ? Long.parseLong(header) : null;
    }

    @GetMapping("/my")
    public R<List<TradeOrderVO>> my(HttpServletRequest request) {
        return tradeOrderService.myOrders(getUserId(request));
    }

    /** 单笔订单详情（含支付/退款单与流转审计）：前端可以据此做单笔轮询与详情页 */
    @GetMapping("/{id}")
    public R<TradeOrderDetailVO> detail(@PathVariable Long id, HttpServletRequest request) {
        return tradeOrderService.detail(getUserId(request), id);
    }

    @PostMapping("/{id}/pay")
    public R<TradeOrderVO> pay(@PathVariable Long id, HttpServletRequest request) {
        return tradeOrderService.pay(getUserId(request), id);
    }

    @PostMapping("/{id}/start")
    public R<TradeOrderVO> start(@PathVariable Long id, HttpServletRequest request) {
        return tradeOrderService.start(getUserId(request), id);
    }

    @PostMapping("/{id}/finish")
    public R<TradeOrderVO> finish(@PathVariable Long id, HttpServletRequest request) {
        return tradeOrderService.finish(getUserId(request), id);
    }

    @PostMapping("/{id}/complete")
    public R<TradeOrderVO> complete(@PathVariable Long id, HttpServletRequest request) {
        return tradeOrderService.complete(getUserId(request), id);
    }

    @PostMapping("/{id}/cancel")
    public R<TradeOrderVO> cancel(@PathVariable Long id, HttpServletRequest request) {
        return tradeOrderService.cancel(getUserId(request), id);
    }

    @PostMapping("/{id}/refund")
    public R<TradeOrderVO> refund(@PathVariable Long id, HttpServletRequest request) {
        return tradeOrderService.refund(getUserId(request), id);
    }
}
