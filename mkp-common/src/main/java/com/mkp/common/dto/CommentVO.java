package com.mkp.common.dto;

import java.time.LocalDateTime;
import java.util.List;

public class CommentVO {
    private Long id;
    private String content;
    private Integer likeCount;
    private Boolean liked;
    private Long parentId;
    private LocalDateTime createTime;
    private UserVO author;
    private List<CommentVO> replies;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getLikeCount() { return likeCount; }
    public void setLikeCount(Integer likeCount) { this.likeCount = likeCount; }
    public Boolean getLiked() { return liked; }
    public void setLiked(Boolean liked) { this.liked = liked; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public UserVO getAuthor() { return author; }
    public void setAuthor(UserVO author) { this.author = author; }
    public List<CommentVO> getReplies() { return replies; }
    public void setReplies(List<CommentVO> replies) { this.replies = replies; }
}
