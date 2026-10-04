package com.mkp.post.search;

/**
 * 帖子索引变更事件。
 *
 * 只带 postId：真正写索引时由监听器回查数据库拿最新状态，
 * 这样帖子被软删除（status=2）时也能自动把索引里的文档清掉，不用区分增 / 删 / 改。
 */
public class PostIndexEvent {

    private final Long postId;

    public PostIndexEvent(Long postId) {
        this.postId = postId;
    }

    public Long getPostId() { return postId; }
}
