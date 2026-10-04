package com.mkp.post.task;

import com.mkp.post.mapper.PostMapper;
import com.mkp.post.service.PostViewCounterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 定时刷库任务：把 Redis 里攒的浏览量增量批量落库。
 * 并发安全靠"先 SREM 摘标记 + GETDEL 原子取钱"（多实例不重复计数）；
 * 失败恢复靠"放回计数器 + 重新 SADD"（下轮自动重试，不丢数）。
 */
@Slf4j
@Component
public class PostViewFlushTask {

    private final PostMapper postMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public PostViewFlushTask(PostMapper postMapper, StringRedisTemplate stringRedisTemplate) {
        this.postMapper = postMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Scheduled(fixedDelayString = "${mkp.post-view.flush-delay-ms:5000}")
    public void flushViewCount() {
        Set<String> ids = stringRedisTemplate.opsForSet().members(PostViewCounterService.DIRTY_SET_KEY);
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (String idStr : ids) {
            flushOne(idStr);
        }
    }

    private void flushOne(String idStr) {
        Long postId;
        try {
            postId = Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            // 脏数据：清掉标记，避免卡在集合里反复报错
            stringRedisTemplate.opsForSet().remove(PostViewCounterService.DIRTY_SET_KEY, idStr);
            return;
        }
        // 先摘脏标记：谁摘到谁负责，多实例同时跑也不会重复处理
        stringRedisTemplate.opsForSet().remove(PostViewCounterService.DIRTY_SET_KEY, idStr);
        // 原子取走并清零计数器，拿到这段增量
        String value = stringRedisTemplate.opsForValue().getAndDelete(PostViewCounterService.viewKey(postId));
        if (value == null) {
            return; // 已被别的实例消费，或本就无增量
        }
        long delta;
        try {
            delta = Long.parseLong(value);
        } catch (NumberFormatException e) {
            return;
        }
        if (delta <= 0) {
            return;
        }
        try {
            postMapper.incrementViewCount(postId, (int) delta);
        } catch (Exception e) {
            // 落库失败：把增量放回 Redis 并重新标记，下轮 5s 后重试
            stringRedisTemplate.opsForValue().increment(PostViewCounterService.viewKey(postId), delta);
            stringRedisTemplate.opsForSet().add(PostViewCounterService.DIRTY_SET_KEY, idStr);
            log.error("flush view_count failed, postId: {}, delta: {}", postId, delta, e);
        }
    }
}
