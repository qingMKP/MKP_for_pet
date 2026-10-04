package com.mkp.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 钱包流水（不可变账本）：只插入，永不更新或删除。
 *
 * 一条流水 = 某个账户上发生的一次资金变化，用 bizType 表达语义：
 *   1 手动调整 / 2 支付冻结(可用→冻结) / 3 结算入账(冻结→收款方可用) / 4 退款解冻(冻结→可用)
 * balanceAfter + frozenAfter 是变动后的快照，用来做"最后一条流水 == 当前钱包"的对账校验。
 *
 * 没有 direction 字段：可用与冻结之间互转时"方向"是歧义的（支付时账户总额并没变），
 * bizType + 两个 after 快照已经能完整还原账户状态，多一个 direction 只会制造自相矛盾的记录。
 */
@TableName("mkp_wallet_log")
public class WalletLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long orderId;
    private Integer bizType;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private BigDecimal frozenAfter;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Integer getBizType() { return bizType; }
    public void setBizType(Integer bizType) { this.bizType = bizType; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(BigDecimal balanceAfter) { this.balanceAfter = balanceAfter; }
    public BigDecimal getFrozenAfter() { return frozenAfter; }
    public void setFrozenAfter(BigDecimal frozenAfter) { this.frozenAfter = frozenAfter; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
