package com.mkp.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mkp.common.constant.TradeConstants;
import com.mkp.common.dto.PaymentVO;
import com.mkp.common.dto.TradeOrderDetailVO;
import com.mkp.common.dto.TradeOrderVO;
import com.mkp.common.entity.Apply;
import com.mkp.common.entity.Payment;
import com.mkp.common.entity.Posting;
import com.mkp.common.entity.TradeEvent;
import com.mkp.common.entity.TradeOrder;
import com.mkp.common.exception.BizException;
import com.mkp.common.result.R;
import com.mkp.pet.config.TradeSettings;
import com.mkp.pet.mapper.PaymentMapper;
import com.mkp.pet.mapper.PostingMapper;
import com.mkp.pet.mapper.TradeEventMapper;
import com.mkp.pet.mapper.TradeOrderMapper;
import com.mkp.pet.service.PostingService;
import com.mkp.pet.service.TradeOrderService;
import com.mkp.pet.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class TradeOrderServiceImpl implements TradeOrderService {

    private static final Logger log = LoggerFactory.getLogger(TradeOrderServiceImpl.class);
    private static final DateTimeFormatter ORDER_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final TradeOrderMapper orderMapper;
    private final PostingMapper postingMapper;
    private final PostingService postingService;
    private final WalletService walletService;
    private final PaymentMapper paymentMapper;
    private final TradeEventMapper tradeEventMapper;
    private final TradeSettings tradeSettings;

    public TradeOrderServiceImpl(TradeOrderMapper orderMapper, PostingMapper postingMapper,
                                 PostingService postingService, WalletService walletService,
                                 PaymentMapper paymentMapper, TradeEventMapper tradeEventMapper,
                                 TradeSettings tradeSettings) {
        this.orderMapper = orderMapper;
        this.postingMapper = postingMapper;
        this.postingService = postingService;
        this.walletService = walletService;
        this.paymentMapper = paymentMapper;
        this.tradeEventMapper = tradeEventMapper;
        this.tradeSettings = tradeSettings;
    }

    @Override
    public TradeOrderVO createFromApply(Posting posting, Apply apply) {
        TradeOrder order = new TradeOrder();
        order.setPostingId(posting.getId());
        order.setApplyId(apply.getId());
        order.setCategory(posting.getCategory());

        // 资金方向由分类推导（不再读用户可选的 direction 字段）：
        //   寄养 2 => 需求：发布者(宠物主人)付钱，申请人(照顾者)收款
        //   出售 3 => 供给：申请人(买家)付钱，发布者(卖家)收款
        if (TradeConstants.deriveDirection(posting.getCategory()) == TradeConstants.DIRECTION_DEMAND) {
            order.setPayerId(posting.getUserId());
            order.setPayeeId(apply.getApplicantId());
        } else {
            order.setPayerId(apply.getApplicantId());
            order.setPayeeId(posting.getUserId());
        }

        // 现在的发布只有寄养和出售，两者都要走"支付 → 托管 → 结算"，不存在直接完成的单
        order.setAmount(calcAmount(posting));
        order.setStatus(TradeConstants.ORDER_PENDING);
        // 待支付超时时间：到点没付款由 TradeTimeoutTask 关单并释放发布单
        order.setExpireTime(LocalDateTime.now().plusMinutes(tradeSettings.getPayTimeoutMinutes()));
        insertWithRetry(order);
        recordEvent(order.getId(), null, TradeConstants.ORDER_PENDING, posting.getUserId(), "下单：等待付款方支付");
        return toVO(order, posting, posting.getUserId());
    }

    private BigDecimal calcAmount(Posting posting) {
        // 金额只由发布单价格决定：寄养按计价单位 × 时长折算，出售即售价
        BigDecimal price = posting.getPrice();
        if (price == null) {
            throw new BizException("该发布单未设置价格，无法生成订单");
        }
        if (posting.getCategory() == TradeConstants.CATEGORY_FOSTER
                && posting.getPriceUnit() != null
                && posting.getDurationMinutes() != null) {
            int minutes = posting.getDurationMinutes();
            switch (posting.getPriceUnit()) {
                case TradeConstants.PRICE_UNIT_DAY:
                    return price.multiply(BigDecimal.valueOf(minutes))
                            .divide(BigDecimal.valueOf(1440), 2, RoundingMode.HALF_UP);
                case TradeConstants.PRICE_UNIT_HOUR:
                    return price.multiply(BigDecimal.valueOf(minutes))
                            .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
                case TradeConstants.PRICE_UNIT_MINUTE:
                    return price.multiply(BigDecimal.valueOf(minutes));
                default:
                    return price;
            }
        }
        return price;
    }

    @Override
    @Transactional
    public R<TradeOrderVO> pay(Long userId, Long orderId) {
        TradeOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return R.fail(R.NOT_FOUND, "订单不存在");
        }
        if (!order.getPayerId().equals(userId)) {
            return R.fail(R.FORBIDDEN, "只有付款方才能支付");
        }
        // 已过期待支付的单不允许再付（关单任务可能还没扫到，这里兜一层）
        if (order.getStatus() == TradeConstants.ORDER_PENDING
                && order.getExpireTime() != null && order.getExpireTime().isBefore(LocalDateTime.now())) {
            cancelExpired(orderId);
            return R.fail("订单已超时未支付，已自动取消，请重新下单");
        }
        int rows = orderMapper.markPaid(orderId);
        if (rows == 0) {
            TradeOrder latest = orderMapper.selectById(orderId);
            if (latest == null) {
                return R.fail(R.NOT_FOUND, "订单不存在");
            }
            if (reached(latest.getStatus(), TradeConstants.ORDER_ESCROW)) {
                // 幂等：重复点击/网络重试，钱只动过一次，直接返回当前订单
                return R.ok(toVO(latest, null, userId));
            }
            if (latest.getStatus() == TradeConstants.ORDER_PENDING) {
                // 本事务快照还是旧值，说明另一个请求正在支付（MySQL 可重复读）
                return R.fail("订单正在支付处理中，请刷新后查看");
            }
            return R.fail("订单状态异常，无法支付（当前状态：" + statusName(latest.getStatus()) + "）");
        }
        // CAS 成功后才是唯一能进入资金动作的门：冻结付款方资金 + 落支付单 + 写流水。
        // 任何一步失败都会抛 BizException，把上面那句订单 UPDATE 一起回滚，
        // 所以不可能出现"订单已托管但没扣钱"。
        walletService.freezeForPay(order);

        order.setStatus(TradeConstants.ORDER_ESCROW);
        order.setPayTime(LocalDateTime.now());
        recordEvent(order.getId(), TradeConstants.ORDER_PENDING, TradeConstants.ORDER_ESCROW,
                userId, "付款方支付，资金进入托管");
        return R.ok(toVO(order, null, userId));
    }

    @Override
    @Transactional
    public R<TradeOrderVO> start(Long userId, Long orderId) {
        TradeOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return R.fail(R.NOT_FOUND, "订单不存在");
        }
        if (!order.getPayeeId().equals(userId)) {
            return R.fail(R.FORBIDDEN, "只有收款方才能开始履约");
        }
        int rows = orderMapper.markStart(orderId);
        if (rows == 0) {
            return casMiss(orderId, userId, TradeConstants.ORDER_SERVING, "无法开始履约");
        }
        order.setStatus(TradeConstants.ORDER_SERVING);
        recordEvent(order.getId(), TradeConstants.ORDER_ESCROW, TradeConstants.ORDER_SERVING,
                userId, "收款方开始履约");
        return R.ok(toVO(order, null, userId));
    }

    @Override
    @Transactional
    public R<TradeOrderVO> finish(Long userId, Long orderId) {
        TradeOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return R.fail(R.NOT_FOUND, "订单不存在");
        }
        if (!order.getPayeeId().equals(userId)) {
            return R.fail(R.FORBIDDEN, "只有收款方才能结束履约");
        }
        int rows = orderMapper.markFinish(orderId);
        if (rows == 0) {
            return casMiss(orderId, userId, TradeConstants.ORDER_SETTLING, "无法结束履约");
        }
        order.setStatus(TradeConstants.ORDER_SETTLING);
        recordEvent(order.getId(), TradeConstants.ORDER_SERVING, TradeConstants.ORDER_SETTLING,
                userId, "收款方结束履约，等待付款方确认结算");
        return R.ok(toVO(order, null, userId));
    }

    @Override
    @Transactional
    public R<TradeOrderVO> complete(Long userId, Long orderId) {
        TradeOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return R.fail(R.NOT_FOUND, "订单不存在");
        }
        if (!order.getPayerId().equals(userId)) {
            return R.fail(R.FORBIDDEN, "只有付款方才能确认完成");
        }
        return doComplete(order, userId, userId, "付款方确认完成，资金结算给收款方");
    }

    @Override
    @Transactional
    public R<TradeOrderVO> cancel(Long userId, Long orderId) {
        TradeOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return R.fail(R.NOT_FOUND, "订单不存在");
        }
        if (!order.getPayerId().equals(userId)) {
            return R.fail(R.FORBIDDEN, "只有付款方才能取消");
        }
        int rows = orderMapper.markCancelled(orderId);
        if (rows == 0) {
            TradeOrder latest = orderMapper.selectById(orderId);
            if (latest == null) {
                return R.fail(R.NOT_FOUND, "订单不存在");
            }
            if (latest.getStatus() == TradeConstants.ORDER_CANCELLED) {
                return R.ok(toVO(latest, null, userId));
            }
            if (latest.getStatus() >= TradeConstants.ORDER_ESCROW
                    && latest.getStatus() <= TradeConstants.ORDER_COMPLETED) {
                return R.fail("订单已支付，无法取消，请改用退款");
            }
            return R.fail("订单状态异常，无法取消（当前状态：" + statusName(latest.getStatus()) + "）");
        }
        order.setStatus(TradeConstants.ORDER_CANCELLED);
        releasePosting(order.getPostingId());
        recordEvent(order.getId(), TradeConstants.ORDER_PENDING, TradeConstants.ORDER_CANCELLED,
                userId, "付款方取消未支付订单");
        postingService.broadcastAfterCommit();
        return R.ok(toVO(order, null, userId));
    }

    @Override
    @Transactional
    public R<TradeOrderVO> refund(Long userId, Long orderId) {
        TradeOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return R.fail(R.NOT_FOUND, "订单不存在");
        }
        if (!order.getPayerId().equals(userId)) {
            return R.fail(R.FORBIDDEN, "只有付款方才能退款");
        }
        int rows = orderMapper.markRefunded(orderId);
        if (rows == 0) {
            TradeOrder latest = orderMapper.selectById(orderId);
            if (latest == null) {
                return R.fail(R.NOT_FOUND, "订单不存在");
            }
            if (latest.getStatus() == TradeConstants.ORDER_REFUNDED) {
                return R.ok(toVO(latest, null, userId));
            }
            return R.fail("只有托管中的订单可以退款（当前状态：" + statusName(latest.getStatus()) + "）");
        }
        // 退款：付款方冻结 -> 可用 + 落一张退款单 + 写流水
        walletService.refundToPayer(order);

        order.setStatus(TradeConstants.ORDER_REFUNDED);
        releasePosting(order.getPostingId());
        recordEvent(order.getId(), TradeConstants.ORDER_ESCROW, TradeConstants.ORDER_REFUNDED,
                userId, "付款方申请退款，托管资金解冻退回");
        postingService.broadcastAfterCommit();
        return R.ok(toVO(order, null, userId));
    }

    /**
     * 超时未支付 → 系统关单并释放发布单。到期时间由 markExpired 里的条件兜住，
     * 所以并发/重复调用都是安全的（第二次返回 0 行，直接当"没我什么事"）。
     */
    @Override
    @Transactional
    public boolean cancelExpired(Long orderId) {
        if (orderMapper.markExpired(orderId) == 0) {
            return false;
        }
        TradeOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return false;
        }
        order.setStatus(TradeConstants.ORDER_CANCELLED);
        releasePosting(order.getPostingId());
        recordEvent(order.getId(), TradeConstants.ORDER_PENDING, TradeConstants.ORDER_CANCELLED,
                TradeConstants.OPERATOR_SYSTEM, "超时未支付，系统自动取消并释放发布单");
        postingService.broadcastAfterCommit();
        return true;
    }

    /**
     * 超时未确认 → 系统自动确认并结算。
     * 出售：买家付了钱却一直不点"确认收货"（卖家永远拿不到钱）；
     * 寄养：照顾者已点"完成履约"、主人一直不点"确认结算"（照顾者永远拿不到钱）。
     * 两种情况都由系统兜底，避免资金永久卡在托管里。
     */
    @Override
    @Transactional
    public boolean autoConfirm(Long orderId) {
        TradeOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return false;
        }
        String remark = order.getCategory() == TradeConstants.CATEGORY_SALE
                ? "买家超时未确认收货，系统自动确认并结算给卖家"
                : "主人超时未确认结算，系统自动结算给照顾者";
        return doComplete(order, null, TradeConstants.OPERATOR_SYSTEM, remark).getCode() == R.SUCCESS;
    }

    @Override
    public R<TradeOrderDetailVO> detail(Long userId, Long orderId) {
        TradeOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            return R.fail(R.NOT_FOUND, "订单不存在");
        }
        if (userId == null || (!order.getPayerId().equals(userId) && !order.getPayeeId().equals(userId))) {
            return R.fail(R.FORBIDDEN, "无权查看该订单");
        }
        TradeOrderDetailVO vo = new TradeOrderDetailVO();
        vo.setOrder(toVO(order, postingMapper.selectById(order.getPostingId()), userId));

        List<Payment> payments = paymentMapper.selectList(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getOrderId, orderId).orderByAsc(Payment::getId));
        List<PaymentVO> payVOs = new ArrayList<>();
        for (Payment p : payments) {
            payVOs.add(toPaymentVO(p));
        }
        vo.setPayments(payVOs);
        vo.setEvents(tradeEventMapper.selectList(new LambdaQueryWrapper<TradeEvent>()
                .eq(TradeEvent::getOrderId, orderId).orderByAsc(TradeEvent::getId)));
        return R.ok(vo);
    }

    /**
     * 完成订单并结算。付款方确认（用户触发）与超时自动确认（系统触发）共用这一条链路：
     * CAS 成功才动钱——结算、审计、发布单收官都在同一事务里，不可能"订单完成了钱没动"。
     */
    private R<TradeOrderVO> doComplete(TradeOrder order, Long viewUserId, long operatorId, String remark) {
        int from = order.getCategory() == TradeConstants.CATEGORY_FOSTER
                ? TradeConstants.ORDER_SETTLING : TradeConstants.ORDER_ESCROW;
        int rows = orderMapper.markComplete(order.getId(), from);
        if (rows == 0) {
            return casMiss(order.getId(), viewUserId == null ? order.getPayerId() : viewUserId,
                    TradeConstants.ORDER_COMPLETED, "无法确认完成");
        }
        walletService.settleToPayee(order);

        order.setStatus(TradeConstants.ORDER_COMPLETED);
        order.setCompleteTime(LocalDateTime.now());
        closePosting(order.getPostingId());
        recordEvent(order.getId(), from, TradeConstants.ORDER_COMPLETED, operatorId, remark);
        postingService.broadcastAfterCommit();
        return R.ok(toVO(order, null, viewUserId == null ? order.getPayerId() : viewUserId));
    }

    /** 订单完成 → 发布单收官（已匹配 -> 已关闭）；CAS 失败必须留痕，不能静默 */
    private void closePosting(Long postingId) {
        int rows = postingMapper.casStatus(postingId, TradeConstants.POSTING_MATCHED, TradeConstants.POSTING_CLOSED);
        if (rows == 0) {
            log.warn("订单已完成但发布单未从「已匹配」关闭：postingId={}（可能已被下架/改状态），需人工核对", postingId);
        }
    }

    /** 订单取消/退款 → 发布单回到「开放中」；同样不能静默失败 */
    private void releasePosting(Long postingId) {
        int rows = postingMapper.casStatus(postingId, TradeConstants.POSTING_MATCHED, TradeConstants.POSTING_OPEN);
        if (rows == 0) {
            log.warn("订单已取消/退款但发布单未回到「开放中」：postingId={}（可能已被下架/改状态），需人工核对", postingId);
        }
    }

    /**
     * CAS 没命中时的统一出口：
     *   已经是目标状态或更后置（重复点击 / 网络重试）→ 幂等返回当前订单，用户不会看到假失败；
     *   其他情况 → 给出带"当前状态"的可读原因。
     */
    private R<TradeOrderVO> casMiss(Long orderId, Long viewUserId, int target, String action) {
        TradeOrder latest = orderMapper.selectById(orderId);
        if (latest == null) {
            return R.fail(R.NOT_FOUND, "订单不存在");
        }
        if (reached(latest.getStatus(), target)) {
            return R.ok(toVO(latest, null, viewUserId));
        }
        return R.fail(action + "（当前状态：" + statusName(latest.getStatus()) + "）");
    }

    /**
     * 重试幂等判定：当前状态是否已经"到达或越过"目标状态。
     * 进度线：0待支付 → 1托管中 → 2履约中 → 3待结算 → 4已完成；5已取消 / 6已退款 是终点，互不越过。
     */
    private boolean reached(int current, int target) {
        switch (target) {
            case TradeConstants.ORDER_ESCROW:
                return current >= TradeConstants.ORDER_ESCROW && current <= TradeConstants.ORDER_COMPLETED;
            case TradeConstants.ORDER_SERVING:
                return current == TradeConstants.ORDER_SERVING
                        || current == TradeConstants.ORDER_SETTLING
                        || current == TradeConstants.ORDER_COMPLETED;
            case TradeConstants.ORDER_SETTLING:
                return current == TradeConstants.ORDER_SETTLING || current == TradeConstants.ORDER_COMPLETED;
            case TradeConstants.ORDER_COMPLETED:
                return current == TradeConstants.ORDER_COMPLETED;
            case TradeConstants.ORDER_CANCELLED:
                return current == TradeConstants.ORDER_CANCELLED;
            case TradeConstants.ORDER_REFUNDED:
                return current == TradeConstants.ORDER_REFUNDED;
            default:
                return current == target;
        }
    }

    private String statusName(int status) {
        switch (status) {
            case TradeConstants.ORDER_PENDING:   return "待支付";
            case TradeConstants.ORDER_ESCROW:    return "托管中";
            case TradeConstants.ORDER_SERVING:   return "履约中";
            case TradeConstants.ORDER_SETTLING:  return "待结算";
            case TradeConstants.ORDER_COMPLETED: return "已完成";
            case TradeConstants.ORDER_CANCELLED: return "已取消";
            case TradeConstants.ORDER_REFUNDED:  return "已退款";
            default: return "未知(" + status + ")";
        }
    }

    /** 每次状态流转（含系统自动动作）都留一条审计 */
    private void recordEvent(Long orderId, Integer fromStatus, int toStatus, long operatorId, String remark) {
        TradeEvent event = new TradeEvent();
        event.setOrderId(orderId);
        event.setFromStatus(fromStatus);
        event.setToStatus(toStatus);
        event.setOperatorId(operatorId);
        event.setRemark(remark);
        tradeEventMapper.insert(event);
    }

    @Override
    public R<List<TradeOrderVO>> myOrders(Long userId) {
        List<TradeOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<TradeOrder>()
                .and(w -> w.eq(TradeOrder::getPayerId, userId).or().eq(TradeOrder::getPayeeId, userId))
                .orderByDesc(TradeOrder::getCreateTime));
        // 一次性把这一批订单的支付单号 + 发布单捞出来，避免在循环里逐单查库（N+1）
        Map<Long, String> payNos = new HashMap<>();
        Map<Long, String> refundNos = new HashMap<>();
        Map<Long, Posting> postingMap = new HashMap<>();
        if (!orders.isEmpty()) {
            List<Long> ids = new ArrayList<>();
            List<Long> postingIds = new ArrayList<>();
            for (TradeOrder o : orders) {
                ids.add(o.getId());
                if (o.getPostingId() != null) {
                    postingIds.add(o.getPostingId());
                }
            }
            List<Payment> payments = paymentMapper.selectList(
                    new LambdaQueryWrapper<Payment>().in(Payment::getOrderId, ids));
            for (Payment pay : payments) {
                if (pay.getBizType() == TradeConstants.PAY_BIZ_PAY) {
                    payNos.put(pay.getOrderId(), pay.getPaymentNo());
                } else if (pay.getBizType() == TradeConstants.PAY_BIZ_REFUND) {
                    refundNos.put(pay.getOrderId(), pay.getPaymentNo());
                }
            }
            if (!postingIds.isEmpty()) {
                for (Posting p : postingMapper.selectBatchIds(postingIds)) {
                    postingMap.put(p.getId(), p);
                }
            }
        }

        List<TradeOrderVO> vos = new ArrayList<>();
        for (TradeOrder o : orders) {
            vos.add(toVO(o, postingMap.get(o.getPostingId()), userId, payNos, refundNos));
        }
        return R.ok(vos);
    }

    /**
     * 订单号：MKP + 时间戳 + 6 位随机。
     * 同秒并发撞 uk_order_no 时重新生成重试（最多 3 次），不能让一次撞号变成用户的 500。
     */
    private void insertWithRetry(TradeOrder order) {
        for (int attempt = 1; ; attempt++) {
            order.setOrderNo("MKP" + LocalDateTime.now().format(ORDER_NO_FMT)
                    + String.format("%06d", ThreadLocalRandom.current().nextInt(1000000)));
            try {
                orderMapper.insert(order);
                return;
            } catch (DuplicateKeyException e) {
                if (attempt >= 3) {
                    throw new BizException("订单号生成冲突，请重试");
                }
                log.warn("订单号冲突，重试第 {} 次：orderNo={}", attempt, order.getOrderNo());
            }
        }
    }

    private TradeOrderVO toVO(TradeOrder order, Posting posting, Long currentUserId) {
        return toVO(order, posting, currentUserId, null, null);
    }

    private TradeOrderVO toVO(TradeOrder order, Posting posting, Long currentUserId,
                              Map<Long, String> payNos, Map<Long, String> refundNos) {
        TradeOrderVO vo = new TradeOrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setPostingId(order.getPostingId());
        vo.setApplyId(order.getApplyId());
        vo.setCategory(order.getCategory());
        vo.setPayerId(order.getPayerId());
        vo.setPayeeId(order.getPayeeId());
        vo.setAmount(order.getAmount());
        vo.setStatus(order.getStatus());
        vo.setPayTime(order.getPayTime());
        vo.setCompleteTime(order.getCompleteTime());
        vo.setExpireTime(order.getExpireTime());
        vo.setCreateTime(order.getCreateTime());
        if (currentUserId != null) {
            vo.setCounterPartyId(order.getPayerId().equals(currentUserId)
                    ? order.getPayeeId() : order.getPayerId());
        }
        if (posting != null) {
            vo.setPostingPetName(posting.getPetName());
            String images = posting.getImages();
            vo.setPostingImage(images != null && !images.isEmpty() ? images.split(",")[0] : null);
        }
        if (order.getId() != null) {
            if (payNos != null) {
                vo.setPaymentNo(payNos.get(order.getId()));
                vo.setRefundNo(refundNos.get(order.getId()));
            } else {
                fillPaymentNos(vo, order.getId());
            }
        }
        return vo;
    }

    private PaymentVO toPaymentVO(Payment p) {
        PaymentVO vo = new PaymentVO();
        vo.setId(p.getId());
        vo.setPaymentNo(p.getPaymentNo());
        vo.setOrderId(p.getOrderId());
        vo.setBizType(p.getBizType());
        vo.setAmount(p.getAmount());
        vo.setStatus(p.getStatus());
        vo.setChannel(p.getChannel());
        vo.setOutTradeNo(p.getOutTradeNo());
        vo.setErrorMsg(p.getErrorMsg());
        vo.setPayTime(p.getPayTime());
        vo.setCreateTime(p.getCreateTime());
        return vo;
    }

    /** 单个订单：直接查一次支付单，把"钱动过没有"的凭据带出去 */
    private void fillPaymentNos(TradeOrderVO vo, Long orderId) {
        List<Payment> payments = paymentMapper.selectList(
                new LambdaQueryWrapper<Payment>().eq(Payment::getOrderId, orderId));
        for (Payment pay : payments) {
            if (pay.getBizType() == TradeConstants.PAY_BIZ_PAY) {
                vo.setPaymentNo(pay.getPaymentNo());
            } else if (pay.getBizType() == TradeConstants.PAY_BIZ_REFUND) {
                vo.setRefundNo(pay.getPaymentNo());
            }
        }
    }
}

