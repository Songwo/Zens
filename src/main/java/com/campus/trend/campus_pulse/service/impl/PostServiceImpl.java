package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.dto.request.PostCreateReq;
import com.campus.trend.campus_pulse.dto.request.TagsExtractReq;
import com.campus.trend.campus_pulse.dto.request.PostSearchReq;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.PostCollect;
import com.campus.trend.campus_pulse.entity.PostLike;
import com.campus.trend.campus_pulse.entity.Tag;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.mapper.SectionMapper;
import com.campus.trend.campus_pulse.service.SectionService;
import com.campus.trend.campus_pulse.service.ContentSecurityService;
import com.campus.trend.campus_pulse.service.PostCollectService;
import com.campus.trend.campus_pulse.service.SentimentAnalysisService;
import java.math.BigDecimal;
import com.campus.trend.campus_pulse.service.PostLikeService;
import com.campus.trend.campus_pulse.service.PostService;
import com.campus.trend.campus_pulse.service.PostEventService;
import com.campus.trend.campus_pulse.service.TagService;
import com.campus.trend.campus_pulse.service.UserService;
import com.campus.trend.campus_pulse.dto.response.PostResp;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.entity.Section;
import com.campus.trend.campus_pulse.utils.IdUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostServiceImpl extends ServiceImpl<PostMapper, Post> implements PostService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 30;
    private static final int SUMMARY_MAX_LEN = 150;
    private static final Duration POST_FEED_CACHE_TTL = Duration.ofSeconds(30);
    private static final String POST_FEED_CACHE_PREFIX = "post:feed:cache:";
    private static final String POST_FEED_CACHE_GLOBAL_VERSION_KEY = "post:feed:version:global";
    private static final String POST_FEED_CACHE_SECTION_VERSION_KEY_PREFIX = "post:feed:version:section:";

    private final PostLikeService postLikeService;
    private final PostCollectService postCollectService;
    private final TagService tagService;
    private final ContentSecurityService contentSecurityService;
    private final SentimentAnalysisService sentimentAnalysisService;
    private final UserService userService;
    private final SectionService sectionService;
    private final SectionMapper sectionMapper;
    private final PostEventService postEventService;
    private final com.campus.trend.campus_pulse.service.LevelService levelService;
    private final com.campus.trend.campus_pulse.service.AiPostAnalysisService aiPostAnalysisService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Post searchByPostId(String postId) {
        Post post = getById(postId);
        if (post != null) {
            try {
                // Song：尝试获取当前登录用户，若未登录会抛出异常，捕获后忽略即可
                com.campus.trend.campus_pulse.security.AuthUser authUser = com.campus.trend.campus_pulse.utils.SecurityUtils
                        .getAuthenticatedUser();
                String userId = authUser.getUser().getId();

                post.setIsLiked(postLikeService.isLike(postId, userId));
                post.setIsCollected(postCollectService.isCollect(postId, userId));
                log.info("Post detail: postId={}, userId={}, isLiked={}, isCollected={}", postId, userId,
                        post.getIsLiked(), post.getIsCollected());
            } catch (Exception e) {
                // Song：说明
                log.debug("SearchByPostId - User not authenticated or error: {}", e.getMessage());
                post.setIsLiked(false);
                post.setIsCollected(false);
            }
        }
        return post;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createPost(PostCreateReq createPostRequest, String userId) {
        // Song：说明
        if (createPostRequest.getSectionId() != null) {
            Section section = sectionService.getSectionById(createPostRequest.getSectionId());
            if (section == null) {
                throw new RuntimeException("板块不存在: " + createPostRequest.getSectionId());
            }
        }

        // Song：2. 内容安全检查 (敏感词过滤)
        String title = createPostRequest.getTitle();
        String content = createPostRequest.getContent();

        if (contentSecurityService.containsSensitiveWords(title)) {
            // Song：策略选择：直接报错拦截，或者替换为 *
            // Song：这里选择替换，保证用户发布不受阻，但内容合规
            title = contentSecurityService.filterSensitiveWords(title);
        }

        if (contentSecurityService.containsSensitiveWords(content)) {
            content = contentSecurityService.filterSensitiveWords(content);
        }
        // Song：如果包含 "抢劫"、"自杀" 等严重违规词，可以直接抛异常拦截
        // Song：说明

        // Song：3.构造帖子
        String postID = IdUtils.genId("POST");

        Post sysPost = new Post();
        sysPost.setId(postID);
        sysPost.setUserId(userId);
        // Song：说明
        sysPost.setSectionId(createPostRequest.getSectionId());
        sysPost.setTitle(title);
        sysPost.setContent(content);
        sysPost.setSummary(buildSummary(content));
        sysPost.setCoverImage(createPostRequest.getCoverImage());
        sysPost.setTags(createPostRequest.getTags());

        // Song：3.1 情感分析
        double sentimentScore = sentimentAnalysisService.analyzeSentiment(title + " " + content);
        sysPost.setSentimentScore(BigDecimal.valueOf(sentimentScore));

        sysPost.setIsAnonymous(createPostRequest.getIsAnonymous());
        sysPost.setLocationName(createPostRequest.getLocationName());

        // Song：处理图片
        if (StringUtils.hasText(createPostRequest.getImages())) {
            try {
                List<String> images = objectMapper.readValue(createPostRequest.getImages(),
                        new TypeReference<List<String>>() {
                        });
                sysPost.setImages(images);
            } catch (Exception e) {
                // Song：如果解析失败，尝试作为单个图片处理，或者忽略
                List<String> images = new ArrayList<>();
                images.add(createPostRequest.getImages());
                sysPost.setImages(images);
            }
        }

        sysPost.setUpdateTime(LocalDateTime.now());
        sysPost.setCreateTime(LocalDateTime.now());
        sysPost.setViewCount(0);
        sysPost.setLikeCount(0);
        sysPost.setCollectCount(0);
        sysPost.setCommentCount(0);
        sysPost.setStatus(1); // Song：正常
        sysPost.setAuditStatus("PENDING"); // Song：待审核
        sysPost.setHeatScore(0.0); // Song：初始热度

        // Song：初始化置顶和活跃时间字段
        sysPost.setGlobalPin(0);
        sysPost.setCategoryPin(0);
        sysPost.setPinOrder(0);
        sysPost.setLastActivityAt(LocalDateTime.now());

        // Song：3.保存帖子
        this.save(sysPost);

        // Song：推送新帖创建事件
        try {
            postEventService.pushPostCreated(sysPost);
        } catch (Exception e) {
            log.warn("推送新帖创建事件失败", e);
        }

        // Song：4.处理标签（自动创建标签并增加热度）
        processPostTags(createPostRequest.getTags());

        // Song：5.更新用户画像
        userService.incrementTotalPosts(userId);
        userService.updateLastActiveTime(userId);
        // Song：说明
        userService.updatePreferredSections(userId, String.valueOf(createPostRequest.getSectionId()));
        userService.addContribution(userId, 5);

        // Song：发帖经验 +5
        levelService.addExperience(userId, 5, "发帖");

        // Song：6.更新活跃地点
        if (createPostRequest.getLocationName() != null && !createPostRequest.getLocationName().isEmpty()) {
            userService.updateActiveRegion(userId, createPostRequest.getLocationName());
        }

        invalidatePostFeedCache(null, sysPost.getSectionId());
    }

    /**
     * Song：处理帖子标签：自动创建标签并增加热度
     *
     * Song：说明
     */
    private void processPostTags(String tagsString) {
        if (!StringUtils.hasText(tagsString)) {
            return;
        }

        // Song：解析标签字符串（支持逗号、空格或#分隔）
        String[] tagNames = tagsString.split("[,，\\s#]+");

        for (String tagName : tagNames) {
            String trimmedTag = tagName.trim();
            if (StringUtils.hasText(trimmedTag) && !trimmedTag.isEmpty()) {
                try {
                    // Song：获取或创建标签
                    Tag tag = tagService.getOrCreateTag(trimmedTag);
                    // Song：增加标签热度
                    tagService.increaseHeat(tag.getId());
                } catch (Exception e) {
                    log.error("处理标签 " + trimmedTag + " 失败", e);
                }
            }
        }
    }

    @Override
    public Map<String, Object> extractTagsAndSummary(TagsExtractReq extractTagsRequest) {
        return aiPostAnalysisService.extractTagsAndSummary(extractTagsRequest);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String regenerateSummary(String postId, String userId) {
        Post post = getById(postId);
        if (post == null) {
            throw new RuntimeException("帖子不存在");
        }

        boolean isOwner = post.getUserId() != null && post.getUserId().equals(userId);
        boolean isAdminOrMod = com.campus.trend.campus_pulse.utils.PermissionUtils.isUserAdminOrModerator(userId);
        if (!isOwner && !isAdminOrMod) {
            throw new RuntimeException("无权重新生成该帖子摘要");
        }

        TagsExtractReq req = new TagsExtractReq();
        req.setContent(post.getContent() != null ? post.getContent() : "");
        req.setTagSize(3);
        req.setSummarySize(180);

        String summary;
        try {
            Map<String, Object> analysis = aiPostAnalysisService.extractTagsAndSummary(req);
            Object aiSummary = analysis.get("summary");
            summary = aiSummary != null ? String.valueOf(aiSummary).trim() : "";
        } catch (Exception e) {
            log.warn("AI 摘要生成失败，使用本地兜底: postId={}, err={}", postId, e.getMessage());
            summary = "";
        }

        if (!StringUtils.hasText(summary)) {
            summary = buildSummary(post.getContent());
        }

        post.setSummary(summary);
        post.setUpdateTime(LocalDateTime.now());
        updateById(post);
        invalidatePostFeedCache(post.getSectionId(), post.getSectionId());

        return summary;
    }

    @Override
    public void likePost(String postId, String userId) {
        Long sectionId = getSectionIdByPostId(postId);
        // Song：说明
        // Song：该方法会自动处理点赞状态切换并更新点赞数
        postLikeService.toggleLike(postId, userId);
        invalidatePostFeedCache(sectionId, sectionId);
    }

    @Override
    public void collectPost(String postId, String userId) {
        Long sectionId = getSectionIdByPostId(postId);
        // Song：说明
        // Song：该方法会自动处理收藏状态切换并更新收藏数
        postCollectService.toggleCollect(postId, userId);
        invalidatePostFeedCache(sectionId, sectionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePost(com.campus.trend.campus_pulse.dto.request.PostUpdateReq request, String userId) {
        Post post = getById(request.getPostId());
        if (post == null) {
            throw new RuntimeException("帖子不存在");
        }
        Long oldSectionId = post.getSectionId();

        boolean isAdmin = com.campus.trend.campus_pulse.utils.PermissionUtils.isUserAdmin(userId);

        if (!post.getUserId().equals(userId) && !isAdmin) {
            throw new RuntimeException("无权修改该帖子");
        }

        boolean contentChanged = false;
        String title = request.getTitle();
        String content = request.getContent();

        // Song：1. 内容安全检查
        if (StringUtils.hasText(title)) {
            if (contentSecurityService.containsSensitiveWords(title)) {
                title = contentSecurityService.filterSensitiveWords(title);
            }
            post.setTitle(title);
            contentChanged = true;
        }

        if (StringUtils.hasText(content)) {
            if (contentSecurityService.containsSensitiveWords(content)) {
                content = contentSecurityService.filterSensitiveWords(content);
            }
            post.setContent(content);
            contentChanged = true;
        }

        // Song：2. 如果内容变动，重新计算情感得分
        if (contentChanged) {
            double sentimentScore = sentimentAnalysisService
                    .analyzeSentiment(post.getTitle() + " " + post.getContent());
            post.setSentimentScore(BigDecimal.valueOf(sentimentScore));
            post.setSummary(buildSummary(post.getContent()));

            // Song：重新审核状态
            post.setAuditStatus("PENDING");
        }

        // Song：3. 其他字段
        if (request.getIsAnonymous() != null) {
            post.setIsAnonymous(request.getIsAnonymous());
        }
        if (StringUtils.hasText(request.getLocationName())) {
            post.setLocationName(request.getLocationName());
        }
        if (request.getSectionId() != null) {
            // Song：验证板块是否存在
            Section section = sectionService.getSectionById(request.getSectionId());
            if (section == null) {
                throw new RuntimeException("板块不存在: " + request.getSectionId());
            }
            post.setSectionId(request.getSectionId());
        }
        if (StringUtils.hasText(request.getCoverImage())) {
            post.setCoverImage(request.getCoverImage());
        }
        if (StringUtils.hasText(request.getTags())) {
            post.setTags(request.getTags());
            processPostTags(request.getTags());
        }

        post.setUpdateTime(LocalDateTime.now());

        // Song：图片处理 (简单实现: 如果传了就覆盖)
        if (StringUtils.hasText(request.getImages())) {
            try {
                List<String> images = objectMapper.readValue(request.getImages(), new TypeReference<List<String>>() {
                });
                post.setImages(images);
            } catch (Exception e) {
                // Song：忽略异常或记录日志
            }
        }

        this.updateById(post);
        invalidatePostFeedCache(oldSectionId, post.getSectionId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePost(String postId, String userId) {
        Post post = getById(postId);
        if (post == null) {
            throw new RuntimeException("帖子不存在");
        }
        Long oldSectionId = post.getSectionId();

        boolean isAdmin = com.campus.trend.campus_pulse.utils.PermissionUtils.isUserAdmin(userId);

        // Song：校验权限 (作者或管理员)
        if (!post.getUserId().equals(userId) && !isAdmin) {
            throw new RuntimeException("无权删除该帖子");
        }

        // Song：物理删除
        this.removeById(postId);
        invalidatePostFeedCache(oldSectionId, null);

        // Song：同时可以考虑扣除用户贡献值等，或者清理浏览记录
        // Song：说明
    }

    @Override
    public void featurePost(String postId, String userId) {
        Post post = getById(postId);
        if (post == null) {
            throw new RuntimeException("帖子不存在");
        }

        boolean isAdminOrMod = com.campus.trend.campus_pulse.utils.PermissionUtils.isUserAdminOrModerator(userId);

        if (!isAdminOrMod) {
            throw new RuntimeException("无权操作精华");
        }

        post.setIsFeatured(post.getIsFeatured() != null && post.getIsFeatured() == 1 ? 0 : 1);
        post.setUpdateTime(java.time.LocalDateTime.now());
        this.updateById(post);
        invalidatePostFeedCache(post.getSectionId(), post.getSectionId());
    }

    @Override
    public IPage<Post> searchAllList(PostSearchReq postSearchRequest) {

        int page = postSearchRequest.getPage() != null ? postSearchRequest.getPage() : 1;
        int pageSize = clampPageSize(postSearchRequest.getPageSize());
        Page<Post> pagePram = new Page<>(page, pageSize);
        if (postSearchRequest.getCursor() != null) {
            // Song：说明
            pagePram.setCurrent(1);
        }

        LambdaQueryWrapper<Post> wrapper = Wrappers.lambdaQuery();
        wrapper.select(
                Post::getId,
                Post::getUserId,
                Post::getSectionId,
                Post::getTitle,
                Post::getSummary,
                Post::getCoverImage,
                Post::getTags,
                Post::getIsAnonymous,
                Post::getStatus,
                Post::getAuditStatus,
                Post::getIsPinned,
                Post::getGlobalPin,
                Post::getCategoryPin,
                Post::getPinOrder,
                Post::getPinExpireAt,
                Post::getIsFeatured,
                Post::getViewCount,
                Post::getLikeCount,
                Post::getCollectCount,
                Post::getCommentCount,
                Post::getHeatScore,
                Post::getSentimentScore,
                Post::getCreateTime,
                Post::getUpdateTime,
                Post::getLastReplyAt,
                Post::getLastActivityAt);

        // Song：说明
        wrapper.eq(postSearchRequest.getSectionId() != null, Post::getSectionId,
                postSearchRequest.getSectionId());

        // Song：如果需要筛选精华帖
        if (postSearchRequest.getIsFeatured() != null && postSearchRequest.getIsFeatured()) {
            wrapper.eq(Post::getIsFeatured, 1);
        }

        // Song：时间范围过滤
        if (org.springframework.util.StringUtils.hasText(postSearchRequest.getTimeRange())) {
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            java.time.LocalDateTime start = null;
            switch (postSearchRequest.getTimeRange().toUpperCase()) {
                case "TODAY":
                    start = now.toLocalDate().atStartOfDay();
                    break;
                case "WEEK":
                    start = now.minusDays(7);
                    break;
                case "MONTH":
                    start = now.minusDays(30);
                    break;
            }
            if (start != null) {
                wrapper.ge(Post::getCreateTime, start);
            }
        }

        // Song：说明
        wrapper.eq(postSearchRequest.getUserId() != null, Post::getUserId, postSearchRequest.getUserId());
        // Song：说明
        wrapper.eq(postSearchRequest.getStatus() != null, Post::getStatus, postSearchRequest.getStatus());
        if (Boolean.TRUE.equals(postSearchRequest.getPinnedOnly())) {
            wrapper.and(w -> w.eq(Post::getGlobalPin, 1).or().eq(Post::getCategoryPin, 1));
        }

        // Song：说明
        String normalizedKeyword = normalizeKeyword(postSearchRequest.getKeyword());
        List<String> keywordTerms = splitKeywordTerms(normalizedKeyword);
        if (!keywordTerms.isEmpty()) {
            wrapper.and(w -> {
                for (String term : keywordTerms) {
                    w.and(inner -> inner.like(Post::getTitle, term)
                            .or()
                            .like(Post::getContent, term));
                }
            });
        }

        // Song：说明
        if (StringUtils.hasText(postSearchRequest.getTag())) {
            wrapper.apply("FIND_IN_SET({0}, tags)", postSearchRequest.getTag());
        }

        // Song：说明
        if (StringUtils.hasText(postSearchRequest.getLikedBy())) {
            List<String> likedIds = postLikeService.lambdaQuery()
                    .eq(PostLike::getUserId, postSearchRequest.getLikedBy())
                    .list()
                    .stream()
                    .map(PostLike::getPostId)
                    .distinct()
                    .toList();
            if (likedIds.isEmpty()) {
                Page<Post> empty = new Page<>(page, pageSize);
                empty.setTotal(0);
                empty.setRecords(new ArrayList<>());
                return empty;
            }
            wrapper.in(Post::getId, likedIds);
        }

        // Song：说明
        if (StringUtils.hasText(postSearchRequest.getCollectedBy())) {
            List<String> collectedIds = postCollectService.lambdaQuery()
                    .eq(PostCollect::getUserId, postSearchRequest.getCollectedBy())
                    .list()
                    .stream()
                    .map(PostCollect::getPostId)
                    .distinct()
                    .toList();
            if (collectedIds.isEmpty()) {
                Page<Post> empty = new Page<>(page, pageSize);
                empty.setTotal(0);
                empty.setRecords(new ArrayList<>());
                return empty;
            }
            wrapper.in(Post::getId, collectedIds);
        }

        // Song：置顶帖子优先排序：全局置顶 > 板块置顶 > 普通帖子
        // Song：如果是全局最新排序（首页的 "最新发布"），暂取消强制置顶；但在指定版块内仍然保留版块置顶
        String orderBy = postSearchRequest.getOrderBy();
        boolean shouldApplyPin = true;

        if ("new".equalsIgnoreCase(orderBy) && postSearchRequest.getSectionId() == null) {
            shouldApplyPin = false; // Song："在最新话题中不用刻意置顶... 但是指定的板块要置顶"
        }

        if (shouldApplyPin) {
            wrapper.orderByDesc(Post::getGlobalPin);
            wrapper.orderByDesc(Post::getCategoryPin);
            wrapper.orderByAsc(Post::getPinOrder);
        }

        // Song：根据排序方式排序
        if ("hot".equalsIgnoreCase(orderBy)) {
            wrapper.orderByDesc(Post::getHeatScore);
            wrapper.orderByDesc(Post::getId);
        } else if ("relevance".equalsIgnoreCase(orderBy) && !keywordTerms.isEmpty()) {
            wrapper.orderByDesc(Post::getHeatScore);
            wrapper.orderByDesc(Post::getLikeCount);
            wrapper.orderByDesc(Post::getCommentCount);
            wrapper.orderByDesc(Post::getCreateTime);
        } else {
            if (postSearchRequest.getCursor() != null) {
                LocalDateTime cursor = postSearchRequest.getCursor();
                String cursorId = postSearchRequest.getCursorId();
                wrapper.and(w -> {
                    w.lt(Post::getLastActivityAt, cursor);
                    if (StringUtils.hasText(cursorId)) {
                        w.or(w2 -> w2.eq(Post::getLastActivityAt, cursor).lt(Post::getId, cursorId));
                    }
                });
            }
            // Song：默认按最后活跃时间排序（新回复的帖子会浮到前面）
            wrapper.orderByDesc(Post::getLastActivityAt);
            wrapper.orderByDesc(Post::getId);
        }

        return this.page(pagePram, wrapper);
    }

    // Song：=================== 新增：带作者信息的帖子查询 ===================

    @Override
    public PostResp getPostWithAuthor(String postId) {
        Post post = searchByPostId(postId);
        if (post == null) {
            return null;
        }
        return convertToPostResp(post);
    }

    @Override
    public IPage<PostResp> searchPostsWithAuthor(PostSearchReq postSearchRequest) {
        IPage<PostResp> cachedPage = readPostFeedCache(postSearchRequest);
        if (cachedPage != null) {
            return cachedPage;
        }

        // Song：1. 先查询帖子分页数据
        IPage<Post> postPage = searchAllList(postSearchRequest);
        List<Post> posts = postPage.getRecords();

        if (posts.isEmpty()) {
            Page<PostResp> emptyPage = new Page<>(postPage.getCurrent(), postPage.getSize(), postPage.getTotal());
            emptyPage.setRecords(new ArrayList<>());
            writePostFeedCache(postSearchRequest, emptyPage);
            return emptyPage;
        }

        // Song：说明
        List<String> userIds = posts.stream().map(Post::getUserId).distinct().toList();
        List<Long> sectionIds = posts.stream()
                .map(Post::getSectionId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        // Song：3. 批量查询用户
        Map<String, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<User> users = userService.listByIds(userIds);
            for (User user : users) {
                userMap.put(user.getId(), user);
            }
        }
        Map<Long, Section> sectionMap = new HashMap<>();
        if (!sectionIds.isEmpty()) {
            List<Section> sections = sectionMapper.selectBatchIds(sectionIds);
            for (Section section : sections) {
                sectionMap.put(section.getId(), section);
            }
        }

        // Song：说明
        List<PostResp> responseList = new ArrayList<>();
        for (Post post : posts) {
            responseList.add(convertToPostResp(post, userMap, sectionMap));
        }

        // Song：5. 构造结果页
        Page<PostResp> responsePage = new Page<>(
                postPage.getCurrent(),
                postPage.getSize(),
                postPage.getTotal());
        responsePage.setRecords(responseList);

        writePostFeedCache(postSearchRequest, responsePage);
        return responsePage;
    }

    /**
     * Song：说明
     * Song：说明
     */
    private PostResp convertToPostResp(Post post, Map<String, User> userMap, Map<Long, Section> sectionMap) {
        PostResp response = new PostResp();

        // Song：复制基本字段
        response.setId(post.getId());
        response.setUserId(post.getUserId());
        response.setSectionId(post.getSectionId());
        response.setTitle(post.getTitle());
        response.setContent(post.getContent());
        response.setCoverImage(post.getCoverImage());
        response.setImages(post.getImages());
        response.setTags(post.getTags());
        response.setSummary(post.getSummary());
        response.setIsAnonymous(post.getIsAnonymous());
        response.setIsPinned(post.getIsPinned());
        response.setLocationName(post.getLocationName());
        response.setStatus(post.getStatus());
        response.setAuditStatus(post.getAuditStatus());
        response.setViewCount(post.getViewCount());
        response.setLikeCount(post.getLikeCount());
        response.setCollectCount(post.getCollectCount());
        response.setCommentCount(post.getCommentCount());
        response.setHeatScore(post.getHeatScore());
        response.setCreateTime(post.getCreateTime());
        response.setUpdateTime(post.getUpdateTime());
        response.setLastReplyAt(post.getLastReplyAt());
        response.setLastActivityAt(post.getLastActivityAt());
        response.setIsLiked(post.getIsLiked());
        response.setIsCollected(post.getIsCollected());
        response.setSentimentScore(post.getSentimentScore());

        // Song：填充置顶相关字段
        response.setGlobalPin(post.getGlobalPin());
        response.setCategoryPin(post.getCategoryPin());
        response.setPinOrder(post.getPinOrder());
        response.setPinExpireAt(post.getPinExpireAt());
        response.setIsPinned(post.getIsPinned()); // Song：兼容旧字段

        // Song：===== 填充作者信息 =====
        if (post.getIsAnonymous() != null && post.getIsAnonymous() == 1) {
            // Song：匿名帖子
            response.setAuthorName("匿名同学");
            response.setAuthorAvatar(null);
        } else {
            // Song：非匿名帖子
            User author = userMap != null ? userMap.get(post.getUserId()) : null;
            // Song：说明
            if (userMap == null) {
                try {
                    author = userService.getById(post.getUserId());
                } catch (Exception ignored) {
                }
            }

            if (author != null) {
                response.setAuthorName(author.getNickname() != null ? author.getNickname()
                        : "用户" + post.getUserId().substring(0, 6));
                response.setAuthorAvatar(author.getAvatar());

                String role = author.getRole() != null ? author.getRole() : "ROLE_USER";
                response.setAuthorRoles(java.util.Collections.singletonList(role));
            } else {
                response.setAuthorName("未知用户");
                response.setAuthorAvatar(null);
                response.setAuthorRoles(java.util.Collections.singletonList("ROLE_USER")); // Song：默认普通用户
            }
        }

        // Song：===== 填充板块名称 =====
        if (post.getSectionId() != null) {
            try {
                Section section = sectionMap != null ? sectionMap.get(post.getSectionId()) : null;
                if (section == null && sectionMap == null) {
                    section = sectionService.getSectionById(post.getSectionId());
                }
                if (section != null) {
                    response.setSectionName(section.getName());
                }
            } catch (Exception ignored) {
            }
        }

        // Song：===== 填充情感标签 =====
        if (post.getSentimentScore() != null) {
            double score = post.getSentimentScore().doubleValue();
            if (score >= 0.6) {
                response.setSentimentLabel("positive");
            } else if (score <= 0.4) {
                response.setSentimentLabel("negative");
            } else {
                response.setSentimentLabel("neutral");
            }
        }

        // Song：===== 填充趋势等级 =====
        Double heatScore = post.getHeatScore();
        if (heatScore != null) {
            if (heatScore >= 100) {
                response.setTrendLevel("hot");
            } else if (heatScore >= 50) {
                response.setTrendLevel("trending");
            } else {
                response.setTrendLevel("normal");
            }
        } else {
            response.setTrendLevel("normal");
        }

        // Song：===== 填充 智能 摘要 =====
        fillSummary(response, post);

        return response;
    }

    private PostResp convertToPostResp(Post post) {
        return convertToPostResp(post, null, null);
    }

    private void fillSummary(PostResp response, Post post) {
        if (StringUtils.hasText(response.getSummary())) {
            response.setSummary(truncate(response.getSummary(), SUMMARY_MAX_LEN));
            return;
        }
        if (StringUtils.hasText(post.getContent())) {
            response.setSummary(buildSummary(post.getContent()));
            return;
        }
        response.setSummary(truncate(post.getTitle(), SUMMARY_MAX_LEN));
    }

    /**
     * Song：说明
     */
    private String stripMarkdown(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }

        String text = content;

        // Song：说明
        text = text.replaceAll("<[^>]*>", "");

        // Song：移除代码块标记 ```
        text = text.replaceAll("```[\\s\\S]*?```", "");
        text = text.replaceAll("```.*", "");

        // Song：移除行内代码 `
        text = text.replaceAll("`[^`]+`", "");
        text = text.replaceAll("`", "");

        // Song：移除标题符号 # ## ### 等
        text = text.replaceAll("^#{1,6}\\s+", "");
        text = text.replaceAll("\\n#{1,6}\\s+", "\n");

        // Song：移除加粗 ** 和 __
        text = text.replaceAll("\\*\\*([^*]+)\\*\\*", "$1");
        text = text.replaceAll("__([^_]+)__", "$1");

        // Song：移除斜体 * 和 _
        text = text.replaceAll("\\*([^*]+)\\*", "$1");
        text = text.replaceAll("_([^_]+)_", "$1");

        // Song：移除删除线 ~~
        text = text.replaceAll("~~([^~]+)~~", "$1");

        // Song：移除分割线 --- 或 ***
        text = text.replaceAll("^[-*]{3,}$", "");
        text = text.replaceAll("\\n[-*]{3,}\\n", "\n");

        // Song：移除引用符号 >
        text = text.replaceAll("^>\\s+", "");
        text = text.replaceAll("\\n>\\s+", "\n");

        // Song：说明
        text = text.replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1");

        // Song：说明
        text = text.replaceAll("!\\[([^\\]]*)\\]\\([^)]+\\)", "");

        // Song：移除列表符号 - * +
        text = text.replaceAll("^[\\-*+]\\s+", "");
        text = text.replaceAll("\\n[\\-*+]\\s+", "\n");

        // Song：移除有序列表 1. 2. 等
        text = text.replaceAll("^\\d+\\.\\s+", "");
        text = text.replaceAll("\\n\\d+\\.\\s+", "\n");

        // Song：说明
        text = text.replaceAll("&nbsp;", " ");
        text = text.replaceAll("&lt;", "<");
        text = text.replaceAll("&gt;", ">");
        text = text.replaceAll("&amp;", "&");
        text = text.replaceAll("&quot;", "\"");

        // Song：清理多余空白
        text = text.replaceAll("\\s+", " ");
        text = text.trim();

        return text;
    }

    private IPage<PostResp> readPostFeedCache(PostSearchReq request) {
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

    private void writePostFeedCache(PostSearchReq request, IPage<PostResp> page) {
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
            stringRedisTemplate.opsForValue().set(cacheKey, json, POST_FEED_CACHE_TTL);
        } catch (Exception e) {
            log.debug("写入帖子流缓存失败: {}", e.getMessage());
        }
    }

    private boolean shouldUsePostFeedCache(PostSearchReq request) {
        if (request == null) {
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
            keyParts.put("pageSize", clampPageSize(request.getPageSize()));
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
            keyParts.put("cursor", request.getCursor() != null ? request.getCursor().toString() : "");
            keyParts.put("cursorId", StringUtils.hasText(request.getCursorId()) ? request.getCursorId() : "");
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

    private long getCacheVersion(String versionKey) {
        try {
            String value = stringRedisTemplate.opsForValue().get(versionKey);
            return StringUtils.hasText(value) ? Long.parseLong(value) : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private void invalidatePostFeedCache(Long oldSectionId, Long newSectionId) {
        bumpCacheVersion(POST_FEED_CACHE_GLOBAL_VERSION_KEY);
        if (oldSectionId != null) {
            bumpCacheVersion(getSectionVersionKey(oldSectionId));
        }
        if (newSectionId != null && !newSectionId.equals(oldSectionId)) {
            bumpCacheVersion(getSectionVersionKey(newSectionId));
        }
    }

    private void bumpCacheVersion(String versionKey) {
        try {
            stringRedisTemplate.opsForValue().increment(versionKey);
        } catch (Exception e) {
            log.debug("递增缓存版本失败: key={}, err={}", versionKey, e.getMessage());
        }
    }

    private String getSectionVersionKey(Long sectionId) {
        return POST_FEED_CACHE_SECTION_VERSION_KEY_PREFIX + sectionId;
    }

    private Long getSectionIdByPostId(String postId) {
        Post post = getById(postId);
        return post != null ? post.getSectionId() : null;
    }

    private int clampPageSize(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(requested, MAX_PAGE_SIZE);
    }

    private String normalizeKeyword(String rawKeyword) {
        if (!StringUtils.hasText(rawKeyword)) {
            return "";
        }
        String normalized = rawKeyword.trim().replaceAll("\\s+", " ");
        int maxLen = 64;
        if (normalized.length() > maxLen) {
            normalized = normalized.substring(0, maxLen);
        }
        return normalized;
    }

    private List<String> splitKeywordTerms(String normalizedKeyword) {
        if (!StringUtils.hasText(normalizedKeyword)) {
            return java.util.Collections.emptyList();
        }
        return Arrays.stream(normalizedKeyword.split("\\s+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .limit(5)
                .toList();
    }

    private String buildSummary(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        return truncate(stripMarkdown(content), SUMMARY_MAX_LEN);
    }

    private String truncate(String text, int maxLen) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String cleaned = text.trim();
        if (cleaned.length() <= maxLen) {
            return cleaned;
        }
        return cleaned.substring(0, maxLen) + "...";
    }

    private static class PostFeedCachePayload {
        public long current;
        public long size;
        public long total;
        public List<PostResp> records;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pinPost(String postId, String userId) {
        // Song：校验权限 (只有管理员能操作置顶)
        if (!com.campus.trend.campus_pulse.utils.PermissionUtils.isUserAdmin(userId)) {
            throw new RuntimeException("无权进行此操作");
        }

        Post post = getById(postId);
        if (post == null) {
            throw new RuntimeException("帖子不存在");
        }

        Integer currentPinned = post.getIsPinned() != null ? post.getIsPinned() : 0;
        Integer newPinned = currentPinned == 1 ? 0 : 1;
        post.setIsPinned(newPinned);
        post.setGlobalPin(newPinned); // Song：同步到新字段
        post.setUpdateTime(LocalDateTime.now());
        this.updateById(post);
        invalidatePostFeedCache(post.getSectionId(), post.getSectionId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setGlobalPin(String postId, String userId, Integer pinOrder, String expireAt) {
        Post post = getById(postId);
        if (post == null) {
            throw new RuntimeException("帖子不存在");
        }

        boolean isAdmin = com.campus.trend.campus_pulse.utils.PermissionUtils.isUserAdmin(userId);
        if (!isAdmin) {
            throw new RuntimeException("无权操作全局置顶");
        }

        // Song：切换全局置顶状态
        Integer currentGlobalPin = post.getGlobalPin() != null ? post.getGlobalPin() : 0;
        Integer newGlobalPin = currentGlobalPin == 1 ? 0 : 1;

        post.setGlobalPin(newGlobalPin);
        post.setIsPinned(newGlobalPin); // Song：保持兼容

        if (newGlobalPin == 1) {
            // Song：设置置顶
            post.setPinOrder(pinOrder != null ? pinOrder : 0);
            if (expireAt != null && !expireAt.isEmpty()) {
                try {
                    post.setPinExpireAt(java.time.LocalDateTime.parse(expireAt));
                } catch (Exception e) {
                    log.warn("解析置顶过期时间失败: {}", expireAt);
                }
            }
        } else {
            // Song：取消置顶
            post.setPinOrder(0);
            post.setPinExpireAt(null);
        }

        post.setUpdateTime(java.time.LocalDateTime.now());
        this.updateById(post);
        invalidatePostFeedCache(post.getSectionId(), post.getSectionId());

        // Song：推送置顶状态更新事件
        try {
            postEventService.pushPinUpdated(post);
        } catch (Exception e) {
            log.warn("推送置顶状态更新事件失败", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setCategoryPin(String postId, String userId, Integer pinOrder, String expireAt) {
        Post post = getById(postId);
        if (post == null) {
            throw new RuntimeException("帖子不存在");
        }

        boolean isAdminOrMod = com.campus.trend.campus_pulse.utils.PermissionUtils.isUserAdminOrModerator(userId);
        if (!isAdminOrMod) {
            throw new RuntimeException("无权操作板块置顶");
        }

        // Song：切换板块置顶状态
        Integer currentCategoryPin = post.getCategoryPin() != null ? post.getCategoryPin() : 0;
        Integer newCategoryPin = currentCategoryPin == 1 ? 0 : 1;

        post.setCategoryPin(newCategoryPin);

        if (newCategoryPin == 1) {
            // Song：设置置顶
            post.setPinOrder(pinOrder != null ? pinOrder : 0);
            if (expireAt != null && !expireAt.isEmpty()) {
                try {
                    post.setPinExpireAt(java.time.LocalDateTime.parse(expireAt));
                } catch (Exception e) {
                    log.warn("解析置顶过期时间失败: {}", expireAt);
                }
            }
        } else {
            // Song：取消置顶
            if (post.getGlobalPin() == null || post.getGlobalPin() == 0) {
                post.setPinOrder(0);
                post.setPinExpireAt(null);
            }
        }

        post.setUpdateTime(java.time.LocalDateTime.now());
        this.updateById(post);
        invalidatePostFeedCache(post.getSectionId(), post.getSectionId());

        // Song：推送置顶状态更新事件
        try {
            postEventService.pushPinUpdated(post);
        } catch (Exception e) {
            log.warn("推送置顶状态更新事件失败", e);
        }
    }

    @Override
    public java.util.List<com.campus.trend.campus_pulse.dto.response.PostResp> getPinnedPosts(Long sectionId) {
        LambdaQueryWrapper<Post> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(Post::getStatus, 1);

        if (sectionId != null) {
            // Song：查询指定板块的置顶（全局置顶 + 板块置顶）
            wrapper.and(w -> w.eq(Post::getGlobalPin, 1)
                    .or()
                    .and(w2 -> w2.eq(Post::getCategoryPin, 1).eq(Post::getSectionId, sectionId)));
        } else {
            // Song：查询所有全局置顶
            wrapper.eq(Post::getGlobalPin, 1);
        }

        // Song：检查过期时间
        wrapper.and(w -> w.isNull(Post::getPinExpireAt)
                .or()
                .gt(Post::getPinExpireAt, java.time.LocalDateTime.now()));

        // Song：按置顶排序
        wrapper.orderByAsc(Post::getPinOrder).orderByDesc(Post::getCreateTime);

        List<Post> posts = this.list(wrapper);

        // Song：说明
        return posts.stream()
                .map(this::convertToPostResp)
                .collect(java.util.stream.Collectors.toList());
    }

}
