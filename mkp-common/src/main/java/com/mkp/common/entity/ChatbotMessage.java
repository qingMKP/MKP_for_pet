package com.mkp.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("mkp_chatbot_message")
public class ChatbotMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private String role;
    private String content;
    @TableField("posts_json")
    private String postsJson;
    @TableField("postings_json")
    private String postingsJson;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getPostsJson() { return postsJson; }
    public void setPostsJson(String postsJson) { this.postsJson = postsJson; }
    public String getPostingsJson() { return postingsJson; }
    public void setPostingsJson(String postingsJson) { this.postingsJson = postingsJson; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
