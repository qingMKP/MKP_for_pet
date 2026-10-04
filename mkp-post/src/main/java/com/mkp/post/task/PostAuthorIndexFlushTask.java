package com.mkp.post.task;

import com.mkp.common.constant.RedisKey;
import com.mkp.post.search.PostSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 作者索引刷新任务：用户改了昵称之后，把他在索引里所有帖子的 authorNickname 刷成新值。
 *
 * 跨服务不改名接口的延迟：mkp-user 改名时只往 Redis 集合里塞一个作者 id，
 * 这里定时消费（与浏览量落库同一套「脏集合 + 定时处理」思路）。
 * 并发安全：先 SREM 摘标记，谁摘到谁负责，多实例不会重复处理；失败再放回去，下轮重试。
 */
@Slf4j
@Component
public class PostAuthorIndexFlushTask {

    private final StringRedisTemplate stringRedisTemplate;
    private final PostSearchService postSearchService;

    public PostAuthorIndexFlushTask(StringRedisTemplate stringRedisTemplate,
                                    PostSearchService postSearchService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.postSearchService = postSearchService;
    }

    @Scheduled(fixedDelayString = "${mkp.post-author-index.flush-delay-ms:3000}")
    public void flushAuthorIndex() {
        Set<String> ids = stringRedisTemplate.opsForSet().members(RedisKey.POST_AUTHOR_INDEX_DIRTY);
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (String idStr : ids) {
            stringRedisTemplate.opsForSet().remove(RedisKey.POST_AUTHOR_INDEX_DIRTY, idStr);
            Long userId;
            try {
                userId = Long.parseLong(idStr);
            } catch (NumberFormatException e) {
                continue;
            }
            try {
                long refreshed = postSearchService.refreshByAuthor(userId);
                if (refreshed > 0) {
                    log.info("[post-search] 作者 {} 昵称变更，已刷新 {} 条帖子索引", userId, refreshed);
                }
            } catch (Exception e) {
                stringRedisTemplate.opsForSet().add(RedisKey.POST_AUTHOR_INDEX_DIRTY, idStr);
                log.error("[post-search] 刷新作者索引失败, userId={}", userId, e);
            }
        }
    }
}
