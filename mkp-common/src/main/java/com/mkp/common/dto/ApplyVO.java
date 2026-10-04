package com.mkp.common.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ApplyVO {
    private Long id;
    private Long postingId;
    private Long applicantId;
    private String message;
    private Integer status;
    private LocalDateTime createTime;
    // 发布单快照（列表展示用）
    private String postingPetName;
    private String postingImage;
    private Integer postingCategory;
    private Integer postingDirection;
    private BigDecimal postingPrice;
    private Integer postingPriceUnit;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPostingId() { return postingId; }
    public void setPostingId(Long postingId) { this.postingId = postingId; }
    public Long getApplicantId() { return applicantId; }
    public void setApplicantId(Long applicantId) { this.applicantId = applicantId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public String getPostingPetName() { return postingPetName; }
    public void setPostingPetName(String postingPetName) { this.postingPetName = postingPetName; }
    public String getPostingImage() { return postingImage; }
    public void setPostingImage(String postingImage) { this.postingImage = postingImage; }
    public Integer getPostingCategory() { return postingCategory; }
    public void setPostingCategory(Integer postingCategory) { this.postingCategory = postingCategory; }
    public Integer getPostingDirection() { return postingDirection; }
    public void setPostingDirection(Integer postingDirection) { this.postingDirection = postingDirection; }
    public BigDecimal getPostingPrice() { return postingPrice; }
    public void setPostingPrice(BigDecimal postingPrice) { this.postingPrice = postingPrice; }
    public Integer getPostingPriceUnit() { return postingPriceUnit; }
    public void setPostingPriceUnit(Integer postingPriceUnit) { this.postingPriceUnit = postingPriceUnit; }
}
