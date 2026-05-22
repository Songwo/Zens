package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.trend.campus_pulse.dto.response.PublicHomeBootstrapResp;
import com.campus.trend.campus_pulse.dto.response.SectionResp;
import com.campus.trend.campus_pulse.dto.response.SiteStatsResp;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.Tag;
import com.campus.trend.campus_pulse.mapper.CommentMapper;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.mapper.UserMapper;
import com.campus.trend.campus_pulse.service.PublicDataService;
import com.campus.trend.campus_pulse.service.SectionService;
import com.campus.trend.campus_pulse.service.TagService;
import com.campus.trend.campus_pulse.utils.TimeRangeUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublicDataServiceImpl implements PublicDataService {

    private static final String AUDIT_STATUS_PENDING = "PENDING";
    private static final String AUDIT_STATUS_APPROVED = "APPROVED";
    private static final Duration SITE_STATS_CACHE_TTL = Duration.ofSeconds(30);
    private static final Duration HOT_RANK_CACHE_TTL = Duration.ofSeconds(20);
    private static final Duration HOME_BOOTSTRAP_CACHE_TTL = Duration.ofSeconds(45);
    private static final String SITE_STATS_CACHE_KEY = "stats:site:summary";
    private static final String HOT_RANK_CACHE_PREFIX = "public:hot-rank:";
    private static final String HOME_BOOTSTRAP_CACHE_PREFIX = "public:home:bootstrap:";

    private final SectionService sectionService;
    private final TagService tagService;
    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final CommentMapper commentMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public SiteStatsResp getSiteStats() {
        try {
            String cachedJson = stringRedisTemplate.opsForValue().get(SITE_STATS_CACHE_KEY);
            if (StringUtils.hasText(cachedJson)) {
                return objectMapper.readValue(cachedJson, SiteStatsResp.class);
            }
        } catch (Exception e) {
            log.debug("读取站点统计缓存失败: {}", e.getMessage());
        }

        SiteStatsResp stats = new SiteStatsResp();
        try {
            stats.setTotalPosts(postMapper.selectCount(applyPublicVisibility(new LambdaQueryWrapper<>())));
            stats.setTotalUsers(userMapper.selectCount(null));
            stats.setTodayPosts(postMapper.selectCount(applyPublicVisibility(new LambdaQueryWrapper<Post>()
                    .ge(Post::getCreateTime, LocalDate.now().atStartOfDay()))));

            long totalComments = 0;
            try {
                totalComments = commentMapper.selectCount(null);
            } catch (Exception ignored) {
            }
            stats.setTotalComments(totalComments);
        } catch (Exception e) {
            log.error("获取站点统计失败", e);
            stats.setTotalPosts(0);
            stats.setTotalUsers(0);
            stats.setTotalComments(0);
            stats.setTodayPosts(0);
        }

        try {
            stringRedisTemplate.opsForValue().set(
                    SITE_STATS_CACHE_KEY,
                    objectMapper.writeValueAsString(stats),
                    SITE_STATS_CACHE_TTL);
        } catch (Exception e) {
            log.debug("写入站点统计缓存失败: {}", e.getMessage());
        }
        return stats;
    }

    @Override
    public List<Map<String, Object>> getHotRank(int limit, String timeRange) {
        int safeLimit = Math.min(Math.max(limit, 1), 20);
        String normalizedTimeRange = normalizeTimeRange(timeRange);
        String cacheKey = HOT_RANK_CACHE_PREFIX + normalizedTimeRange + ":" + safeLimit;

        try {
            String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
            if (StringUtils.hasText(cachedJson)) {
                return objectMapper.readValue(cachedJson, new TypeReference<List<Map<String, Object>>>() {
                });
            }
        } catch (Exception e) {
            log.debug("读取热榜缓存失败: {}", e.getMessage());
        }

        LambdaQueryWrapper<Post> wrapper = applyPublicVisibility(new LambdaQueryWrapper<>());
        wrapper.select(
                Post::getId,
                Post::getTitle,
                Post::getHeatScore,
                Post::getViewCount,
                Post::getLikeCount,
                Post::getCommentCount,
                Post::getLastActivityAt);

        LocalDateTime start = TimeRangeUtils.resolveRangeStart(normalizedTimeRange);
        if (start != null) {
            wrapper.ge(Post::getLastActivityAt, start)
                    .and(w -> w.gt(Post::getViewCount, 0).or().gt(Post::getCommentCount, 0));
        }

        wrapper.orderByDesc(Post::getHeatScore)
                .orderByDesc(Post::getViewCount)
                .orderByDesc(Post::getId)
                .last("LIMIT " + safeLimit);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Post post : postMapper.selectList(wrapper)) {
            Map<String, Object> item = new HashMap<>();
            item.put("postId", post.getId());
            item.put("title", post.getTitle());
            item.put("heatScore", post.getHeatScore() != null ? post.getHeatScore() : 0.0);
            item.put("viewCount", post.getViewCount() != null ? post.getViewCount() : 0);
            item.put("likeCount", post.getLikeCount() != null ? post.getLikeCount() : 0);
            item.put("commentCount", post.getCommentCount() != null ? post.getCommentCount() : 0);
            result.add(item);
        }

        try {
            stringRedisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(result),
                    HOT_RANK_CACHE_TTL);
        } catch (Exception e) {
            log.debug("写入热榜缓存失败: {}", e.getMessage());
        }
        return result;
    }

    @Override
    public PublicHomeBootstrapResp getHomeBootstrap(int hotTagLimit, int hotRankLimit, String timeRange) {
        int safeHotTagLimit = Math.min(Math.max(hotTagLimit, 1), 20);
        int safeHotRankLimit = Math.min(Math.max(hotRankLimit, 1), 20);
        String normalizedTimeRange = normalizeTimeRange(timeRange);
        String cacheKey = HOME_BOOTSTRAP_CACHE_PREFIX + safeHotTagLimit + ":" + safeHotRankLimit + ":" + normalizedTimeRange;

        try {
            String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
            if (StringUtils.hasText(cachedJson)) {
                return objectMapper.readValue(cachedJson, PublicHomeBootstrapResp.class);
            }
        } catch (Exception e) {
            log.debug("读取首页 bootstrap 缓存失败: {}", e.getMessage());
        }

        PublicHomeBootstrapResp resp = new PublicHomeBootstrapResp();
        List<SectionResp> activeSections = sectionService.getActiveSections();
        List<Tag> hotTags = tagService.getHotTags(safeHotTagLimit);
        List<Map<String, Object>> hotRank = getHotRank(safeHotRankLimit, normalizedTimeRange);
        SiteStatsResp siteStats = getSiteStats();

        resp.setActiveSections(activeSections);
        resp.setHotTags(hotTags);
        resp.setHotRank(hotRank);
        resp.setSiteStats(siteStats);

        try {
            stringRedisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(resp),
                    HOME_BOOTSTRAP_CACHE_TTL);
        } catch (Exception e) {
            log.debug("写入首页 bootstrap 缓存失败: {}", e.getMessage());
        }
        return resp;
    }

    private String normalizeTimeRange(String timeRange) {
        if (!StringUtils.hasText(timeRange)) {
            return "WEEK";
        }
        String normalized = timeRange.trim().toUpperCase();
        return switch (normalized) {
            case "TODAY", "WEEK", "MONTH" -> normalized;
            default -> "WEEK";
        };
    }

    private LambdaQueryWrapper<Post> applyPublicVisibility(LambdaQueryWrapper<Post> wrapper) {
        return wrapper.eq(Post::getStatus, 1)
                .and(w -> w.isNull(Post::getAuditStatus)
                        .or()
                        .eq(Post::getAuditStatus, "")
                        .or()
                        .eq(Post::getAuditStatus, AUDIT_STATUS_PENDING)
                        .or()
                        .eq(Post::getAuditStatus, AUDIT_STATUS_APPROVED));
    }
}
