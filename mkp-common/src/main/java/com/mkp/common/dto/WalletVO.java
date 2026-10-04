package com.mkp.common.dto;

import com.mkp.common.entity.WalletLog;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 我的钱包视图：余额 + 冻结 + 合计 + 最近流水。
 *
 * 流水这里直接复用 WalletLog 实体而没有再包一层 VO：日志字段全是展示安全的，
 * 再写一个字段完全一致的 VO 只是多 60 行样板代码，没有边界收益。
 */
public class WalletVO {
    private Long userId;
    private BigDecimal balance;
    private BigDecimal frozen;
    /** 账户总额 = balance + frozen */
    private BigDecimal total;
    private List<WalletLog> logs = new ArrayList<>();

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public BigDecimal getFrozen() { return frozen; }
    public void setFrozen(BigDecimal frozen) { this.frozen = frozen; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public List<WalletLog> getLogs() { return logs; }
    public void setLogs(List<WalletLog> logs) { this.logs = logs; }
}
