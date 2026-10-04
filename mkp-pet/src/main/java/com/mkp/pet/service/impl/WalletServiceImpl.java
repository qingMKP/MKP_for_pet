package com.mkp.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mkp.common.constant.TradeConstants;
import com.mkp.common.dto.WalletVO;
import com.mkp.common.entity.Payment;
import com.mkp.common.entity.TradeOrder;
import com.mkp.common.entity.Wallet;
import com.mkp.common.entity.WalletLog;
import com.mkp.common.exception.BizException;
import com.mkp.common.result.R;
import com.mkp.pet.mapper.PaymentMapper;
import com.mkp.pet.mapper.WalletLogMapper;
import com.mkp.pet.mapper.WalletMapper;
import com.mkp.pet.service.WalletService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class WalletServiceImpl implements WalletService {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final WalletMapper walletMapper;
    private final WalletLogMapper walletLogMapper;
    private final PaymentMapper paymentMapper;

    public WalletServiceImpl(WalletMapper walletMapper, WalletLogMapper walletLogMapper,
                             PaymentMapper paymentMapper) {
        this.walletMapper = walletMapper;
        this.walletLogMapper = walletLogMapper;
        this.paymentMapper = paymentMapper;
    }

    @Override
    public R<WalletVO> myWallet(Long userId, int logLimit) {
        if (userId == null) {
            return R.fail(R.UNAUTHORIZED, "未登录");
        }
        walletMapper.ensureRow(userId);
        return R.ok(buildVO(userId, logLimit));
    }

    @Override
    @Transactional
    public R<WalletVO> setBalance(Long userId, BigDecimal balance, String remark) {
        if (userId == null) {
            return R.fail(R.UNAUTHORIZED, "未登录");
        }
        if (balance == null || balance.signum() < 0) {
            return R.fail("余额不能为空且不能为负数");
        }
        walletMapper.ensureRow(userId);
        Wallet before = walletMapper.selectOne(new LambdaQueryWrapper<Wallet>().eq(Wallet::getUserId, userId));
        walletMapper.setBalance(userId, balance);
        Wallet after = walletMapper.selectOne(new LambdaQueryWrapper<Wallet>().eq(Wallet::getUserId, userId));

        // 手动调整也留痕：账本里必须能看出"这个余额是被人为设定的"
        BigDecimal delta = after.getBalance().subtract(before.getBalance());
        writeLog(userId, null, TradeConstants.WALLET_BIZ_ADJUST, delta.abs(), after,
                "手动设置余额：" + before.getBalance() + " -> " + after.getBalance()
                        + (remark != null && !remark.isEmpty() ? "（" + remark + "）" : ""));
        return R.ok(buildVO(userId, 50));
    }

    @Override
    public void freezeForPay(TradeOrder order) {
        Long payerId = order.getPayerId();
        BigDecimal amount = nz(order.getAmount());
        walletMapper.ensureRow(payerId);

        if (walletMapper.freezeForPay(payerId, amount) == 0) {
            Wallet wallet = findWallet(payerId);
            throw new BizException("余额不足：需要 " + amount + "，当前可用 " + (wallet == null ? "0.00" : wallet.getBalance()));
        }
        Wallet payer = findWallet(payerId);

        insertPayment(order, TradeConstants.PAY_BIZ_PAY, amount);
        writeLog(payerId, order.getId(), TradeConstants.WALLET_BIZ_PAY, amount, payer,
                "支付冻结：可用 -" + amount + "，冻结 +" + amount);
    }

    @Override
    public void settleToPayee(TradeOrder order) {
        Long payerId = order.getPayerId();
        Long payeeId = order.getPayeeId();
        BigDecimal amount = nz(order.getAmount());
        walletMapper.ensureRow(payerId);
        walletMapper.ensureRow(payeeId);

        if (walletMapper.releaseFrozen(payerId, amount) == 0) {
            throw new BizException("托管资金异常：付款方冻结金额不足 " + amount + "，无法结算");
        }
        walletMapper.addBalance(payeeId, amount);

        writeLog(payerId, order.getId(), TradeConstants.WALLET_BIZ_SETTLE, amount, findWallet(payerId),
                "结算出账：冻结 -" + amount + "，转给收款方 #" + payeeId);
        writeLog(payeeId, order.getId(), TradeConstants.WALLET_BIZ_SETTLE, amount, findWallet(payeeId),
                "结算入账：可用 +" + amount + "，来自付款方 #" + payerId);
    }

    @Override
    public void refundToPayer(TradeOrder order) {
        Long payerId = order.getPayerId();
        BigDecimal amount = nz(order.getAmount());
        walletMapper.ensureRow(payerId);

        if (walletMapper.refundFrozen(payerId, amount) == 0) {
            throw new BizException("退款失败：付款方冻结金额不足 " + amount + "，无法退回");
        }
        Wallet payer = findWallet(payerId);

        insertPayment(order, TradeConstants.PAY_BIZ_REFUND, amount);
        writeLog(payerId, order.getId(), TradeConstants.WALLET_BIZ_REFUND, amount, payer,
                "退款解冻：冻结 -" + amount + "，可用 +" + amount);
    }

    private void insertPayment(TradeOrder order, int bizType, BigDecimal amount) {
        Payment payment = new Payment();
        payment.setPaymentNo(generatePaymentNo(bizType));
        payment.setOrderId(order.getId());
        payment.setBizType(bizType);
        payment.setPayerId(order.getPayerId());
        payment.setPayeeId(order.getPayeeId());
        payment.setAmount(amount);
        payment.setStatus(TradeConstants.PAY_STATUS_SUCCESS);
        payment.setChannel(TradeConstants.PAY_CHANNEL_WALLET);
        payment.setOutTradeNo(payment.getPaymentNo());
        payment.setErrorMsg("");
        payment.setPayTime(LocalDateTime.now());
        paymentMapper.insert(payment);
    }

    private String generatePaymentNo(int bizType) {
        String prefix = bizType == TradeConstants.PAY_BIZ_REFUND ? "R" : "P";
        return prefix + LocalDateTime.now().format(NO_FMT)
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
    }

    private void writeLog(Long userId, Long orderId, int bizType, BigDecimal amount, Wallet wallet, String remark) {
        WalletLog log = new WalletLog();
        log.setUserId(userId);
        log.setOrderId(orderId);
        log.setBizType(bizType);
        log.setAmount(amount);
        log.setBalanceAfter(wallet == null ? BigDecimal.ZERO : wallet.getBalance());
        log.setFrozenAfter(wallet == null ? BigDecimal.ZERO : wallet.getFrozen());
        log.setRemark(remark);
        walletLogMapper.insert(log);
    }

    private Wallet findWallet(Long userId) {
        return walletMapper.selectOne(new LambdaQueryWrapper<Wallet>().eq(Wallet::getUserId, userId));
    }

    private WalletVO buildVO(Long userId, int logLimit) {
        Wallet wallet = findWallet(userId);
        WalletVO vo = new WalletVO();
        vo.setUserId(userId);
        BigDecimal balance = wallet == null ? BigDecimal.ZERO : wallet.getBalance();
        BigDecimal frozen = wallet == null ? BigDecimal.ZERO : wallet.getFrozen();
        vo.setBalance(balance);
        vo.setFrozen(frozen);
        vo.setTotal(balance.add(frozen));

        List<WalletLog> logs = walletLogMapper.selectList(new LambdaQueryWrapper<WalletLog>()
                .eq(WalletLog::getUserId, userId)
                .orderByDesc(WalletLog::getId)
                .last("LIMIT " + Math.max(1, Math.min(logLimit, 200))));
        vo.setLogs(logs);
        return vo;
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
