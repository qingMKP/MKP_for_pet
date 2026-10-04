package com.mkp.pet.service;

import com.mkp.common.dto.WalletVO;
import com.mkp.common.entity.TradeOrder;
import com.mkp.common.result.R;

import java.math.BigDecimal;

/**
 * 钱包服务。五个动作：一个查询、一个调试入口、三个资金动作。
 *
 * 三个资金动作（freezeForPay / settleToPayee / refundToPayer）都是"内嵌在订单事务里"的，
 * 只能由 TradeOrderServiceImpl 在订单状态 CAS 成功之后调用；失败一律抛 BizException 让整个事务回滚。
 */
public interface WalletService {

    /** 我的钱包：余额 / 冻结 / 合计 + 最近 50 条流水 */
    R<WalletVO> myWallet(Long userId, int logLimit);

    /** 手动设置可用余额（仅调试，走 /api/admin/wallet/set，网关已限定 ADMIN） */
    R<WalletVO> setBalance(Long userId, BigDecimal balance, String remark);

    /** 支付：付款方 可用 -> 冻结，并落一张支付单 */
    void freezeForPay(TradeOrder order);

    /** 确认完成：付款方冻结扣出 -> 收款方可用到账 */
    void settleToPayee(TradeOrder order);

    /** 退款：付款方冻结 -> 可用，并落一张退款单 */
    void refundToPayer(TradeOrder order);
}
