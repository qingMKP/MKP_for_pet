package com.mkp.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 发布单通知台账（mkp_posting_notify）。
 * uk_posting_notify(posting_id, notify_type) 是幂等键：同一个帖子同一类通知只会有一条记录，
 * 靠它保证"过期提醒只发一次"（多实例并发也只有一个能抢到发送权）。
 */
@TableName("mkp_posting_notify")
public class PostingNotify {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postingId;
    private Long toUserId;
    private Integer notifyType;
    /** 0=待发送 1=已发送 2=发送中（发送中的记录超过 10 分钟可被重新抢占） */
    private Integer status;
    private Integer retryCount;
    private String lastError;
    private LocalDateTime sentTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPostingId() { return postingId; }
    public void setPostingId(Long postingId) { this.postingId = postingId; }
    public Long getToUserId() { return toUserId; }
    public void setToUserId(Long toUserId) { this.toUserId = toUserId; }
    public Integer getNotifyType() { return notifyType; }
    public void setNotifyType(Integer notifyType) { this.notifyType = notifyType; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public LocalDateTime getSentTime() { return sentTime; }
    public void setSentTime(LocalDateTime sentTime) { this.sentTime = sentTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
