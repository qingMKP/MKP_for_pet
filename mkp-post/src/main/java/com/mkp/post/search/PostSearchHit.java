package com.mkp.post.search;

/**
 * 单条搜索命中：帖子 id + 高亮片段。
 *
 * 高亮片段已经在后端做过 HTML 转义（只保留 mark 标记），前端可以安全地按 HTML 渲染。
 */
public class PostSearchHit {

    private final Long id;
    private final String contentHighlight;
    private final String authorHighlight;

    public PostSearchHit(Long id, String contentHighlight, String authorHighlight) {
        this.id = id;
        this.contentHighlight = contentHighlight;
        this.authorHighlight = authorHighlight;
    }

    public Long getId() { return id; }
    public String getContentHighlight() { return contentHighlight; }
    public String getAuthorHighlight() { return authorHighlight; }
}
