package com.campus.trend.campus_pulse.service.post;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.trend.campus_pulse.dto.request.PostSearchReq;
import com.campus.trend.campus_pulse.dto.response.PostResp;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 帖子 feed / 详情的 Redis 版本号缓存,从 {@code PostServiceImpl} 抽离的纯技术职责。
 *
 * <p>失效策略:写路径只递增版本键(global / section / detail / heat),
 * 读路径把当前版本编进缓存 Key,旧版本条目靠 TTL 自然过期。
 * 版本键统一在这里递增,保证 TTL 续期一致(此前 ViewLog 等处的手工副本不续期,
 * 首次由其创建的版本键会变成永不过期)。
 */
@Slf4j
@Component
public class PostCacheManager {

    private static final Duration POST_FEED_CACHE_TTL = Duration.ofSeconds(120);
    private static final Duration POST_FEED_EMPTY_CACHE_TTL = Duration.ofSeconds(30);
    private static final Duration POST_DETAIL_CACHE_TTL = Duration.ofSeconds(300);
    private static final String CACHE_NULL_MARKER = "__NULL__";

    private static final String POST_FEED_CACHE_PREFIX = "post:feed:cache:";
    private static final String POST_DETAIL_CACHE_PREFIX = "post:detail:cache:";
    public static final String POST_FEED_CACHE_GLOBAL_VERSION_KEY = "post:feed:version:global";
    public static final String POST_FEED_CACHE_SECTION_VERSION_KEY_PREFIX = "post:feed:version:section:";
    public static final String POST_DETAIL_CACHE_VERSION_KEY_PREFIX = "post:detail:version:";
    public static final String POST_HEAT_RANK_VERSION_KEY = "post:heat:version";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${campus.cache.null-value-ttl-seconds:30}")
    private long nullValueTtlSeconds;

    @Value("${campus.cache.version-key-ttl-hours:168}")
    private long versionKeyTtlHours;

    @Value("${campus.cache.ttl-jitter-ratio:0.2}")
    private double ttlJitterRatio;

    public PostCacheManager(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    // ─── 详情缓存 ────────────────────────────────────────────────────────

    public CachedDetail readPostDetailCache(String postId) {
        if (!StringUtils.hasText(postId)) {
            return CachedDetail.miss();
        }
        String cacheKey = buildPostDetailCacheKey(postId);
        if (!StringUtils.hasText(cacheKey)) {
            return CachedDetail.miss();
        }
        try {
            String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
            if (!StringUtils.hasText(cachedJson)) {
                return CachedDetail.miss();
            }
            if (CACHE_NULL_MARKER.equals(cachedJson)) {
                return CachedDetail.hit(null);
            }
            return CachedDetail.hit(objectMapper.readValue(cachedJson, PostResp.class));
        } catch (Exception e) {
            log.debug("读取帖子详情缓存失败: postId={}, err={}", postId, e.getMessage());
            return CachedDetail.miss();
        }
    }

    public void writePostDetailCache(String postId, PostResp detail) {
        if (!StringUtils.hasText(postId)) {
            return;
        }
        String cacheKey = buildPostDetailCacheKey(postId);
        if (!StringUtils.hasText(cacheKey)) {
            return;
        }
        try {
            if (detail == null) {
                stringRedisTemplate.opsForValue().set(
                        cacheKey,
                        CACHE_NULL_MARKER,
                        withCacheTtlJitter(resolveNullValueTtl()));
                return;
            }
            String json = objectMapper.writeValueAsString(detail);
            stringRedisTemplate.opsForValue().set(cacheKey, json, withCacheTtlJitter(POST_DETAIL_CACHE_TTL));
        } catch (Exception e) {
            log.debug("写入帖子详情缓存失败: postId={}, err={}", postId, e.getMessage());
        }
    }

    // ─── Feed 缓存 ───────────────────────────────────────────────────────

    public IPage<PostResp> readPostFeedCache(PostSearchReq request) {
        if (!shouldUsePostFeedCache(request)) {
            return null;
        }

        String cacheKey = buildPostFeedCacheKey(request);
        if (!StringUtils.hasText(cacheKey)) {
            return null;
        }

        try {
            String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
            if (!StringUtils.hasText(cachedJson)) {
                return null;
            }

            PostFeedCachePayload payload = objectMapper.readValue(cachedJson, PostFeedCachePayload.class);
            Page<PostResp> page = new Page<>(payload.current, payload.size, payload.total);
            page.setRecords(payload.records != null ? payload.records : new ArrayList<>());
            return page;
        } catch (Exception e) {
            log.debug("读取帖子流缓存失败: {}", e.getMessage());
            return null;
        }
    }

    public void writePostFeedCache(PostSearchReq request, IPage<PostResp> page) {
        if (!shouldUsePostFeedCache(request)) {
            return;
        }

        String cacheKey = buildPostFeedCacheKey(request);
        if (!StringUtils.hasText(cacheKey)) {
            return;
        }

        try {
            PostFeedCachePayload payload = new PostFeedCachePayload();
            payload.current = page.getCurrent();
            payload.size = page.getSize();
            payload.total = page.getTotal();
            payload.records = page.getRecords();

            String json = objectMapper.writeValueAsString(payload);
            Duration ttl = page.getRecords() == null || page.getRecords().isEmpty()
                    ? POST_FEED_EMPTY_CACHE_TTL
                    : POST_FEED_CACHE_TTL;
            stringRedisTemplate.opsForValue().set(cacheKey, json, withCacheTtlJitter(ttl));
        } catch (Exception e) {
            log.debug("写入帖子流缓存失败: {}", e.getMessage());
        }
    }

    // ─── 失效(版本号递增) ────────────────────────────────────────────────

    public void invalidatePostFeedCache(Long oldSectionId, Long newSectionId) {
        bumpCacheVersion(POST_FEED_CACHE_GLOBAL_VERSION_KEY);
        bumpCacheVersion(POST_HEAT_RANK_VERSION_KEY);
        if (oldSectionId != null) {
            bumpCacheVersion(getSectionVersionKey(oldSectionId));
        }
        if (newSectionId != null && !newSectionId.equals(oldSectionId)) {
            bumpCacheVersion(getSectionVersionKey(newSectionId));
        }
    }

    public void bumpPostDetailCacheVersion(String postId) {
        if (!StringUtils.hasText(postId)) {
            return;
        }
        bumpCacheVersion(POST_DETAIL_CACHE_VERSION_KEY_PREFIX + postId);
    }

    /** feed + 详情一并失效,适配"帖子有互动"类写路径(浏览、评论等) */
    public void invalidatePostCaches(Long sectionId, String postId) {
        invalidatePostFeedCache(sectionId, sectionId);
        bumpPostDetailCacheVersion(postId);
    }

    // ─── 内部实现 ────────────────────────────────────────────────────────

    private boolean shouldUsePostFeedCache(PostSearchReq request) {
        if (request == null) {
            return false;
        }

        if (Boolean.TRUE.equals(request.getModerationView())
                || Boolean.TRUE.equals(request.getIncludeDeleted())
                || StringUtils.hasText(request.getAuditStatus())) {
            return false;
        }

        if (request.getSectionIds() != null && !request.getSectionIds().isEmpty()) {
            return false;
        }

        // Song：个性化列表与搜索结果不进缓存，聚焦高并发公共帖子流
        if (StringUtils.hasText(request.getKeyword())
                || StringUtils.hasText(request.getUserId())
                || StringUtils.hasText(request.getLikedBy())
                || StringUtils.hasText(request.getCollectedBy())) {
            return false;
        }

        if (request.getPage() != null && request.getPage() > 5 && request.getCursor() == null) {
            return false;
        }

        return true;
    }

    private String buildPostFeedCacheKey(PostSearchReq request) {
        try {
            Map<String, Object> keyParts = new TreeMap<>();
            keyParts.put("page", request.getPage() != null ? request.getPage() : 1);
            keyParts.put("pageSize", PostSupport.clampPageSize(request.getPageSize()));
            keyParts.put("navType", StringUtils.hasText(request.getNavType())
                    ? request.getNavType().trim().toLowerCase()
                    : "");
            keyParts.put("category", StringUtils.hasText(request.getCategory())
                    ? request.getCategory().trim()
                    : "");
            keyParts.put("orderBy", StringUtils.hasText(request.getOrderBy())
                    ? request.getOrderBy().toLowerCase()
                    : "new");
            keyParts.put("timeRange", StringUtils.hasText(request.getTimeRange())
                    ? request.getTimeRange().toUpperCase()
                    : "");
            keyParts.put("status", request.getStatus() != null ? request.getStatus() : -1);
            keyParts.put("sectionId", request.getSectionId() != null ? request.getSectionId() : 0L);
            keyParts.put("tag", StringUtils.hasText(request.getTag()) ? request.getTag().trim() : "");
            keyParts.put("isFeatured", Boolean.TRUE.equals(request.getIsFeatured()) ? 1 : 0);
            keyParts.put("pinnedOnly", Boolean.TRUE.equals(request.getPinnedOnly()) ? 1 : 0);
            keyParts.put("answerState", StringUtils.hasText(request.getAnswerState())
                    ? request.getAnswerState().trim().toLowerCase()
                    : "");
            keyParts.put("cursor", request.getCursor() != null ? request.getCursor().toString() : "");
            keyParts.put("cursorId", StringUtils.hasText(request.getCursorId()) ? request.getCursorId() : "");
            keyParts.put("needTotal", Boolean.TRUE.equals(request.getNeedTotal()) ? 1 : 0);
            keyParts.put("globalVer", getCacheVersion(POST_FEED_CACHE_GLOBAL_VERSION_KEY));
            if (request.getSectionId() != null) {
                keyParts.put("sectionVer", getCacheVersion(getSectionVersionKey(request.getSectionId())));
            } else {
                keyParts.put("sectionVer", 0L);
            }

            String rawKey = objectMapper.writeValueAsString(keyParts);
            String digest = DigestUtils.md5DigestAsHex(rawKey.getBytes(StandardCharsets.UTF_8));
            return POST_FEED_CACHE_PREFIX + digest;
        } catch (Exception e) {
            log.debug("构建帖子流缓存 Key 失败: {}", e.getMessage());
            return null;
        }
    }

    private String buildPostDetailCacheKey(String postId) {
        try {
            Map<String, Object> keyParts = new TreeMap<>();
            keyParts.put("postId", postId);
            keyParts.put("ver", getCacheVersion(POST_DETAIL_CACHE_VERSION_KEY_PREFIX + postId));
            String rawKey = objectMapper.writeValueAsString(keyParts);
            String digest = DigestUtils.md5DigestAsHex(rawKey.getBytes(StandardCharsets.UTF_8));
            return POST_DETAIL_CACHE_PREFIX + digest;
        } catch (Exception e) {
            return null;
        }
    }

    private long getCacheVersion(String versionKey) {
        try {
            String value = stringRedisTemplate.opsForValue().get(versionKey);
            return StringUtils.hasText(value) ? Long.parseLong(value) : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private void bumpCacheVersion(String versionKey) {
        try {
            stringRedisTemplate.opsForValue().increment(versionKey);
            stringRedisTemplate.expire(versionKey, Duration.ofHours(Math.max(1L, versionKeyTtlHours)));
        } catch (Exception e) {
            log.debug("递增缓存版本失败: key={}, err={}", versionKey, e.getMessage());
        }
    }

    private String getSectionVersionKey(Long sectionId) {
        return POST_FEED_CACHE_SECTION_VERSION_KEY_PREFIX + sectionId;
    }

    private Duration withCacheTtlJitter(Duration baseTtl) {
        if (baseTtl == null || baseTtl.isNegative() || baseTtl.isZero()) {
            return baseTtl;
        }
        double safeRatio = Math.max(0.0d, Math.min(0.5d, ttlJitterRatio));
        if (safeRatio <= 0.0d) {
            return baseTtl;
        }
        long baseMillis = baseTtl.toMillis();
        long jitterMillis = (long) (baseMillis * safeRatio * ThreadLocalRandom.current().nextDouble());
        return Duration.ofMillis(baseMillis + jitterMillis);
    }

    private Duration resolveNullValueTtl() {
        return Duration.ofSeconds(Math.max(10L, nullValueTtlSeconds));
    }

    // ─── 载体类型 ────────────────────────────────────────────────────────

    /** 区分"命中且值为 null(空值缓存)"与"未命中" */
    public static final class CachedDetail {
        private final boolean hit;
        private final PostResp value;

        private CachedDetail(boolean hit, PostResp value) {
            this.hit = hit;
            this.value = value;
        }

        static CachedDetail hit(PostResp value) {
            return new CachedDetail(true, value);
        }

        static CachedDetail miss() {
            return new CachedDetail(false, null);
        }

        public boolean isHit() {
            return hit;
        }

        public PostResp getValue() {
            return value;
        }
    }

    private static class PostFeedCachePayload {
        public long current;
        public long size;
        public long total;
        public List<PostResp> records;
    }
}
