package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.trend.campus_pulse.dto.response.PublicHomeBootstrapResp;
import com.campus.trend.campus_pulse.dto.response.SectionResp;
import com.campus.trend.campus_pulse.dto.response.SiteStatsResp;
import com.campus.trend.campus_pulse.entity.AnswerAdoption;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.Section;
import com.campus.trend.campus_pulse.entity.Tag;
import com.campus.trend.campus_pulse.entity.UserTagRelation;
import com.campus.trend.campus_pulse.mapper.AnswerAdoptionMapper;
import com.campus.trend.campus_pulse.mapper.CommentMapper;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.mapper.SectionMapper;
import com.campus.trend.campus_pulse.mapper.TagMapper;
import com.campus.trend.campus_pulse.mapper.UserMapper;
import com.campus.trend.campus_pulse.mapper.UserTagRelationMapper;
import com.campus.trend.campus_pulse.service.PublicDataService;
import com.campus.trend.campus_pulse.service.SectionService;
import com.campus.trend.campus_pulse.service.TagService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublicDataServiceImpl implements PublicDataService {

    private static final String AUDIT_STATUS_APPROVED = "APPROVED";
    private static final String QA_SECTION_NAME = "答疑解惑";
    private static final Duration SITE_STATS_CACHE_TTL = Duration.ofSeconds(30);
    private static final Duration HOT_RANK_CACHE_TTL = Duration.ofSeconds(20);
    private static final Duration HOME_BOOTSTRAP_CACHE_TTL = Duration.ofSeconds(45);
    private static final String SITE_STATS_CACHE_KEY = "stats:site:summary";
    private static final String HOT_RANK_CACHE_PREFIX = "public:hot-rank:";
    private static final String HOME_BOOTSTRAP_CACHE_PREFIX = "public:home:bootstrap:";

    private final SectionService sectionService;
    private final TagService tagService;
    private final PostMapper postMapper;
    private final SectionMapper sectionMapper;
    private final AnswerAdoptionMapper answerAdoptionMapper;
    private final UserMapper userMapper;
    private final CommentMapper commentMapper;
    private final UserTagRelationMapper userTagRelationMapper;
    private final TagMapper tagMapper;
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
        String currentUserId = SecurityUtils.getCurrentUserId();
        String cacheUserScope = StringUtils.hasText(currentUserId) ? "user:" + currentUserId : "guest";
        String cacheKey = HOME_BOOTSTRAP_CACHE_PREFIX + cacheUserScope + ":" + safeHotTagLimit + ":" + safeHotRankLimit + ":" + normalizedTimeRange;

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
        resp.setUnsolvedQaCount(countUnsolvedQaPosts());
        resp.setTodaySolvedQaCount(countTodaySolvedQaPosts());
        resp.setFollowedTagUpdateCount(countTodayFollowedTagPosts(currentUserId));

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
                        .eq(Post::getAuditStatus, AUDIT_STATUS_APPROVED));
    }

    private List<Long> getActiveQaSectionIds() {
        try {
            return sectionMapper.selectList(new LambdaQueryWrapper<Section>()
                            .select(Section::getId)
                            .eq(Section::getStatus, 1)
                            .eq(Section::getName, QA_SECTION_NAME))
                    .stream()
                    .map(Section::getId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        } catch (Exception e) {
            log.debug("查询{}板块失败: {}", QA_SECTION_NAME, e.getMessage());
            return Collections.emptyList();
        }
    }

    private long countUnsolvedQaPosts() {
        List<Long> qaSectionIds = getActiveQaSectionIds();
        if (qaSectionIds.isEmpty()) {
            return 0;
        }
        try {
            LambdaQueryWrapper<Post> wrapper = applyPublicVisibility(new LambdaQueryWrapper<Post>())
                    .in(Post::getSectionId, qaSectionIds)
                    .and(w -> w.isNull(Post::getHasAdoptedAnswer).or().ne(Post::getHasAdoptedAnswer, 1));
            return postMapper.selectCount(wrapper);
        } catch (Exception e) {
            log.debug("统计待解决问答失败: {}", e.getMessage());
            return 0;
        }
    }

    private long countTodaySolvedQaPosts() {
        try {
            List<String> solvedPostIds = answerAdoptionMapper.selectList(new LambdaQueryWrapper<AnswerAdoption>()
                            .select(AnswerAdoption::getPostId)
                            .ge(AnswerAdoption::getAdoptedAt, LocalDate.now().atStartOfDay()))
                    .stream()
                    .map(AnswerAdoption::getPostId)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
            if (solvedPostIds.isEmpty()) {
                return 0;
            }

            List<Long> qaSectionIds = getActiveQaSectionIds();
            if (qaSectionIds.isEmpty()) {
                return 0;
            }

            return postMapper.selectCount(applyPublicVisibility(new LambdaQueryWrapper<Post>())
                    .in(Post::getId, solvedPostIds)
                    .in(Post::getSectionId, qaSectionIds)
                    .eq(Post::getHasAdoptedAnswer, 1));
        } catch (Exception e) {
            log.debug("统计今日已解决问答失败: {}", e.getMessage());
            return 0;
        }
    }

    private long countTodayFollowedTagPosts(String userId) {
        if (!StringUtils.hasText(userId)) {
            return 0;
        }

        try {
            List<Long> followedTagIds = userTagRelationMapper.selectList(new LambdaQueryWrapper<UserTagRelation>()
                            .select(UserTagRelation::getTagId)
                            .eq(UserTagRelation::getUserId, userId))
                    .stream()
                    .map(UserTagRelation::getTagId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();

            if (followedTagIds.isEmpty()) {
                return 0;
            }

            Set<String> followedTagNames = tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                            .select(Tag::getName)
                            .in(Tag::getId, followedTagIds))
                    .stream()
                    .map(Tag::getName)
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

            if (followedTagNames.isEmpty()) {
                return 0;
            }

            LambdaQueryWrapper<Post> wrapper = applyPublicVisibility(new LambdaQueryWrapper<Post>())
                    .ge(Post::getCreateTime, LocalDate.now().atStartOfDay())
                    .and(tags -> {
                        boolean first = true;
                        for (String tagName : followedTagNames) {
                            String spacedTagName = " " + tagName;
                            if (first) {
                                tags.apply("FIND_IN_SET({0}, tags)", tagName)
                                        .or()
                                        .apply("FIND_IN_SET({0}, tags)", spacedTagName)
                                        .or()
                                        .eq(Post::getTags, tagName);
                                first = false;
                            } else {
                                tags.or()
                                        .apply("FIND_IN_SET({0}, tags)", tagName)
                                        .or()
                                        .apply("FIND_IN_SET({0}, tags)", spacedTagName)
                                        .or()
                                        .eq(Post::getTags, tagName);
                            }
                        }
                    });
            return postMapper.selectCount(wrapper);
        } catch (Exception e) {
            log.debug("统计关注标签今日更新失败: {}", e.getMessage());
            return 0;
        }
    }
}
