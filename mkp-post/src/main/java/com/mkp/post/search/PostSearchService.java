package com.mkp.post.search;

import com.mkp.common.entity.Post;

/**
 * 帖子检索（Elasticsearch）。
 *
 * 所有方法在 ES 不可用时都会抛异常或返回 false，调用方需自行降级（走 MySQL LIKE）。
 */
public interface PostSearchService {

    /** ES 是否可用；不可用时调用方应走 MySQL 降级 */
    boolean isAvailable();

    /** 确保索引存在（应用启动时调用，失败只记日志，不阻断启动） */
    void initIndex();

    /** 把一条帖子组装成搜索文档（会补齐作者昵称） */
    PostDocument buildDocument(Post post);

    /** 写入 / 覆盖一条文档 */
    void save(PostDocument document);

    /** 删除一条文档（文档不存在也算成功） */
    void delete(Long postId);

    /**
     * 全文检索：帖子文案 + 作者昵称，可叠加条件筛选。
     *
     * @param sort     relevance（相关度）/ latest（最新）/ hot（最热）
     * @param hasImage true 只看有图 / false 只看纯文字 / null 不限
     * @param topic    #话题 精确筛选（内容里的 #xxx），null 或空表示不限
     */
    PostSearchResult search(String keyword, int page, int size, String sort, Boolean hasImage, String topic);

    /** 某个作者的帖子索引整体刷新（用户改昵称后调用），返回刷新的条数 */
    long refreshByAuthor(Long userId);

    /** 全量重建索引，返回写入条数 */
    long rebuildAll();

    /** 索引内文档数；-1 表示 ES 不可用 */
    long count();

    /** 健康状态描述（给管理端看） */
    String describeStatus();
}
