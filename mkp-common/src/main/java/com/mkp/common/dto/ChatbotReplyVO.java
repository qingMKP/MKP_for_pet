package com.mkp.common.dto;

import java.util.List;

public class ChatbotReplyVO {
    private String content;
    private List<ChatbotPostVO> posts;
    private List<PostingVO> postings;
    private Long conversationId;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public List<ChatbotPostVO> getPosts() { return posts; }
    public void setPosts(List<ChatbotPostVO> posts) { this.posts = posts; }
    public List<PostingVO> getPostings() { return postings; }
    public void setPostings(List<PostingVO> postings) { this.postings = postings; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
}
