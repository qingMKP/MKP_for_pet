package com.mkp.common.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

/**
 * 文档切片。
 * parentId = 0 表示父块（按标题切出来的整节），parentId > 0 表示子块（父块再按长度切出来的小段）。
 * 只有子块参与向量化与召回，命中后回查父块给大模型。
 */
@TableName("mkp_chatbot_chunk")
public class ChatbotChunk {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private Long parentId;
    private Integer seq;
    private String sectionTitle;
    private String content;
    private Integer charCount;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Integer getSeq() { return seq; }
    public void setSeq(Integer seq) { this.seq = seq; }
    public String getSectionTitle() { return sectionTitle; }
    public void setSectionTitle(String sectionTitle) { this.sectionTitle = sectionTitle; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getCharCount() { return charCount; }
    public void setCharCount(Integer charCount) { this.charCount = charCount; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
