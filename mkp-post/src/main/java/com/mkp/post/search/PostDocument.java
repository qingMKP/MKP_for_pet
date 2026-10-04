package com.mkp.post.search;

import java.time.LocalDateTime;

/**
 * 帖子搜索文档：ES 索引里的一条记录。
 *
 * content（帖子文案）与 authorNickname（作者昵称）是参与全文检索的两个字段。
 * 作者昵称不在帖子表里（在用户服务），所以建索引时必须把昵称冗余进文档，
 * 否则没法按作者名字搜。
 */
public class PostDocument {

    private Long id;
    private Long userId;
    private String content;
    private String authorNickname;
    private String images;
    private Integer likeCount;
    private Integer viewCount;
    private Integer status;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getAuthorNickname() { return authorNickname; }
    public void setAuthorNickname(String authorNickname) { this.authorNickname = authorNickname; }

    public String getImages() { return images; }
    public void setImages(String images) { this.images = images; }

    public Integer getLikeCount() { return likeCount; }
    public void setLikeCount(Integer likeCount) { this.likeCount = likeCount; }

    public Integer getViewCount() { return viewCount; }
    public void setViewCount(Integer viewCount) { this.viewCount = viewCount; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
