package com.mkp.post.search;

import java.util.List;

/** 一次检索的结果：命中列表 + 总命中数 */
public class PostSearchResult {

    private final List<PostSearchHit> hits;
    private final long total;

    public PostSearchResult(List<PostSearchHit> hits, long total) {
        this.hits = hits;
        this.total = total;
    }

    public List<PostSearchHit> getHits() { return hits; }
    public long getTotal() { return total; }
}
