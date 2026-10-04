package com.mkp.post.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mkp.common.dto.ChatbotPostVO;
import com.mkp.common.dto.PageResult;
import com.mkp.common.dto.PostSaveDTO;
import com.mkp.common.dto.PostVO;
import com.mkp.common.dto.UserVO;
import com.mkp.common.entity.Comment;
import com.mkp.common.entity.Like;
import com.mkp.common.entity.Post;
import com.mkp.common.result.R;
import com.mkp.post.mapper.CommentMapper;
import com.mkp.post.mapper.LikeMapper;
import com.mkp.post.mapper.PostMapper;
import com.mkp.post.service.PostService;
import com.mkp.post.search.PostIndexEvent;
import com.mkp.post.search.PostSearchHit;
import com.mkp.post.search.PostSearchResult;
import com.mkp.post.search.PostSearchService;
import com.mkp.post.service.PostViewCounterService;
import com.mkp.post.utils.UserCacheService;
import com.mkp.api.UserFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PostServiceImpl implements PostService {

    
    private final PostMapper postMapper;
    private final LikeMapper likeMapper;
    private final CommentMapper commentMapper;
    private final UserCacheService userCacheService;
    private final PostViewCounterService postViewCounter;
    private final PostSearchService postSearchService;
    private final UserFeignClient userFeignClient;
    private final ApplicationEventPublisher eventPublisher;

    public PostServiceImpl(PostMapper postMapper, LikeMapper likeMapper,
                           CommentMapper commentMapper, UserCacheService userCacheService,
                           PostViewCounterService postViewCounter, PostSearchService postSearchService,
                           UserFeignClient userFeignClient, ApplicationEventPublisher eventPublisher) {
        this.postMapper = postMapper;
        this.likeMapper = likeMapper;
        this.commentMapper = commentMapper;
        this.userCacheService = userCacheService;
        this.postViewCounter = postViewCounter;
        this.postSearchService = postSearchService;
        this.userFeignClient = userFeignClient;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public R<List<PostVO>> listPosts(Integer page, Integer size, Long currentUserId) {
        Page<Post> pageParam = new Page<>(page, size);
        Page<Post> result = postMapper.selectPage(pageParam,
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getStatus, 1)
                        .orderByDesc(Post::getCreateTime));

        List<Long> userIds = result.getRecords().stream().map(Post::getUserId).distinct().collect(Collectors.toList());
        Map<Long, UserVO> data = userCacheService.getByIds(userIds);
        List<PostVO> vos = new ArrayList<>();
        for (Post post : result.getRecords()) {
            vos.add(toVO(post, currentUserId,data));
        }
        return R.ok(vos);
    }

    @Override
    public R<PostVO> getPostDetail(Long postId, Long currentUserId) {
        Post post = postMapper.selectById(postId);
        if (post == null || post.getStatus() != 1) {
            return R.fail(404, "Post not found");
        }
        // 浏览量只写 Redis，由 PostViewFlushTask 异步批量落库，消除读-改-写竞态
        postViewCounter.increment(postId);
        // 展示实时值 = DB 已落库值 + Redis 待落库增量
        int base = post.getViewCount() == null ? 0 : post.getViewCount();
        post.setViewCount(base + (int) postViewCounter.pendingDelta(postId));
        UserVO author = userCacheService.getUserById(post.getUserId());
        Map<Long, UserVO> userMap = new HashMap<>();
        if (author != null) {
            userMap.put(post.getUserId(), author);
        }
        return R.ok(toVO(post, currentUserId, userMap));
    }

    @Override
    @Transactional
    public R<PostVO> createPost(Long userId, PostSaveDTO dto) {
        Post post = new Post();
        post.setUserId(userId);
        post.setContent(dto.getContent());
        post.setImages(dto.getImages() != null ? String.join(",", dto.getImages()) : "");
        post.setStatus(1);
        post.setViewCount(0);
        post.setLikeCount(0);
        postMapper.insert(post);
        // 事务提交后异步写 ES 索引（见 PostIndexSyncListener），失败不影响发帖
        eventPublisher.publishEvent(new PostIndexEvent(post.getId()));
        UserVO author = userCacheService.getUserById(userId);
        Map<Long, UserVO> userMap = new HashMap<>();
        if (author != null) {
            userMap.put(userId, author);
        }
        return R.ok(toVO(post, userId, userMap));
    }

    @Override
    public R<Void> deletePost(Long userId, Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            return R.fail(404, "Post not found");
        }
        if (!post.getUserId().equals(userId)) {
            return R.fail(403, "Cannot delete other user's post");
        }
        post.setStatus(2);
        postMapper.updateById(post);
        eventPublisher.publishEvent(new PostIndexEvent(postId));
        return R.ok();
    }

    @Override
    @Transactional
    public R<Boolean> toggleLike(Long userId, Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null || post.getStatus() != 1) {
            return R.fail(404, "Post not found");
        }
        Like existing = likeMapper.selectOne(new LambdaQueryWrapper<Like>()
                .eq(Like::getPostId, postId)
                .eq(Like::getUserId, userId));
        if (existing != null) {
            // Unlike：只有真正删掉了这条 like，才 count-1（防止双击"取消赞"多减）
            int deleted = likeMapper.deleteById(existing.getId());
            if (deleted > 0) {
                eventPublisher.publishEvent(new PostIndexEvent(postId));
                postMapper.decrementLikeCount(postId);   // 原子 -1，基于当前行值，不丢更新
            }
            return R.ok(false);
        } else {
            // Like：INSERT 撞上唯一索引 uk_post_user 说明并发下已有人插好，视为已点赞且不再 +1
            Like like = new Like();
            like.setPostId(postId);
            like.setUserId(userId);
            try {
                likeMapper.insert(like);
            } catch (DuplicateKeyException e) {
                return R.ok(true);
            }
            // 只有真正插入成功，才算一次新点赞
            eventPublisher.publishEvent(new PostIndexEvent(postId));
            postMapper.incrementLikeCount(postId);       // 原子 +1，基于当前行值，不丢更新
            return R.ok(true);
        }
    }

    @Override
    public R<List<ChatbotPostVO>> searchPosts(String keyword) {
        List<Post> posts = postMapper.selectList(new LambdaQueryWrapper<Post>()
                .like(Post::getContent, keyword)
                .eq(Post::getStatus, 1)
                .orderByDesc(Post::getLikeCount)
                .last("LIMIT 5"));

        List<ChatbotPostVO> result = new ArrayList<>();
        for (Post post : posts) {
            ChatbotPostVO vo = new ChatbotPostVO();
            vo.setId(post.getId());
            String content = post.getContent();
            vo.setContent(content.length() > 100 ? content.substring(0, 100) + "..." : content);
            vo.setLikeCount(post.getLikeCount());
            String images = post.getImages();
            if (images != null && !images.isEmpty()) {
                vo.setImage(images.split(",")[0]);
            }
            result.add(vo);
        }
        return R.ok(result);
    }

    // ==================== Admin ====================

    @Override
    public R<PageResult<PostVO>> adminListPosts(Integer page, Integer size, String keyword) {
        Page<Post> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>()
                .orderByDesc(Post::getCreateTime);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Post::getContent, keyword);
        }
        Page<Post> result = postMapper.selectPage(pageParam, wrapper);
        List<Long> userIds = result.getRecords().stream()
                .map(Post::getUserId).distinct().collect(Collectors.toList());
        Map<Long, UserVO> userMap = userIds.isEmpty() ? Map.of() : userCacheService.getByIds(userIds);
        List<PostVO> vos = new ArrayList<>();
        for (Post post : result.getRecords()) {
            vos.add(toVO(post, null, userMap));
        }
        return R.ok(new PageResult<>(vos, result.getTotal(), page, size));
    }

    @Override
    public R<Void> adminDeletePost(Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            return R.fail(404, "Post not found");
        }
        post.setStatus(2);
        postMapper.updateById(post);
        eventPublisher.publishEvent(new PostIndexEvent(postId));
        return R.ok();
    }

    @Override
    public R<Map<String, Object>> adminGetPostStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalPosts", postMapper.selectCount(
                new LambdaQueryWrapper<Post>().eq(Post::getStatus, 1)));
        stats.put("todayNewPosts", postMapper.selectCount(
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getStatus, 1)
                        .ge(Post::getCreateTime, java.time.LocalDate.now().atStartOfDay())));
        stats.put("totalPostLikes", likeMapper.selectCount(null));
        return R.ok(stats);
    }

    @Override
    public R<List<Map<String, Object>>> adminGetDailyTrend() {
        List<Map<String, Object>> trend = new ArrayList<>();
        java.time.LocalDate today = java.time.LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            java.time.LocalDate date = today.minusDays(i);
            java.time.LocalDateTime start = date.atStartOfDay();
            java.time.LocalDateTime end = date.plusDays(1).atStartOfDay();
            long posts = postMapper.selectCount(new LambdaQueryWrapper<Post>()
                    .eq(Post::getStatus, 1)
                    .ge(Post::getCreateTime, start)
                    .lt(Post::getCreateTime, end));
            long comments = commentMapper.selectCount(new LambdaQueryWrapper<Comment>()
                    .ge(Comment::getCreateTime, start)
                    .lt(Comment::getCreateTime, end));
            Map<String, Object> day = new HashMap<>();
            day.put("date", date.toString());
            day.put("newPosts", posts);
            day.put("newComments", comments);
            trend.add(day);
        }
        return R.ok(trend);
    }


    @Override
    public R<PageResult<PostVO>> searchPosts(String keyword, Integer page, Integer size, String sort,
                                             Boolean hasImage, String topic, Long currentUserId) {
        int safePage = (page == null || page < 1) ? 1 : page;
        int safeSize = (size == null || size < 1) ? 10 : Math.min(size, 50);
        String kw = keyword == null ? "" : keyword.trim();
        String topicTerm = topic == null ? "" : topic.trim();
        // 关键词写成 #话题 时按话题精确筛，而不是当全文关键词
        if (topicTerm.isEmpty() && kw.startsWith("#")) {
            topicTerm = kw.substring(1).trim();
            kw = "";
        }
        boolean hasFilter = hasImage != null || !topicTerm.isEmpty();

        // 既没关键词也没筛选条件：退化成普通列表，避免空查询把整个索引扫一遍
        if (kw.isEmpty() && !hasFilter) {
            Page<Post> result = postMapper.selectPage(new Page<>(safePage, safeSize),
                    new LambdaQueryWrapper<Post>()
                            .eq(Post::getStatus, 1)
                            .orderByDesc(Post::getCreateTime));
            return R.ok(new PageResult<>(buildVos(result.getRecords(), currentUserId),
                    result.getTotal(), safePage, safeSize));
        }

        // 优先走 ES：文案 + 作者昵称全文检索，叠加有图 / 话题筛选，并返回关键词高亮
        if (postSearchService.isAvailable()) {
            try {
                PostSearchResult searchResult = postSearchService.search(kw, safePage, safeSize, sort, hasImage, topicTerm);
                List<Long> ids = searchResult.getHits().stream()
                        .map(PostSearchHit::getId).collect(Collectors.toList());
                if (ids.isEmpty()) {
                    return R.ok(new PageResult<>(new ArrayList<>(), 0L, safePage, safeSize));
                }
                List<Post> posts = postMapper.selectList(new LambdaQueryWrapper<Post>()
                        .in(Post::getId, ids)
                        .eq(Post::getStatus, 1));
                Map<Long, Post> postMap = posts.stream()
                        .collect(Collectors.toMap(Post::getId, p -> p, (a, b) -> a));
                Map<Long, UserVO> userMap = userCacheService.getByIds(posts.stream()
                        .map(Post::getUserId).distinct().collect(Collectors.toList()));

                // 正文、点赞数、浏览量都取库里的实时值，只沿用 ES 的命中顺序和高亮
                List<PostVO> vos = new ArrayList<>();
                for (PostSearchHit hit : searchResult.getHits()) {
                    Post post = postMap.get(hit.getId());
                    if (post == null) {
                        continue;
                    }
                    PostVO vo = toVO(post, currentUserId, userMap);
                    vo.setContentHighlight(hit.getContentHighlight());
                    vo.setAuthorHighlight(hit.getAuthorHighlight());
                    vos.add(vo);
                }
                return R.ok(new PageResult<>(vos, searchResult.getTotal(), safePage, safeSize));
            } catch (Exception e) {
                log.error("[post-search] ES 检索失败，降级为 MySQL LIKE: {}", e.getMessage());
            }
        }

        // 降级：MySQL LIKE（文案 + 作者昵称 + 图片 / 话题条件）
        return R.ok(searchFromMysql(kw, safePage, safeSize, hasImage, topicTerm, currentUserId));
    }

    /**
     * 降级检索：帖子文案 LIKE，外加「作者昵称命中」的帖子。
     *
     * 昵称在用户服务里，帖子表没有这个字段，所以先用 Feign 把昵称命中的用户 id 查出来，
     * 再用 user_id IN (...) 兜住这部分，保证按作者名字也能搜到。
     */
    private PageResult<PostVO> searchFromMysql(String keyword, int page, int size,
                                              Boolean hasImage, String topic, Long currentUserId) {
        List<Long> userIds = new ArrayList<>();
        if (!keyword.isEmpty()) {
            try {
                R<List<Long>> r = userFeignClient.searchUserIds(keyword, 200);
                if (r != null && r.getCode() == R.SUCCESS && r.getData() != null) {
                    userIds.addAll(r.getData());
                }
            } catch (Exception e) {
                log.warn("[post-search] 按昵称查用户失败，本次仅按文案检索: {}", e.getMessage());
            }
        }

        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>().eq(Post::getStatus, 1);
        if (Boolean.TRUE.equals(hasImage)) {
            wrapper.isNotNull(Post::getImages).ne(Post::getImages, "");
        } else if (Boolean.FALSE.equals(hasImage)) {
            wrapper.and(w -> w.isNull(Post::getImages).or().eq(Post::getImages, ""));
        }
        if (!topic.isEmpty()) {
            wrapper.like(Post::getContent, "#" + topic);
        }
        if (!keyword.isEmpty()) {
            if (userIds.isEmpty()) {
                wrapper.like(Post::getContent, keyword);
            } else {
                wrapper.and(w -> w.like(Post::getContent, keyword).or().in(Post::getUserId, userIds));
            }
        }
        wrapper.orderByDesc(Post::getCreateTime);

        Page<Post> result = postMapper.selectPage(new Page<>(page, size), wrapper);
        return new PageResult<>(buildVos(result.getRecords(), currentUserId), result.getTotal(), page, size);
    }

    @Override
    public R<Long> refreshAuthorIndex(Long userId) {
        try {
            return R.ok(postSearchService.refreshByAuthor(userId));
        } catch (Exception e) {
            log.error("[post-search] 刷新作者索引失败, userId={}", userId, e);
            return R.fail("刷新作者帖子索引失败: " + e.getMessage());
        }
    }

    private List<PostVO> buildVos(List<Post> posts, Long currentUserId) {
        List<PostVO> vos = new ArrayList<>();
        if (posts == null || posts.isEmpty()) {
            return vos;
        }
        List<Long> userIds = posts.stream().map(Post::getUserId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, UserVO> userMap = userIds.isEmpty() ? new HashMap<>() : userCacheService.getByIds(userIds);
        for (Post post : posts) {
            vos.add(toVO(post, currentUserId, userMap));
        }
        return vos;
    }

    @Override
    public R<Long> rebuildSearchIndex() {
        try {
            return R.ok(postSearchService.rebuildAll());
        } catch (Exception e) {
            log.error("[post-search] 重建索引失败", e);
            return R.fail("重建 ES 索引失败: " + e.getMessage());
        }
    }

    @Override
    public R<String> getSearchStatus() {
        return R.ok(postSearchService.describeStatus());
    }

    private PostVO toVO(Post post, Long currentUserId, Map<Long, UserVO> userMap) {
        PostVO vo = new PostVO();
        vo.setId(post.getId());
        vo.setContent(post.getContent());
        vo.setImages(post.getImages() != null && !post.getImages().isEmpty()
                ? Arrays.asList(post.getImages().split(",")) : List.of());
        vo.setViewCount(post.getViewCount());
        vo.setLikeCount(post.getLikeCount());
        vo.setCreateTime(post.getCreateTime());

        // Fetch author info from user service
        try {
            vo.setAuthor(userMap.get(post.getUserId()));
        } catch (Exception ignored) {}

        // Check if current user liked this post
        if (currentUserId != null) {
            Long count = likeMapper.selectCount(new LambdaQueryWrapper<Like>()
                    .eq(Like::getPostId, post.getId())
                    .eq(Like::getUserId, currentUserId));
            vo.setLiked(count > 0);
        }
        return vo;
    }
}
