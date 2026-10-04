package com.mkp.common.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TradeOrderVO {
    private Long id;
    private String orderNo;
    private Long postingId;
    private Long applyId;
    private Integer category;
    private Long payerId;
    private Long payeeId;
    private BigDecimal amount;
    private Integer status;
    private LocalDateTime payTime;
    private LocalDateTime completeTime;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
    // 凭据：支付单号 / 退款单号（来自 mkp_payment，说明"这笔钱真的动过"）
    private String paymentNo;
    private String refundNo;
    // 快照
    private String postingPetName;
    private String postingImage;
    private Long counterPartyId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getPostingId() { return postingId; }
    public void setPostingId(Long postingId) { this.postingId = postingId; }
    public Long getApplyId() { return applyId; }
    public void setApplyId(Long applyId) { this.applyId = applyId; }
    public Integer getCategory() { return category; }
    public void setCategory(Integer category) { this.category = category; }
    public Long getPayerId() { return payerId; }
    public void setPayerId(Long payerId) { this.payerId = payerId; }
    public Long getPayeeId() { return payeeId; }
    public void setPayeeId(Long payeeId) { this.payeeId = payeeId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getPayTime() { return payTime; }
    public void setPayTime(LocalDateTime payTime) { this.payTime = payTime; }
    public LocalDateTime getCompleteTime() { return completeTime; }
    public void setCompleteTime(LocalDateTime completeTime) { this.completeTime = completeTime; }
    public LocalDateTime getExpireTime() { return expireTime; }
    public void setExpireTime(LocalDateTime expireTime) { this.expireTime = expireTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public String getPaymentNo() { return paymentNo; }
    public void setPaymentNo(String paymentNo) { this.paymentNo = paymentNo; }
    public String getRefundNo() { return refundNo; }
    public void setRefundNo(String refundNo) { this.refundNo = refundNo; }
    public String getPostingPetName() { return postingPetName; }
    public void setPostingPetName(String postingPetName) { this.postingPetName = postingPetName; }
    public String getPostingImage() { return postingImage; }
    public void setPostingImage(String postingImage) { this.postingImage = postingImage; }
    public Long getCounterPartyId() { return counterPartyId; }
    public void setCounterPartyId(Long counterPartyId) { this.counterPartyId = counterPartyId; }
}
