package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.config.properties.MeilisearchProperties;
import com.campus.trend.campus_pulse.dto.request.PostSearchReq;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.service.SearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.model.Searchable;
import com.meilisearch.sdk.model.Settings;
import com.meilisearch.sdk.model.TaskInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 搜索服务统一实现（facade）。
 *
 * Song：根据 campus.meilisearch.enabled 决定走 Meilisearch 还是降级。
 * - enabled=true 且 Client bean 存在且健康：使用 Meilisearch（中文分词、错字容忍）
 * - 否则：searchPostIds 返回空列表，调用方（PostServiceImpl.searchPostsWithAuthor）自动回退 MySQL FULLTEXT
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchServiceImpl implements SearchService {

    private final MeilisearchProperties props;
    private final ObjectMapper objectMapper;
    /** Song：可选注入 —— enabled=false 时 MeilisearchConfig 不会创建 Client bean，这里拿到空 */
    private final ObjectProvider<Client> meilisearchClientProvider;

    private volatile boolean indexInitialized = false;
    private static final int POST_STATUS_PUBLISHED = 1;
    private static final String AUDIT_STATUS_APPROVED = "APPROVED";

    @Override
    public boolean isAvailable() {
        if (!props.isEnabled()) {
            return false;
        }
        Client client = meilisearchClientProvider.getIfAvailable();
        if (client == null) {
            return false;
        }
        try {
            ensureIndex(client);
            client.health();
            return true;
        } catch (Exception e) {
            log.debug("Meilisearch 不可用: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public List<String> searchPostIds(PostSearchReq req) {
        if (!StringUtils.hasText(req.getKeyword()) || !isAvailable()) {
            return List.of();
        }
        try {
            Client client = meilisearchClientProvider.getIfAvailable();
            if (client == null) {
                return List.of();
            }
            ensureIndex(client);
            Index index = client.index(props.getPostsIndex());

            SearchRequest searchRequest = new SearchRequest(req.getKeyword().trim());
            searchRequest.setLimit(200);
            searchRequest.setShowRankingScore(false);

            Searchable result = index.search(searchRequest);
            List<String> ids = new ArrayList<>();
            for (HashMap<String, Object> hit : result.getHits()) {
                Object id = hit.get("id");
                if (id != null) {
                    ids.add(String.valueOf(id));
                }
            }
            return ids;
        } catch (Exception e) {
            log.warn("Meilisearch 搜索失败，将回退到 FULLTEXT: keyword={}, err={}",
                    req.getKeyword(), e.getMessage());
            return List.of();
        }
    }

    @Override
    public void indexPost(Post post) {
        if (post == null || !StringUtils.hasText(post.getId()) || !props.isEnabled()) {
            return;
        }
        if (!isIndexablePost(post)) {
            deletePost(post.getId());
            return;
        }
        Client client = meilisearchClientProvider.getIfAvailable();
        if (client == null) {
            return;
        }
        try {
            ensureIndex(client);
            Index index = client.index(props.getPostsIndex());
            Map<String, Object> doc = toDocument(post);
            index.addDocuments(objectMapper.writeValueAsString(List.of(doc)), "id");
            log.debug("Meilisearch 索引帖子成功 postId={}", post.getId());
        } catch (Exception e) {
            log.warn("Meilisearch 索引帖子失败 postId={}, err={}", post.getId(), e.getMessage());
        }
    }

    @Override
    public void deletePost(String postId) {
        if (!StringUtils.hasText(postId) || !props.isEnabled()) {
            return;
        }
        Client client = meilisearchClientProvider.getIfAvailable();
        if (client == null) {
            return;
        }
        try {
            ensureIndex(client);
            Index index = client.index(props.getPostsIndex());
            index.deleteDocument(postId);
        } catch (Exception e) {
            log.warn("Meilisearch 删除文档失败 postId={}, err={}", postId, e.getMessage());
        }
    }

    @Override
    public int reindexAll() {
        log.info("Meilisearch 索引设置就绪（全量数据重建由 SearchAdminController 分页批量索引）");
        Client client = meilisearchClientProvider.getIfAvailable();
        if (client == null) {
            return -1;
        }
        try {
            ensureIndex(client);
            return 0;
        } catch (Exception e) {
            log.error("Meilisearch 索引初始化失败", e);
            return -1;
        }
    }

    /**
     * Song：懒初始化索引设置（searchable / filterable / sortable）。
     * 只在首次访问时执行一次，失败不阻断查询。
     */
    private synchronized void ensureIndex(Client client) {
        if (indexInitialized) {
            return;
        }
        try {
            Index index = client.index(props.getPostsIndex());
            Settings settings = new Settings();
            settings.setSearchableAttributes(new String[]{"title", "content", "summary", "tags"});
            settings.setFilterableAttributes(new String[]{
                    "sectionId", "auditStatus", "status", "isFeatured", "userId", "isAnonymous"
            });
            settings.setSortableAttributes(new String[]{
                    "createTime", "heatScore", "likeCount", "viewCount", "lastActivityAt"
            });
            TaskInfo taskInfo = index.updateSettings(settings);
            log.info("Meilisearch 索引设置已提交, taskUid={}, index={}", taskInfo.getTaskUid(), props.getPostsIndex());
        } catch (Exception e) {
            log.warn("Meilisearch 索引设置失败（可能已存在），忽略: {}", e.getMessage());
        } finally {
            indexInitialized = true;
        }
    }

    private Map<String, Object> toDocument(Post post) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("id", post.getId());
        doc.put("title", nullToEmpty(post.getTitle()));
        doc.put("content", nullToEmpty(post.getContent()));
        doc.put("summary", nullToEmpty(post.getSummary()));
        doc.put("tags", nullToEmpty(post.getTags()));
        doc.put("userId", nullToEmpty(post.getUserId()));
        doc.put("sectionId", post.getSectionId());
        doc.put("status", post.getStatus());
        doc.put("auditStatus", nullToEmpty(post.getAuditStatus()));
        doc.put("isFeatured", post.getIsFeatured());
        doc.put("isAnonymous", post.getIsAnonymous());
        doc.put("heatScore", post.getHeatScore() != null ? post.getHeatScore() : 0);
        doc.put("likeCount", post.getLikeCount() != null ? post.getLikeCount() : 0);
        doc.put("viewCount", post.getViewCount() != null ? post.getViewCount() : 0);
        doc.put("createTime", post.getCreateTime() != null ? post.getCreateTime().toString() : null);
        doc.put("lastActivityAt", post.getLastActivityAt() != null ? post.getLastActivityAt().toString() : null);
        return doc;
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private boolean isIndexablePost(Post post) {
        if (post == null || !Integer.valueOf(POST_STATUS_PUBLISHED).equals(post.getStatus())) {
            return false;
        }
        String auditStatus = post.getAuditStatus();
        return !StringUtils.hasText(auditStatus) || AUDIT_STATUS_APPROVED.equalsIgnoreCase(auditStatus);
    }
}
