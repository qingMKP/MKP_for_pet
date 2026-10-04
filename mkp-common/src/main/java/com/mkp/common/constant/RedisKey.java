package com.mkp.common.constant;

public class RedisKey {
    public static final String FRIEND_REQUEST_PREFIX = "friend:request:";
    public static final String ONLINE_USER_PREFIX = "online:user:";
    public static final String PET_ORDER_TOKEN_PREFIX = "pet:order:token:";
    public static final String POSTING_APPLY_TOKEN_PREFIX = "trade:apply:token:";

    /** 待刷新 ES 索引的作者 id 集合：用户改昵称时由 mkp-user 写入，mkp-post 定时消费 */
    public static final String POST_AUTHOR_INDEX_DIRTY = "post:author:index:dirty";
}
