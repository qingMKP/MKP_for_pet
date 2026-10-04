package com.mkp.common.dto;

/**
 * 申请入参。
 * 没有 amount 字段：订单金额只由发布单价格决定（发布时必须定价），申请人不再报价。
 */
public class ApplySaveDTO {
    private String requestId;
    private String message;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
