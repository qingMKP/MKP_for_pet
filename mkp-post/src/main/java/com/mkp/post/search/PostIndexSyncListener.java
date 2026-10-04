package com.mkp.post.search;

import com.mkp.common.entity.Post;
import com.mkp.post.mapper.PostMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 帖子索引同步。
 *
 * 监听帖子增删改事件，在事务提交之后异步写 ES：
 *  - 事务提交后才触发，避免事务回滚把脏数据写进索引；
 *  - 不在事务内写 ES，避免 ES 抖动拖慢发帖主流程；
 *  - 只拿 postId 回查数据库，写入的一定是落库后的最终状态；状态不是上架(1)就从索引里删掉；
 *  - 同步失败只记日志，检索侧有 MySQL 降级兜底，不能因为索引失败影响用户发帖。
 */
@Slf4j
@Component
public class PostIndexSyncListener {

    private final PostMapper postMapper;
    private final PostSearchService postSearchService;

    public PostIndexSyncListener(PostMapper postMapper, PostSearchService postSearchService) {
        this.postMapper = postMapper;
        this.postSearchService = postSearchService;
    }

    @Async("postSearchExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPostIndexEvent(PostIndexEvent event) {
        if (event == null || event.getPostId() == null) {
            return;
        }
        Long postId = event.getPostId();
        try {
            Post post = postMapper.selectById(postId);
            if (post == null || post.getStatus() == null || post.getStatus() != 1) {
                postSearchService.delete(postId);
                return;
            }
            postSearchService.save(postSearchService.buildDocument(post));
        } catch (Exception e) {
            log.error("[post-search] 同步帖子索引失败, postId={}", postId, e);
        }
    }
}
