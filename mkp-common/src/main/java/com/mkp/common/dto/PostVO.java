package com.mkp.common.dto;

import java.time.LocalDateTime;
import java.util.List;

public class PostVO {
    private Long id;
    private String content;
    private List<String> images;
    private Integer viewCount;
    private Integer likeCount;
    private Boolean liked;
    private LocalDateTime createTime;
    private UserVO author;
    /** 命中的帖子文案高亮片段（后端已转义，只保留 mark 标记），未命中时为 null */
    private String contentHighlight;
    /** 命中的作者昵称高亮片段，未命中时为 null */
    private String authorHighlight;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public List<String> getImages() { return images; }
    public void setImages(List<String> images) { this.images = images; }
    public Integer getViewCount() { return viewCount; }
    public void setViewCount(Integer viewCount) { this.viewCount = viewCount; }
    public Integer getLikeCount() { return likeCount; }
    public void setLikeCount(Integer likeCount) { this.likeCount = likeCount; }
    public Boolean getLiked() { return liked; }
    public void setLiked(Boolean liked) { this.liked = liked; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public UserVO getAuthor() { return author; }
    public void setAuthor(UserVO author) { this.author = author; }
    public String getContentHighlight() { return contentHighlight; }
    public void setContentHighlight(String contentHighlight) { this.contentHighlight = contentHighlight; }
    public String getAuthorHighlight() { return authorHighlight; }
    public void setAuthorHighlight(String authorHighlight) { this.authorHighlight = authorHighlight; }
}
