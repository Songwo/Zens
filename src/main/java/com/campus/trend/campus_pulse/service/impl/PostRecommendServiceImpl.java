package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.trend.campus_pulse.config.properties.RecommendationWeightProperties;
import com.campus.trend.campus_pulse.dto.response
        .PostRecommendResp;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.Section;
import com.campus.trend.campus_pulse.entity.Tag;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.entity.UserTagRelation;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.mapper.SectionMapper;
import com.campus.trend.campus_pulse.mapper.UserMapper;
import com.campus.trend.campus_pulse.service.CollaborativeFilteringService;
import com.campus.trend.campus_pulse.service.PostRecommendService;
import com.campus.trend.campus_pulse.service.TagService;
import com.campus.trend.campus_pulse.service.UserTagRelationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PostRecommendServiceImpl implements PostRecommendService {

    private static final String AUDIT_STATUS_PENDING = "PENDING";
    private static final String AUDIT_STATUS_APPROVED = "APPROVED";
    private final UserTagRelationService userTagRelationService;
    private final TagService tagService;
    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final SectionMapper sectionMapper;
    private final CollaborativeFilteringService collaborativeFilteringService;
    private final StringRedisTemplate redisTemplate;
    private final RecommendationWeightProperties weightProperties;
    private final ObjectMapper objectMapper;

    @Autowired
    public PostRecommendServiceImpl(UserTagRelationService userTagRelationService,
            TagService tagService,
            PostMapper postMapper,
            UserMapper userMapper,
            SectionMapper sectionMapper,
            CollaborativeFilteringService collaborativeFilteringService,
            StringRedisTemplate redisTemplate,
            RecommendationWeightProperties weightProperties,
            ObjectMapper objectMapper) {
        this.userTagRelationService = userTagRelationService;
        this.tagService = tagService;
        this.postMapper = postMapper;
        this.userMapper = userMapper;
        this.sectionMapper = sectionMapper;
        this.collaborativeFilteringService = collaborativeFilteringService;
        this.redisTemplate = redisTemplate;
        this.weightProperties = weightProperties;
        this.objectMapper = objectMapper;
    }

    private static final String RECOMMEND_CACHE_KEY = "user:recommend:";
    private static final String POST_DETAIL_RECOMMEND_CACHE_KEY = "post:recommend:detail:";
    private static final long RECOMMEND_CACHE_TTL_SECONDS = 90;
    private static final long POST_DETAIL_RECOMMEND_CACHE_TTL_SECONDS = 90;
    private static final int MAX_POST_DETAIL_RECOMMEND_LIMIT = 10;

    @Override
    public List<PostRecommendResp> getPostDetailRecommendations(String postId, String userId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_POST_DETAIL_RECOMMEND_LIMIT);
        String cacheKey = buildPostDetailRecommendCacheKey(postId, userId, safeLimit);
        List<PostRecommendResp> cached = readPostDetailRecommendCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        Post currentPost = postMapper.selectOne(applyPublicVisibilityFilter(new LambdaQueryWrapper<Post>())
                .select(Post::getId, Post::getSectionId)
                .eq(Post::getId, postId)
                .last("LIMIT 1"));
        if (currentPost == null) {
            return Collections.emptyList();
        }

        List<Post> recommendations = new ArrayList<>();
        Set<String> recommendedIds = new HashSet<>();
        recommendedIds.add(postId);

        // Song：1. 同专业推荐 (如果登录且有专业信息)
        if (StringUtils.hasText(userId)) {
            User currentUser = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .select(User::getId, User::getMajor)
                    .eq(User::getId, userId)
                    .last("LIMIT 1"));
            if (currentUser != null && StringUtils.hasText(currentUser.getMajor())) {
                List<User> sameMajorUsers = userMapper.selectList(new LambdaQueryWrapper<User>()
                        .select(User::getId)
                        .eq(User::getMajor, currentUser.getMajor()));
                List<String> sameMajorUserIds = sameMajorUsers.stream()
                        .map(User::getId)
                        .distinct()
                        .toList();
                if (!sameMajorUserIds.isEmpty()) {
                    List<Post> majorPosts = postMapper.selectList(baseRecommendPostQuery()
                            .eq(Post::getStatus, 1)
                            .ne(Post::getId, postId)
                            .in(Post::getUserId, sameMajorUserIds)
                            .last("LIMIT " + safeLimit));
                    for (Post p : majorPosts) {
                        if (recommendedIds.add(p.getId())) {
                            recommendations.add(p);
                        }
                    }
                }
            }
        }

        // Song：2. 相同板块或标签
        if (recommendations.size() < safeLimit) {
            List<Post> catPosts = postMapper.selectList(baseRecommendPostQuery()
                    .eq(Post::getStatus, 1)
                    .eq(Post::getSectionId, currentPost.getSectionId())
                    .ne(Post::getId, postId)
                    .last("LIMIT " + safeLimit));
            for (Post p : catPosts) {
                if (recommendedIds.add(p.getId())) {
                    recommendations.add(p);
                }
            }
        }

        // Song：3. 热度兜底
        if (recommendations.size() < safeLimit) {
            List<Post> hotPosts = getHotPostsList(safeLimit * 2);
            for (Post p : hotPosts) {
                if (recommendedIds.add(p.getId())) {
                    recommendations.add(p);
                }
            }
        }

        // Song：裁剪
        List<Post> resultPosts = recommendations.stream().limit(safeLimit).collect(Collectors.toList());
        List<PostRecommendResp> result = convertToRecommendDTO(resultPosts, "相关内容推荐");
        writePostDetailRecommendCache(cacheKey, result);
        return result;
    }

    @Override
    public IPage<PostRecommendResp> getHybridRecommendations(String userId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = clampPageSize(pageSize);
        String cacheKey = buildRecommendCacheKey(userId, safePage, safePageSize);
        IPage<PostRecommendResp> cached = readRecommendCache(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        List<PostRecommendResp> allRecommendations = new ArrayList<>();

        if (userId == null) {
            // Song：匿名用户：仅推荐热门
            List<Post> hotPosts = getHotPostsList(weightProperties.getAnonymousHotLimit());
            allRecommendations = convertToRecommendDTO(hotPosts, "校园热门推荐");
            log.info("生成匿名推荐: totalCandidates={}", allRecommendations.size());
        } else {
            // Song：登录用户：混合推荐
            log.info("生成用户推荐: userId={}, page={}, pageSize={}", userId, safePage, safePageSize);
            
            List<Post> interestPosts = recommendPostsList(userId, weightProperties.getInterestLimit());
            allRecommendations.addAll(convertToRecommendDTO(interestPosts, "基于你的兴趣标签"));

            try {
                List<Post> cfPosts = collaborativeFilteringService.recommendByUserBased(userId, weightProperties.getCfLimit());
                allRecommendations.addAll(convertToRecommendDTO(cfPosts, "志趣相投的同学也在看"));
            } catch (Exception e) {
                log.warn("Collaborative filtering failed: {}", e.getMessage());
            }

            List<Post> hotPosts = getHotPostsList(weightProperties.getHotLimit());
            allRecommendations.addAll(convertToRecommendDTO(hotPosts, "全校都在看"));
        }

        // Song：2. 去重并按综合分排序（替代随机打乱）
        Map<String, PostRecommendResp> uniqueMap = new LinkedHashMap<>();
        for (PostRecommendResp resp : allRecommendations) {
            uniqueMap.putIfAbsent(resp.getId(), resp);
        }
        
        List<PostRecommendResp> resultList = new ArrayList<>(uniqueMap.values());
        resultList.sort((a, b) -> Double.compare(scoreRecommendResp(b), scoreRecommendResp(a)));

        // Song：3. 手动分页
        int start = (safePage - 1) * safePageSize;
        int end = Math.min(start + safePageSize, resultList.size());
        
        List<PostRecommendResp> paginatedList = new ArrayList<>();
        if (start < resultList.size()) {
            paginatedList = resultList.subList(start, end);
        }

        Page<PostRecommendResp> resultPage = new Page<>(safePage, safePageSize);
        resultPage.setRecords(paginatedList);
        resultPage.setTotal(resultList.size());

        writeRecommendCache(cacheKey, resultPage);

        return resultPage;
    }

    private List<PostRecommendResp> convertToRecommendDTO(List<Post> posts, String reason) {
        if (posts == null || posts.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> userIds = posts.stream().map(Post::getUserId).distinct().toList();
        List<Long> sectionIds = posts.stream()
                .map(Post::getSectionId)
                .filter(id -> id != null)
                .distinct()
                .toList();

        Map<String, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            userMapper.selectList(new LambdaQueryWrapper<User>()
                    .select(User::getId, User::getNickname, User::getAvatar)
                    .in(User::getId, userIds)).forEach(u -> userMap.put(u.getId(), u));
        }

        Map<Long, Section> sectionMap = new HashMap<>();
        if (!sectionIds.isEmpty()) {
            sectionMapper.selectList(new LambdaQueryWrapper<Section>()
                    .select(Section::getId, Section::getName)
                    .in(Section::getId, sectionIds)).forEach(s -> sectionMap.put(s.getId(), s));
        }

        return posts.stream().map(post -> {
            PostRecommendResp dto = new PostRecommendResp();
            dto.setId(post.getId());
            dto.setTitle(post.getTitle());
            dto.setSummary(buildRecommendSummary(post));

            // Song：作者
            var author = userMap.get(post.getUserId());
            if (author != null) {
                dto.setAuthorName(author.getNickname());
                dto.setAuthorAvatar(author.getAvatar());
                dto.setAuthorBadgeText(author.getBadgeText());
            } else {
                dto.setAuthorName("未知用户");
            }

            // Song：板块
            var section = sectionMap.get(post.getSectionId());
            if (section != null) {
                dto.setSectionName(section.getName());
            } else {
                dto.setSectionName("默认板块");
            }

            // Song：标签
            if (StringUtils.hasText(post.getTags())) {
                dto.setTags(Arrays.asList(post.getTags().split("[\\s#]+")).stream().filter(t -> !t.isEmpty()).collect(Collectors.toList()));
            }

            dto.setViewCount(post.getViewCount());
            dto.setLikeCount(post.getLikeCount());
            dto.setCollectCount(post.getCollectCount());
            dto.setCommentCount(post.getCommentCount());
            dto.setCreateTime(post.getCreateTime() != null ? post.getCreateTime().toString() : null);
            dto.setRecommendReason(reason);
            dto.setIsLiked(post.getIsLiked());
            dto.setIsCollected(post.getIsCollected());
            return dto;
        }).collect(Collectors.toList());
    }

    private List<Post> recommendPostsList(String userId, int limit) {
        List<UserTagRelation> userTags = userTagRelationService.lambdaQuery()
                .eq(UserTagRelation::getUserId, userId)
                .list();
        if (userTags.isEmpty()) return Collections.emptyList();

        Map<Long, String> tagNameMap = resolveTagNameMap(userTags);
        Set<String> tagNames = tagNameMap.values().stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        if (tagNames.isEmpty()) {
            return Collections.emptyList();
        }

        List<Post> candidatePosts = postMapper.selectList(baseRecommendPostQuery()
                .eq(Post::getStatus, 1)
                .orderByDesc(Post::getCreateTime)
                .last("LIMIT " + weightProperties.getCandidateLimit()));

        return candidatePosts.stream()
                .filter(post -> StringUtils.hasText(post.getTags()))
                .filter(post -> containsAnyTag(post.getTags(), tagNames))
                .sorted((a, b) -> Double.compare(
                        calculateScore(b, userTags, tagNameMap),
                        calculateScore(a, userTags, tagNameMap)))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private List<Post> getHotPostsList(int limit) {
        return postMapper.selectList(baseRecommendPostQuery()
                .eq(Post::getStatus, 1)
                .orderByDesc(Post::getHeatScore)
                .last("LIMIT " + limit));
    }

    @Override
    public IPage<Post> recommendPosts(String userId, int page, int pageSize) {
        if (userId == null) {
            log.info("匿名用户访问，返回热门帖子");
            return getHotPosts(page, pageSize);
        }

        List<UserTagRelation> userTags = userTagRelationService.lambdaQuery()
                .eq(UserTagRelation::getUserId, userId)
                .list();

        if (userTags.isEmpty()) {
            log.info("用户 [{}] 未关注任何标签，返回热门", userId);
            return getHotPosts(page, pageSize);
        }

        Map<Long, String> tagNameMap = resolveTagNameMap(userTags);
        Set<String> tagNames = tagNameMap.values().stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (tagNames.isEmpty()) {
            return getHotPosts(page, pageSize);
        }

        List<Post> candidatePosts = postMapper.selectList(baseRecommendPostQuery()
                        .eq(Post::getStatus, 1)
                        .orderByDesc(Post::getCreateTime)
                        .last("LIMIT " + weightProperties.getCandidateLimit()))
                .stream()
                .filter(post -> StringUtils.hasText(post.getTags()))
                .filter(post -> containsAnyTag(post.getTags(), tagNames))
                .collect(Collectors.toList());

        // Song：计算相关度分数并排序
        List<PostWithScore> scoredPosts = candidatePosts.stream()
                .map(post -> {
                    double score = calculateScore(post, userTags, tagNameMap);
                    return new PostWithScore(post, score);
                })
                .sorted((a, b) -> Double.compare(b.score, a.score)) // Song：降序
                .collect(Collectors.toList());

        // Song：分页处理
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, scoredPosts.size());

        List<Post> paginatedPosts = new ArrayList<>();
        if (start < scoredPosts.size()) {
            paginatedPosts = scoredPosts.subList(start, end).stream()
                    .map(ps -> ps.post)
                    .collect(Collectors.toList());
        }

        Page<Post> resultPage = new Page<>(page, pageSize);
        resultPage.setRecords(paginatedPosts);
        resultPage.setTotal(scoredPosts.size());

        log.info("为用户 [{}] 推荐了 {} 个帖子", userId, paginatedPosts.size());

        return resultPage;
    }

    @Override
    public double calculateScore(Post post, List<UserTagRelation> userTags) {
        return calculateScore(post, userTags, resolveTagNameMap(userTags));
    }

    private double calculateScore(Post post, List<UserTagRelation> userTags, Map<Long, String> tagNameMap) {
        if (post.getTags() == null || post.getTags().isEmpty()) {
            return 0.0;
        }

        // Song：1. 标签匹配度分数
        double tagScore = 0.0;
        String[] postTags = post.getTags().split("[\\s#]+");
        Set<String> postTagSet = Arrays.stream(postTags)
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toSet());

        for (UserTagRelation userTag : userTags) {
            String tagName = tagNameMap.get(userTag.getTagId());
            if (StringUtils.hasText(tagName) && postTagSet.contains(tagName)) {
                // Song：用户兴趣权重
                tagScore += userTag.getScore().doubleValue();
            }
        }

        // Song：2. 时间因子
        double timeFactor = calculateTimeFactor(post.getCreateTime());

        // Song：3. 互动因子
        double engagementFactor = calculateEngagementFactor(post);

        double finalScore = (tagScore * weightProperties.getTagScoreWeight())
                * (timeFactor * weightProperties.getTimeFactorWeight())
                * (engagementFactor * weightProperties.getEngagementFactorWeight());

        if (log.isDebugEnabled()) {
            log.debug("推荐打分: postId={}, tagScore={}, timeFactor={}, engagementFactor={}, finalScore={}",
                    post.getId(), tagScore, timeFactor, engagementFactor, finalScore);
        }

        return finalScore;
    }

    private Map<Long, String> resolveTagNameMap(List<UserTagRelation> userTags) {
        if (userTags == null || userTags.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> tagIds = userTags.stream()
                .map(UserTagRelation::getTagId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (tagIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return tagService.listByIds(tagIds).stream()
                .filter(Objects::nonNull)
                .filter(tag -> tag.getId() != null && StringUtils.hasText(tag.getName()))
                .collect(Collectors.toMap(Tag::getId, Tag::getName, (left, right) -> left));
    }

    @Override
    public List<Tag> recommendTags(String userId, int limit) {
        if (userId == null) {
            log.info("匿名用户访问，返回热门标签");
            return tagService.getHotTags(limit);
        }

        List<Tag> followedTags = userTagRelationService.getUserFollowingTags(userId);
        Set<Long> followedTagIds = followedTags.stream()
                .map(Tag::getId)
                .collect(Collectors.toSet());

        List<Tag> hotTags = tagService.getHotTags(limit * 2);
        List<Tag> recommendations = hotTags.stream()
                .filter(tag -> !followedTagIds.contains(tag.getId()))
                .limit(limit)
                .collect(Collectors.toList());

        log.info("为用户 [{}] 推荐了 {} 个标签", userId, recommendations.size());

        return recommendations;
    }

    private IPage<Post> getHotPosts(int page, int pageSize) {
        Page<Post> queryPage = new Page<>(page, pageSize);
        return postMapper.selectPage(queryPage,
                applyPublicVisibilityFilter(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Post>())
                        .orderByDesc(Post::getHeatScore)
                        .orderByDesc(Post::getCreateTime));
    }

    private boolean containsAnyTag(String postTags, Set<String> userTagNames) {
        String[] tags = postTags.split("[\\s#]+");
        for (String tag : tags) {
            String trimmed = tag.trim();
            if (!trimmed.isEmpty() && userTagNames.contains(trimmed)) {
                return true;
            }
        }
        return false;
    }

    private LambdaQueryWrapper<Post> baseRecommendPostQuery() {
        return applyPublicVisibilityFilter(new LambdaQueryWrapper<Post>()).select(
                Post::getId,
                Post::getUserId,
                Post::getSectionId,
                Post::getTitle,
                Post::getSummary,
                Post::getTags,
                Post::getViewCount,
                Post::getLikeCount,
                Post::getCollectCount,
                Post::getCommentCount,
                Post::getHeatScore,
                Post::getCreateTime);
    }

    private LambdaQueryWrapper<Post> applyPublicVisibilityFilter(LambdaQueryWrapper<Post> wrapper) {
        return wrapper.eq(Post::getStatus, 1)
                .and(w -> w.isNull(Post::getAuditStatus)
                        .or()
                        .eq(Post::getAuditStatus, "")
                        .or()
                        .eq(Post::getAuditStatus, AUDIT_STATUS_PENDING)
                        .or()
                        .eq(Post::getAuditStatus, AUDIT_STATUS_APPROVED));
    }

    private String buildRecommendSummary(Post post) {
        String text = StringUtils.hasText(post.getSummary())
                ? post.getSummary().trim()
                : (StringUtils.hasText(post.getContent()) ? post.getContent().trim() : "");
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.length() > 100 ? text.substring(0, 100) + "..." : text;
    }

    private double calculateTimeFactor(LocalDateTime createTime) {
        if (createTime == null) {
            return 0.5;
        }
        Duration age = Duration.between(createTime, LocalDateTime.now());
        long ageInDays = age.toDays();
        return 1.0 / Math.pow(1 + (ageInDays / weightProperties.getTimeDecayDays()), weightProperties.getTimeDecayExponent());
    }

    private double calculateEngagementFactor(Post post) {
        int likes = post.getLikeCount() != null ? post.getLikeCount() : 0;
        int comments = post.getCommentCount() != null ? post.getCommentCount() : 0;
        int collects = post.getCollectCount() != null ? post.getCollectCount() : 0;
        double engagementScore = likes * weightProperties.getLikeWeight()
                + comments * weightProperties.getCommentWeight()
                + collects * weightProperties.getCollectWeight();
        return 1.0 + Math.min(
                engagementScore / weightProperties.getEngagementDivisor(),
                weightProperties.getMaxEngagementBoost());
    }

    private int clampPageSize(int pageSize) {
        if (pageSize <= 0) {
            return 10;
        }
        return Math.min(pageSize, 50);
    }

    private double scoreRecommendResp(PostRecommendResp resp) {
        int likes = resp.getLikeCount() != null ? resp.getLikeCount() : 0;
        int comments = resp.getCommentCount() != null ? resp.getCommentCount() : 0;
        int collects = resp.getCollectCount() != null ? resp.getCollectCount() : 0;
        int views = resp.getViewCount() != null ? resp.getViewCount() : 0;

        double engagement = likes * 1.0 + comments * 2.0 + collects * 1.5 + views * 0.03;

        double reasonBoost = 1.0;
        String reason = resp.getRecommendReason() != null ? resp.getRecommendReason() : "";
        if (reason.contains("兴趣")) {
            reasonBoost = 1.12;
        } else if (reason.contains("志趣")) {
            reasonBoost = 1.08;
        } else if (reason.contains("热门")) {
            reasonBoost = 1.04;
        }

        double recencyBoost = 0.0;
        try {
            if (resp.getCreateTime() != null) {
                LocalDateTime createTime = LocalDateTime.parse(resp.getCreateTime());
                long days = Math.max(Duration.between(createTime, LocalDateTime.now()).toDays(), 0);
                recencyBoost = Math.max(0, 7 - days) * 0.6;
            }
        } catch (DateTimeParseException ignored) {
        }

        return engagement * reasonBoost + recencyBoost;
    }

    private String buildRecommendCacheKey(String userId, int page, int pageSize) {
        String uid = userId == null ? "anonymous" : userId;
        return RECOMMEND_CACHE_KEY + uid + ":" + page + ":" + pageSize;
    }

    private String buildPostDetailRecommendCacheKey(String postId, String userId, int limit) {
        String uid = StringUtils.hasText(userId) ? userId : "anonymous";
        return POST_DETAIL_RECOMMEND_CACHE_KEY + postId + ":" + uid + ":" + limit;
    }

    private IPage<PostRecommendResp> readRecommendCache(String cacheKey) {
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (!StringUtils.hasText(cached)) {
                return null;
            }
            RecommendCachePayload payload = objectMapper.readValue(cached, RecommendCachePayload.class);
            Page<PostRecommendResp> page = new Page<>(payload.current, payload.size, payload.total);
            page.setRecords(payload.records != null ? payload.records : new ArrayList<>());
            return page;
        } catch (Exception e) {
            log.debug("读取推荐缓存失败: key={}, err={}", cacheKey, e.getMessage());
            return null;
        }
    }

    private List<PostRecommendResp> readPostDetailRecommendCache(String cacheKey) {
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (!StringUtils.hasText(cached)) {
                return null;
            }
            return objectMapper.readValue(cached, new TypeReference<List<PostRecommendResp>>() {
            });
        } catch (Exception e) {
            log.debug("读取帖子详情推荐缓存失败: key={}, err={}", cacheKey, e.getMessage());
            return null;
        }
    }

    private void writeRecommendCache(String cacheKey, IPage<PostRecommendResp> page) {
        try {
            RecommendCachePayload payload = new RecommendCachePayload();
            payload.current = page.getCurrent();
            payload.size = page.getSize();
            payload.total = page.getTotal();
            payload.records = page.getRecords();

            redisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(payload),
                    RECOMMEND_CACHE_TTL_SECONDS,
                    TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("写入推荐缓存失败: key={}, err={}", cacheKey, e.getMessage());
        }
    }

    private void writePostDetailRecommendCache(String cacheKey, List<PostRecommendResp> recommendations) {
        try {
            redisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(recommendations != null ? recommendations : Collections.emptyList()),
                    POST_DETAIL_RECOMMEND_CACHE_TTL_SECONDS,
                    TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("写入帖子详情推荐缓存失败: key={}, err={}", cacheKey, e.getMessage());
        }
    }

    private static class RecommendCachePayload {
        public long current;
        public long size;
        public long total;
        public List<PostRecommendResp> records;
    }

    private static class PostWithScore {
        Post post;
        double score;
        PostWithScore(Post post, double score) {
            this.post = post;
            this.score = score;
        }
    }
}
