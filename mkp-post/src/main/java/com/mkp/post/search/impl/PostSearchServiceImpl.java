package com.mkp.post.search.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mkp.common.dto.UserVO;
import com.mkp.common.entity.Post;
import com.mkp.post.mapper.PostMapper;
import com.mkp.post.search.PostDocument;
import com.mkp.post.search.PostSearchHit;
import com.mkp.post.search.PostSearchResult;
import com.mkp.post.search.PostSearchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mkp.post.utils.UserCacheService;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.PutMappingRequest;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 帖子检索实现（Elasticsearch 7.17 + IK 分词）。
 *
 * 索引 mkp_post 里存三部分内容：
 *  - 参与检索的文本：content（帖子文案）、authorNickname（作者昵称，ik_max_word 建 / ik_smart 查）；
 *  - 排序 / 过滤用的数值：status、likeCount、createTime；
 *  - 展示用的冗余字段：images（只存不索引）。
 *
 * 检索结果只取 id + 高亮片段，正文仍然回 MySQL 查（保证点赞数、浏览量等是实时的），
 * 顺序按 ES 的打分 / 排序结果还原。ES 不可用时所有方法抛异常或返回 false，由调用方降级。
 */
@Slf4j
@Service
public class PostSearchServiceImpl implements PostSearchService {

    public static final String INDEX_NAME = "mkp_post";

    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_AUTHOR = "authorNickname";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_HAS_IMAGE = "hasImage";
    private static final String FIELD_TOPICS = "topics";
    /** 帖子里 #话题 的抽取规则（与前端展示保持一致） */
    private static final Pattern TOPIC_PATTERN = Pattern.compile("#([\\p{L}\\p{N}_]{1,20})");
    private static final String FIELD_LIKE = "likeCount";
    private static final String FIELD_CREATE_TIME = "createTime";

    private static final String MARK_START = "<mark>";
    private static final String MARK_END = "</mark>";
    /** 转义时先把高亮标记换成占位符，转义完再换回来，避免用户输入被当 HTML 执行 */
    private static final String HOLD_START = "\u0001";
    private static final String HOLD_END = "\u0002";

    private static final int MAX_PAGE_SIZE = 50;
    private static final int REBUILD_BATCH = 500;
    private static final long PROBE_CACHE_MS = 10_000L;

    private final RestHighLevelClient client;
    private final PostMapper postMapper;
    private final UserCacheService userCacheService;
    private final ObjectMapper objectMapper;

    @Value("${mkp.post.search.enabled:true}")
    private boolean enabled;

    /** ES 可用性缓存：避免 ES 挂掉时每个请求都去连一次 */
    private volatile boolean esAvailable = false;
    private volatile long lastProbeAt = 0L;

    public PostSearchServiceImpl(RestHighLevelClient client, PostMapper postMapper,
                                 UserCacheService userCacheService, ObjectMapper objectMapper) {
        this.client = client;
        this.postMapper = postMapper;
        this.userCacheService = userCacheService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    @Override
    public void initIndex() {
        if (!enabled) {
            log.info("[post-search] ES 检索未启用（mkp.post.search.enabled=false），全部走 MySQL");
            return;
        }
        try {
            if (!client.indices().exists(new GetIndexRequest(INDEX_NAME), RequestOptions.DEFAULT)) {
                CreateIndexRequest create = new CreateIndexRequest(INDEX_NAME);
                create.source(CREATE_INDEX_JSON, XContentType.JSON);
                client.indices().create(create, RequestOptions.DEFAULT);
                log.info("[post-search] 索引 {} 创建完成", INDEX_NAME);
            } else {
                log.info("[post-search] 索引 {} 已存在", INDEX_NAME);
            }
            // 每次启动都补一次字段映射：ES 允许给已有索引新增字段，
            // 后面再加筛选字段（hasImage / topics 之类）就不用手工改索引
            ensureMapping();
            esAvailable = true;
        } catch (Exception e) {
            esAvailable = false;
            log.warn("[post-search] 初始化 ES 索引失败，检索将降级为 MySQL: {}", e.getMessage());
        }
    }

    @Override
    public boolean isAvailable() {
        if (!enabled) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastProbeAt < PROBE_CACHE_MS) {
            return esAvailable;
        }
        lastProbeAt = now;
        try {
            esAvailable = client.ping(RequestOptions.DEFAULT);
        } catch (Exception e) {
            esAvailable = false;
            log.warn("[post-search] ES 探活失败: {}", e.getMessage());
        }
        return esAvailable;
    }

    @Override
    public PostDocument buildDocument(Post post) {
        Map<Long, UserVO> userMap = Collections.emptyMap();
        if (post.getUserId() != null) {
            UserVO author = userCacheService.getUserById(post.getUserId());
            if (author != null) {
                userMap = Collections.singletonMap(post.getUserId(), author);
            }
        }
        return toDocument(post, userMap);
    }

    @Override
    public void save(PostDocument document) {
        if (!enabled || document == null || document.getId() == null) {
            return;
        }
        try {
            IndexRequest request = new IndexRequest(INDEX_NAME)
                    .id(String.valueOf(document.getId()))
                    .source(toSource(document));
            client.index(request, RequestOptions.DEFAULT);
            esAvailable = true;
        } catch (Exception e) {
            esAvailable = false;
            throw new IllegalStateException("写入 ES 索引失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(Long postId) {
        if (!enabled || postId == null) {
            return;
        }
        try {
            client.delete(new DeleteRequest(INDEX_NAME, String.valueOf(postId)), RequestOptions.DEFAULT);
            esAvailable = true;
        } catch (Exception e) {
            esAvailable = false;
            throw new IllegalStateException("删除 ES 文档失败: " + e.getMessage(), e);
        }
    }

    @Override
    public PostSearchResult search(String keyword, int page, int size, String sort,
                                   Boolean hasImage, String topic) {
        int safePage = page < 1 ? 1 : page;
        int safeSize = size < 1 ? 10 : Math.min(size, MAX_PAGE_SIZE);
        String kw = keyword == null ? "" : keyword.trim();
        String topicTerm = topic == null ? "" : topic.trim();

        try {
            SearchSourceBuilder source = new SearchSourceBuilder();
            BoolQueryBuilder bool = QueryBuilders.boolQuery();
            bool.filter(QueryBuilders.termQuery(FIELD_STATUS, 1));
            if (hasImage != null) {
                bool.filter(QueryBuilders.termQuery(FIELD_HAS_IMAGE, hasImage));
            }
            if (!topicTerm.isEmpty()) {
                bool.filter(QueryBuilders.termQuery(FIELD_TOPICS, topicTerm));
            }
            if (!kw.isEmpty()) {
                bool.must(QueryBuilders.multiMatchQuery(kw, FIELD_CONTENT, FIELD_AUTHOR)
                        .type(MultiMatchQueryBuilder.Type.BEST_FIELDS));
            }
            source.query(bool);
            source.from((safePage - 1) * safeSize).size(safeSize);
            source.trackTotalHits(true);
            source.timeout(TimeValue.timeValueSeconds(5));
            source.fetchSource(false);
            applySort(source, sort);

            HighlightBuilder highlight = new HighlightBuilder();
            highlight.field(new HighlightBuilder.Field(FIELD_CONTENT)
                    .numOfFragments(0).preTags(MARK_START).postTags(MARK_END));
            highlight.field(new HighlightBuilder.Field(FIELD_AUTHOR)
                    .numOfFragments(0).preTags(MARK_START).postTags(MARK_END));
            highlight.requireFieldMatch(false);
            source.highlighter(highlight);

            SearchResponse response = client.search(new SearchRequest(INDEX_NAME).source(source), RequestOptions.DEFAULT);

            List<PostSearchHit> hits = new ArrayList<>();
            for (SearchHit hit : response.getHits().getHits()) {
                Long id;
                try {
                    id = Long.parseLong(hit.getId());
                } catch (NumberFormatException e) {
                    continue;
                }
                Map<String, HighlightField> fields = hit.getHighlightFields();
                hits.add(new PostSearchHit(id,
                        escapeKeepingMark(fragmentsText(fields.get(FIELD_CONTENT))),
                        escapeKeepingMark(fragmentsText(fields.get(FIELD_AUTHOR)))));
            }
            long total = response.getHits().getTotalHits() == null
                    ? hits.size() : response.getHits().getTotalHits().value;
            esAvailable = true;
            return new PostSearchResult(hits, total);
        } catch (Exception e) {
            esAvailable = false;
            throw new IllegalStateException("ES 检索失败: " + e.getMessage(), e);
        }
    }

    @Override
    public long rebuildAll() {
        if (!enabled) {
            return 0;
        }
        try {
            long total = 0;
            int page = 1;
            while (true) {
                Page<Post> result = postMapper.selectPage(new Page<>(page, REBUILD_BATCH),
                        new LambdaQueryWrapper<Post>()
                                .eq(Post::getStatus, 1)
                                .orderByAsc(Post::getId));
                List<Post> records = result.getRecords();
                if (records == null || records.isEmpty()) {
                    break;
                }
                Map<Long, UserVO> userMap = loadAuthors(records);
                BulkRequest bulk = new BulkRequest();
                for (Post post : records) {
                    bulk.add(new IndexRequest(INDEX_NAME)
                            .id(String.valueOf(post.getId()))
                            .source(toSource(toDocument(post, userMap))));
                }
                BulkResponse response = client.bulk(
                        bulk.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE), RequestOptions.DEFAULT);
                if (response.hasFailures()) {
                    log.error("[post-search] 批量重建索引存在失败项: {}", response.buildFailureMessage());
                }
                total += records.size();
                if (records.size() < REBUILD_BATCH) {
                    break;
                }
                page++;
            }
            esAvailable = true;
            log.info("[post-search] 重建索引完成，共写入 {} 条", total);
            return total;
        } catch (Exception e) {
            esAvailable = false;
            throw new IllegalStateException("重建 ES 索引失败: " + e.getMessage(), e);
        }
    }

    @Override
    public long refreshByAuthor(Long userId) {
        if (!enabled || userId == null) {
            return 0;
        }
        List<Post> posts = postMapper.selectList(new LambdaQueryWrapper<Post>()
                .eq(Post::getUserId, userId)
                .eq(Post::getStatus, 1));
        if (posts == null || posts.isEmpty()) {
            return 0;
        }
        Map<Long, UserVO> userMap = loadAuthors(posts);
        try {
            BulkRequest bulk = new BulkRequest();
            for (Post post : posts) {
                bulk.add(new IndexRequest(INDEX_NAME)
                        .id(String.valueOf(post.getId()))
                        .source(toSource(toDocument(post, userMap))));
            }
            BulkResponse response = client.bulk(
                    bulk.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE), RequestOptions.DEFAULT);
            if (response.hasFailures()) {
                log.error("[post-search] 刷新作者索引存在失败项: {}", response.buildFailureMessage());
            }
            esAvailable = true;
            log.info("[post-search] 作者 {} 的 {} 条帖子索引已刷新", userId, posts.size());
            return posts.size();
        } catch (Exception e) {
            esAvailable = false;
            throw new IllegalStateException("刷新作者帖子索引失败: " + e.getMessage(), e);
        }
    }

    @Override
    public long count() {
        if (!enabled) {
            return -1;
        }
        try {
            return client.count(new CountRequest(INDEX_NAME), RequestOptions.DEFAULT).getCount();
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public String describeStatus() {
        if (!enabled) {
            return "disabled: ES 检索未启用（mkp.post.search.enabled=false）";
        }
        if (!isAvailable()) {
            return "unavailable: ES 不可达，检索已降级为 MySQL LIKE";
        }
        return "ok: 索引 " + INDEX_NAME + " 文档数 " + count();
    }

    /** 排序：hot=点赞数优先，latest=发布时间，默认相关度（相同分数按时间倒序） */
    private void applySort(SearchSourceBuilder source, String sort) {
        if ("hot".equalsIgnoreCase(sort)) {
            source.sort(SortBuilders.fieldSort(FIELD_LIKE).order(SortOrder.DESC));
            source.sort(SortBuilders.fieldSort(FIELD_CREATE_TIME).order(SortOrder.DESC));
        } else if ("latest".equalsIgnoreCase(sort)) {
            source.sort(SortBuilders.fieldSort(FIELD_CREATE_TIME).order(SortOrder.DESC));
        } else {
            source.sort(SortBuilders.scoreSort());
            source.sort(SortBuilders.fieldSort(FIELD_CREATE_TIME).order(SortOrder.DESC));
        }
    }

    private Map<Long, UserVO> loadAuthors(List<Post> posts) {
        List<Long> userIds = posts.stream()
                .map(Post::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, UserVO> userMap = userCacheService.getByIds(userIds);
        return userMap == null ? Collections.emptyMap() : userMap;
    }

    private PostDocument toDocument(Post post, Map<Long, UserVO> userMap) {
        PostDocument document = new PostDocument();
        document.setId(post.getId());
        document.setUserId(post.getUserId());
        document.setContent(post.getContent());
        document.setImages(post.getImages());
        document.setLikeCount(post.getLikeCount());
        document.setViewCount(post.getViewCount());
        document.setStatus(post.getStatus());
        document.setCreateTime(post.getCreateTime());

        UserVO author = userMap == null ? null : userMap.get(post.getUserId());
        if (author != null) {
            String nickname = author.getNickname();
            if (nickname == null || nickname.isBlank()) {
                nickname = author.getUsername();
            }
            document.setAuthorNickname(nickname);
        }
        return document;
    }

    private Map<String, Object> toSource(PostDocument document) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("id", document.getId());
        source.put("userId", document.getUserId());
        source.put("content", document.getContent());
        source.put("authorNickname", document.getAuthorNickname());
        source.put("images", document.getImages());
        // 筛选用字段：有没有配图、内容里带的 #话题
        source.put(FIELD_HAS_IMAGE, document.getImages() != null && !document.getImages().isBlank());
        source.put(FIELD_TOPICS, extractTopics(document.getContent()));
        source.put("likeCount", document.getLikeCount());
        source.put("viewCount", document.getViewCount());
        source.put("status", document.getStatus());
        if (document.getCreateTime() != null) {
            source.put("createTime", document.getCreateTime()
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        }
        return source;
    }

    /**
     * 建索引请求体。
     *
     * content / authorNickname 用 IK 分词（建索引 ik_max_word 细粒度、查询 ik_smart 粗粒度）；
     * 单节点集群副本数必须为 0，否则索引会一直是 yellow；
     * createTime 用 epoch_millis 存，兼容 yyyy-MM-dd HH:mm:ss 格式。
     */
    private static final String CREATE_INDEX_JSON = "{"
            + "\"settings\":{\"number_of_shards\":1,\"number_of_replicas\":0,\"max_result_window\":50000},"
            + "\"mappings\":{\"properties\":{"
            + "\"id\":{\"type\":\"long\"},"
            + "\"userId\":{\"type\":\"long\"},"
            + "\"content\":{\"type\":\"text\",\"analyzer\":\"ik_max_word\",\"search_analyzer\":\"ik_smart\"},"
            + "\"authorNickname\":{\"type\":\"text\",\"analyzer\":\"ik_max_word\",\"search_analyzer\":\"ik_smart\","
            + "  \"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256}}},"
            + "\"images\":{\"type\":\"keyword\",\"index\":false},"
            + "\"hasImage\":{\"type\":\"boolean\"},"
            + "\"topics\":{\"type\":\"keyword\"},"
            + "\"likeCount\":{\"type\":\"integer\"},"
            + "\"viewCount\":{\"type\":\"integer\"},"
            + "\"status\":{\"type\":\"integer\"},"
            + "\"createTime\":{\"type\":\"date\",\"format\":\"epoch_millis||yyyy-MM-dd HH:mm:ss||yyyy-MM-dd'T'HH:mm:ss\"}"
            + "}}}";

    /** 从帖子文案里抽 #话题（话题筛选用），去重后返回 */
    private static List<String> extractTopics(String content) {
        if (content == null || content.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> topics = new LinkedHashSet<>();
        Matcher matcher = TOPIC_PATTERN.matcher(content);
        while (matcher.find()) {
            topics.add(matcher.group(1));
            if (topics.size() >= 10) {
                break;
            }
        }
        return new ArrayList<>(topics);
    }

    /** 补字段映射：把 CREATE_INDEX_JSON 里的 mappings 部分 PUT 上去（新增字段不影响已有数据） */
    private void ensureMapping() throws IOException {
        JsonNode mappings = objectMapper.readTree(CREATE_INDEX_JSON).get("mappings");
        if (mappings == null) {
            return;
        }
        PutMappingRequest request = new PutMappingRequest(INDEX_NAME)
                .source(objectMapper.writeValueAsString(mappings), XContentType.JSON);
        client.indices().putMapping(request, RequestOptions.DEFAULT);
    }

    private static String fragmentsText(HighlightField field) {
        if (field == null || field.fragments() == null || field.fragments().length == 0) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (Text text : field.fragments()) {
            builder.append(text.string());
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    /**
     * 高亮片段转义：把 mark 标记先换成占位符，转义其余 HTML 之后再换回来。
     * 这样用户发帖里写的 script 之类的标签不会被执行，而关键词标红仍然有效。
     */
    private static String escapeKeepingMark(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.replace(MARK_START, HOLD_START).replace(MARK_END, HOLD_END);
        text = text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
        return text.replace(HOLD_START, MARK_START).replace(HOLD_END, MARK_END);
    }
}
