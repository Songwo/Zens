package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.dto.request.PostCreateReq;
import com.campus.trend.campus_pulse.dto.request.PostDraftReq;
import com.campus.trend.campus_pulse.dto.request.TagsExtractReq;
import com.campus.trend.campus_pulse.dto.request.PostSearchReq;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.PostCollect;
import com.campus.trend.campus_pulse.entity.PostLike;
import com.campus.trend.campus_pulse.entity.PostVersionHistory;
import com.campus.trend.campus_pulse.entity.Tag;
import com.campus.trend.campus_pulse.entity.AnswerAdoption;
import com.campus.trend.campus_pulse.mapper.AnswerAdoptionMapper;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.mapper.PostVersionHistoryMapper;
import com.campus.trend.campus_pulse.mapper.SectionMapper;
import com.campus.trend.campus_pulse.service.*;

import java.math.BigDecimal;

import com.campus.trend.campus_pulse.entity.UserTagRelation;
import com.campus.trend.campus_pulse.dto.media.MediaObject;
import com.campus.trend.campus_pulse.dto.response.PostResp;
import com.campus.trend.campus_pulse.dto.response.PostVersionHistoryResp;
import com.campus.trend.campus_pulse.entity.PostMedia;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.entity.Section;
import com.campus.trend.campus_pulse.utils.IdUtils;
import com.campus.trend.campus_pulse.utils.TimeRangeUtils;
import com.campus.trend.campus_pulse.service.post.PostCacheManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.campus.trend.campus_pulse.service.post.PostSupport.SUMMARY_MAX_LEN;
import static com.campus.trend.campus_pulse.service.post.PostSupport.buildSummary;
import static com.campus.trend.campus_pulse.service.post.PostSupport.clampPageSize;
import static com.campus.trend.campus_pulse.service.post.PostSupport.extractAccessUrls;
import static com.campus.trend.campus_pulse.service.post.PostSupport.firstAccessUrl;
import static com.campus.trend.campus_pulse.service.post.PostSupport.mediaRowToDto;
import static com.campus.trend.campus_pulse.service.post.PostSupport.normalizeTags;
import static com.campus.trend.campus_pulse.service.post.PostSupport.truncate;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostServiceImpl extends ServiceImpl<PostMapper, Post> implements PostService {

    private static final int POST_STATUS_DRAFT = 0;
    private static final int POST_STATUS_PUBLISHED = 1;
    private static final String AUDIT_STATUS_DRAFT = "DRAFT";
    private static final String AUDIT_STATUS_PENDING = "PENDING";
    private static final String AUDIT_STATUS_APPROVED = "APPROVED";
    private static final String AUDIT_STATUS_REJECTED = "REJECTED";
    private static final String AUDIT_STATUS_DELETED = "DELETED";
    private static final String POST_TYPE_NORMAL = "NORMAL";
    private static final String POST_TYPE_LOTTERY = "LOTTERY";
    private static final int RESTORE_GRACE_DAYS = 7;

    private final PostLikeService postLikeService;
    private final PostCollectService postCollectService;
    private final TagService tagService;
    private final ContentSecurityService contentSecurityService;
    private final SentimentAnalysisService sentimentAnalysisService;
    private final UserService userService;
    private final SectionService sectionService;
    private final SectionModeratorService sectionModeratorService;
    private final SectionMapper sectionMapper;
    private final PostEventService postEventService;
    private final NotificationService notificationService;
    private final UserTagRelationService userTagRelationService;
    private final com.campus.trend.campus_pulse.service.LevelService levelService;
    private final com.campus.trend.campus_pulse.service.AiPostAnalysisService aiPostAnalysisService;
    private final ObjectMapper objectMapper;
    private final PostVersionHistoryMapper postVersionHistoryMapper;
    private final AsyncTaskService asyncTaskService;
    private final PostMediaService postMediaService;
    private final AnswerAdoptionMapper answerAdoptionMapper;
    private final PollService pollService;
    private final PostSubscriptionService postSubscriptionService;
    private final PostCacheManager postCacheManager;
    private final com.campus.trend.campus_pulse.service.TrustLevelService trustLevelService;
    private final com.campus.trend.campus_pulse.service.SearchService searchService;
    private final RBloomFilter<String> postIdBloomFilter;
    private final BatchUserService batchUserService;

    @Override
    public Post searchByPostId(String postId) {
        if (!StringUtils.hasText(postId)) {
            return null;
        }

        boolean possibleExists = true;
        try {
            possibleExists = postIdBloomFilter.contains(postId);
        } catch (Exception e) {
            log.debug("查询帖子ID布隆过滤器失败，回退数据库查询: postId={}, err={}", postId, e.getMessage());
        }

        Post post = getById(postId);
        if (post != null) {
            if (!possibleExists) {
                addPostIdToBloomFilter(postId);
            }
            hydratePostInteractionState(post);
        } else if (!possibleExists) {
            log.debug("布隆过滤器未命中且数据库未找到帖子: postId={}", postId);
        }
        return post;
    }

    private void hydratePostInteractionState(Post post) {
        if (post == null || !StringUtils.hasText(post.getId())) {
            return;
        }
        try {
            // 尝试获取当前登录用户，若未登录会抛出异常，捕获后按游客处理。
            com.campus.trend.campus_pulse.security.AuthUser authUser = com.campus.trend.campus_pulse.utils.SecurityUtils
                    .getAuthenticatedUser();
            String userId = authUser.getUser().getId();

            post.setIsLiked(postLikeService.isLike(post.getId(), userId));
            post.setIsCollected(postCollectService.isCollect(post.getId(), userId));
            log.debug("Post detail: postId={}, userId={}, isLiked={}, isCollected={}", post.getId(), userId,
                    post.getIsLiked(), post.getIsCollected());
        } catch (Exception e) {
            log.debug("SearchByPostId - User not authenticated or error: {}", e.getMessage());
            post.setIsLiked(false);
            post.setIsCollected(false);
        }
    }

    private void addPostIdToBloomFilter(String postId) {
        if (!StringUtils.hasText(postId)) {
            return;
        }
        try {
            postIdBloomFilter.add(postId);
        } catch (Exception e) {
            log.debug("添加帖子ID到布隆过滤器失败: postId={}, err={}", postId, e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createPost(PostCreateReq createPostRequest, String userId) {
        if (createPostRequest.getSectionId() != null) {
            Section section = sectionService.getSectionById(createPostRequest.getSectionId());
            if (section == null) {
                throw new BusinessException(ResultCode.SECTION_NOT_FOUND);
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

        // Song：3.构造帖子
        String postID = IdUtils.genId("POST");

        Post sysPost = new Post();
        sysPost.setId(postID);
        sysPost.setUserId(userId);
        sysPost.setSectionId(createPostRequest.getSectionId());
        sysPost.setTitle(title);
        sysPost.setContent(content);
        sysPost.setSummary(buildSummary(content));
        sysPost.setCoverImage(createPostRequest.getCoverImage());
        sysPost.setTags(normalizeTags(createPostRequest.getTags()));

        // Song：3.1 情感分析
        double sentimentScore = sentimentAnalysisService.analyzeSentiment(title + " " + content);
        sysPost.setSentimentScore(BigDecimal.valueOf(sentimentScore));

        sysPost.setIsAnonymous(createPostRequest.getIsAnonymous());
        applyPostTypeRules(
                sysPost,
                createPostRequest.getPostType(),
                createPostRequest.getCommentDeadline(),
                createPostRequest.getCommentOncePerUser());
        sysPost.setLocationName(createPostRequest.getLocationName());

        // Song：新版媒体字段优先：若前端传了 mediaList，回填 images/coverImage 以兼容老列表接口。
        List<MediaObject> mediaList = createPostRequest.getMediaList();
        if (mediaList != null && !mediaList.isEmpty()) {
            sysPost.setImages(extractAccessUrls(mediaList));
            if (!StringUtils.hasText(sysPost.getCoverImage())) {
                sysPost.setCoverImage(firstAccessUrl(mediaList));
            }
        } else if (StringUtils.hasText(createPostRequest.getImages())) {
            // Song：老字段兼容
            try {
                List<String> images = objectMapper.readValue(createPostRequest.getImages(),
                        new TypeReference<List<String>>() {
                        });
                sysPost.setImages(images);
            } catch (Exception e) {
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
        sysPost.setStatus(POST_STATUS_PUBLISHED); // Song：正常发布
        // Song：新用户冷启动审核 —— TL0 用户或注册<24h/发帖<3 的新用户帖子进入 PENDING 人工审核，
        //      借鉴 Discourse TL0 反 spam 机制，避免新注册账号灌水。TL1 及以上信任用户直接放行。
        sysPost.setAuditStatus(resolveCreatePostAuditStatus(userId));
        sysPost.setHeatScore(0.0); // Song：初始热度

        // Song：初始化置顶和活跃时间字段
        sysPost.setGlobalPin(0);
        sysPost.setCategoryPin(0);
        sysPost.setPinOrder(0);
        sysPost.setLastActivityAt(LocalDateTime.now());

        // Song：3.保存帖子
        this.save(sysPost);
        addPostIdToBloomFilter(sysPost.getId());

        // Song：3.1 覆盖式写入 sys_post_media
        if (mediaList != null) {
            postMediaService.saveForPost(sysPost.getId(), mediaList);
        }

        // Song：3.2 附带投票（可选）。同一事务内创建，投票建失败整帖回滚；投票数据独立读，不进帖子缓存。
        if (createPostRequest.getPoll() != null) {
            pollService.createForPost(sysPost.getId(), createPostRequest.getPoll(), userId);
        }

        // Song：3.3 主题追踪：发帖人自动订阅自己的帖（增强逻辑，失败不阻断发帖）
        try {
            postSubscriptionService.subscribe(userId, sysPost.getId(), "auto");
        } catch (Exception e) {
            log.warn("发帖自动订阅失败: postId={}, userId={}", sysPost.getId(), userId);
        }

        // 异步：推送新帖创建事件
        asyncTaskService.pushPostCreatedAsync(sysPost);

        // 异步：处理标签 + 通知标签关注者
        asyncTaskService.processPostTagsAsync(createPostRequest.getTags());
        asyncTaskService.notifyTagFollowersAsync(userId, sysPost.getId(), sysPost.getTitle(), createPostRequest.getTags());

        // 同步：更新帖子计数（影响用户主页展示，需即时）
        userService.incrementTotalPosts(userId);
        userService.updatePreferredSections(userId, String.valueOf(createPostRequest.getSectionId()));
        userService.addContribution(userId, 5);

        // 异步：经验值、活跃时间、地区（不阻塞主流程）
        asyncTaskService.addExperienceAsync(userId, 5, "发帖");
        asyncTaskService.updateLastActiveAsync(userId);
        if (createPostRequest.getLocationName() != null && !createPostRequest.getLocationName().isEmpty()) {
            asyncTaskService.updateActiveRegionAsync(userId, createPostRequest.getLocationName());
        }

        // Song：新用户帖子进入 PENDING 时异步通知作者，提升透明度
        if (AUDIT_STATUS_PENDING.equals(sysPost.getAuditStatus())) {
            asyncTaskService.sendSystemNotificationAsync(userId,
                    "你的帖子正在审核",
                    String.format("新用户帖子需管理员审核，「%s」通过后将公开显示。", sysPost.getTitle()),
                    sysPost.getId());
        }

        // Song：异步同步到 Meilisearch 搜索引擎（关闭时静默返回）
        asyncTaskService.syncPostToSearchAsync(sysPost.getId());

        postCacheManager.invalidatePostFeedCache(null, sysPost.getSectionId());
    }

    /**
     * Song：新用户冷启动审核判定 —— 决定发帖时的初始审核状态。
     * - TL1 及以上信任用户：直接 APPROVED
     * - TL0 新用户 + (注册<24h 或 已发帖<3)：PENDING 人工审核
     * - 其它：APPROVED（默认放行，举报后处理）
     */
    private String resolveCreatePostAuditStatus(String userId) {
        if (trustLevelService.isUserTrusted(userId, 1)) {
            return AUDIT_STATUS_APPROVED;
        }
        // Song：查用户的注册时间和发帖数判断是否新用户
        com.campus.trend.campus_pulse.entity.User author = userService.getById(userId);
        if (author == null) {
            return AUDIT_STATUS_APPROVED;
        }
        boolean justRegistered = author.getCreateTime() == null
                || author.getCreateTime().isAfter(LocalDateTime.now().minusHours(24));
        boolean fewPosts = author.getTotalPosts() == null || author.getTotalPosts() < 3;
        if (justRegistered || fewPosts) {
            return AUDIT_STATUS_PENDING;
        }
        return AUDIT_STATUS_APPROVED;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PostResp saveDraft(PostDraftReq draftRequest, String userId) {
        if (draftRequest == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "草稿内容不能为空");
        }

        boolean hasAnyContent = StringUtils.hasText(draftRequest.getTitle())
                || StringUtils.hasText(draftRequest.getContent())
                || draftRequest.getSectionId() != null
                || StringUtils.hasText(draftRequest.getTags())
                || StringUtils.hasText(draftRequest.getCoverImage());
        if (!hasAnyContent) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "草稿至少需要包含标题、正文、板块、标签或封面中的一项");
        }

        Post post;
        Long oldSectionId = null;
        if (StringUtils.hasText(draftRequest.getPostId())) {
            post = getById(draftRequest.getPostId());
            if (post == null) {
                throw new BusinessException(ResultCode.DRAFT_NOT_FOUND);
            }
            if (isDeletedPost(post)) {
                throw new BusinessException(ResultCode.NO_PERMISSION, "帖子已删除，请先在回收站恢复后再编辑");
            }
            if (!userId.equals(post.getUserId())) {
                throw new BusinessException(ResultCode.NO_PERMISSION, "无权修改该草稿");
            }
            oldSectionId = post.getSectionId();
        } else {
            post = new Post();
            post.setId(IdUtils.genId("POST"));
            post.setUserId(userId);
            post.setCreateTime(LocalDateTime.now());
            post.setViewCount(0);
            post.setLikeCount(0);
            post.setCollectCount(0);
            post.setCommentCount(0);
            post.setHeatScore(0.0);
            post.setGlobalPin(0);
            post.setCategoryPin(0);
            post.setPinOrder(0);
            post.setLastReplyAt(null);
        }

        fillPostDraftFields(post, draftRequest);
        validateDraftForSave(post);
        post.setStatus(POST_STATUS_DRAFT);
        if (!AUDIT_STATUS_REJECTED.equalsIgnoreCase(post.getAuditStatus())) {
            post.setAuditStatus(AUDIT_STATUS_DRAFT);
        }
        post.setLastActivityAt(LocalDateTime.now());
        post.setUpdateTime(LocalDateTime.now());

        if (StringUtils.hasText(draftRequest.getPostId())) {
            updateById(post);
        } else {
            save(post);
        }

        // Song：草稿也支持媒体覆盖保存
        if (draftRequest.getMediaList() != null) {
            postMediaService.saveForPost(post.getId(), draftRequest.getMediaList());
        }

        postCacheManager.invalidatePostFeedCache(oldSectionId, post.getSectionId());
        postCacheManager.bumpPostDetailCacheVersion(post.getId());
        return convertToPostResp(post);
    }

    /**
     * Song：处理帖子标签：自动创建标签并增加热度
     *
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

    private void notifyTagFollowers(String authorUserId, String postId, String postTitle, String tagsString) {
        if (!StringUtils.hasText(tagsString)) return;
        try {
            User author = userService.getById(authorUserId);
            String authorName = author != null && StringUtils.hasText(author.getNickname()) ? author.getNickname() : "匿名用户";
            String authorAvatar = author != null ? author.getAvatar() : null;

            String[] tagNames = tagsString.split("[,，\\s#]+");
            for (String rawTag : tagNames) {
                String tagName = rawTag.trim();
                if (!StringUtils.hasText(tagName)) continue;
                Tag tag = tagService.getOrCreateTag(tagName);
                if (tag == null) continue;

                List<UserTagRelation> followers = userTagRelationService.list(
                    com.baomidou.mybatisplus.core.toolkit.Wrappers.<UserTagRelation>lambdaQuery()
                        .eq(UserTagRelation::getTagId, tag.getId())
                );

                for (UserTagRelation rel : followers) {
                    if (rel.getUserId().equals(authorUserId)) continue;
                    try {
                        notificationService.createNotification(
                            rel.getUserId(),
                            authorUserId,
                            authorName,
                            authorAvatar,
                            "话题 #" + tagName + " 有新帖子",
                            authorName + " 在你关注的话题 #" + tagName + " 中发布了「" + postTitle + "」",
                            0,
                            postId
                        );
                    } catch (Exception e) {
                        log.warn("发送标签关注通知失败 userId={} tagName={}", rel.getUserId(), tagName, e);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("notifyTagFollowers 异常", e);
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
            throw new BusinessException(ResultCode.POST_NOT_FOUND);
        }

        if (!canManagePost(post, userId)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "无权重新生成该帖子摘要");
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
        postCacheManager.invalidatePostFeedCache(post.getSectionId(), post.getSectionId());
        postCacheManager.bumpPostDetailCacheVersion(postId);

        return summary;
    }

    @Override
    public void likePost(String postId, String userId) {
        Post post = getById(postId);
        if (post == null) {
            throw new BusinessException(ResultCode.POST_NOT_FOUND);
        }
        if (!isPubliclyVisible(post)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "该帖子当前不可点赞");
        }
        // Song：该方法会自动处理点赞状态切换并更新点赞数
        postLikeService.toggleLike(postId, userId);
        postCacheManager.invalidatePostFeedCache(post.getSectionId(), post.getSectionId());
        postCacheManager.bumpPostDetailCacheVersion(postId);
    }

    @Override
    public void collectPost(String postId, String userId) {
        Post post = getById(postId);
        if (post == null) {
            throw new BusinessException(ResultCode.POST_NOT_FOUND);
        }
        if (!isPubliclyVisible(post)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "该帖子当前不可收藏");
        }
        // Song：该方法会自动处理收藏状态切换并更新收藏数
        postCollectService.toggleCollect(postId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePost(com.campus.trend.campus_pulse.dto.request.PostUpdateReq request, String userId) {
        Post post = getById(request.getPostId());
        if (post == null) {
            throw new BusinessException(ResultCode.POST_NOT_FOUND);
        }
        if (isDeletedPost(post)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "帖子已删除，请先在回收站恢复后再编辑");
        }
        Long oldSectionId = post.getSectionId();

        if (!canManagePost(post, userId)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "无权修改该帖子");
        }

        Post before = snapshotPost(post);
        boolean contentChanged = false;
        boolean visibleChanged = hasVisiblePostChange(post, request);
        boolean publishAction = Boolean.TRUE.equals(request.getPublish()) || Integer.valueOf(POST_STATUS_PUBLISHED).equals(request.getStatus());
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

            if (Integer.valueOf(POST_STATUS_PUBLISHED).equals(post.getStatus())) {
                post.setAuditStatus(AUDIT_STATUS_APPROVED);
            }
        }

        // Song：3. 其他字段
        if (request.getIsAnonymous() != null) {
            post.setIsAnonymous(request.getIsAnonymous());
        }
        String requestedPostType = request.getPostType() != null ? request.getPostType() : post.getPostType();
        boolean switchingToLottery = POST_TYPE_LOTTERY.equals(normalizePostType(requestedPostType))
                && !POST_TYPE_LOTTERY.equalsIgnoreCase(post.getPostType());
        applyPostTypeRules(
                post,
                requestedPostType,
                request.getCommentDeadline() != null ? request.getCommentDeadline() : post.getCommentDeadline(),
                request.getCommentOncePerUser() != null
                        ? request.getCommentOncePerUser()
                        : switchingToLottery
                        ? Boolean.TRUE
                        : Integer.valueOf(1).equals(post.getCommentOncePerUser()));
        if (StringUtils.hasText(request.getLocationName())) {
            post.setLocationName(request.getLocationName());
        }
        if (request.getSectionId() != null) {
            // Song：验证板块是否存在
            Section section = sectionService.getSectionById(request.getSectionId());
            if (section == null) {
                throw new BusinessException(ResultCode.SECTION_NOT_FOUND);
            }
            post.setSectionId(request.getSectionId());
        }
        if (StringUtils.hasText(request.getCoverImage())) {
            post.setCoverImage(request.getCoverImage());
        }
        if (StringUtils.hasText(request.getTags())) {
            post.setTags(normalizeTags(request.getTags()));
        }

        if (publishAction) {
            validatePostForPublish(post);
            post.setStatus(POST_STATUS_PUBLISHED);
            post.setAuditStatus(AUDIT_STATUS_APPROVED);
            post.setRejectReason(null);
            processPostTags(post.getTags());
        } else if (Integer.valueOf(POST_STATUS_DRAFT).equals(request.getStatus())) {
            post.setStatus(POST_STATUS_DRAFT);
            if (!AUDIT_STATUS_REJECTED.equalsIgnoreCase(post.getAuditStatus())) {
                post.setAuditStatus(AUDIT_STATUS_DRAFT);
            }
        }

        post.setUpdateTime(LocalDateTime.now());

        // Song：媒体处理。mediaList 优先（覆盖式），传空列表表示清空；传 null 视为不动。
        List<MediaObject> updateMediaList = request.getMediaList();
        if (updateMediaList != null) {
            post.setImages(extractAccessUrls(updateMediaList));
            if (!StringUtils.hasText(post.getCoverImage())) {
                post.setCoverImage(firstAccessUrl(updateMediaList));
            }
        } else if (StringUtils.hasText(request.getImages())) {
            try {
                List<String> images = objectMapper.readValue(request.getImages(), new TypeReference<List<String>>() {
                });
                post.setImages(images);
            } catch (Exception e) {
                // Song：忽略异常或记录日志
            }
        }

        if (visibleChanged) {
            savePostVersionHistory(before, userId, buildPostChangeSummary(before, post, request));
        }

        this.updateById(post);

        // Song：覆盖式同步 sys_post_media
        if (updateMediaList != null) {
            postMediaService.saveForPost(post.getId(), updateMediaList);
        }

        postCacheManager.invalidatePostFeedCache(oldSectionId, post.getSectionId());
        postCacheManager.bumpPostDetailCacheVersion(post.getId());
        // Song：异步同步更新后的帖子到 Meilisearch
        asyncTaskService.syncPostToSearchAsync(post.getId());
    }

    @Override
    public List<PostVersionHistoryResp> listVersionHistory(String postId, String userId) {
        Post post = getById(postId);
        if (post == null) {
            throw new BusinessException(ResultCode.POST_NOT_FOUND);
        }
        if (!canManagePost(post, userId)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "无权查看该帖版本历史");
        }

        List<PostVersionHistory> histories = postVersionHistoryMapper.selectList(
                Wrappers.<PostVersionHistory>lambdaQuery()
                        .eq(PostVersionHistory::getPostId, postId)
                        .orderByDesc(PostVersionHistory::getVersionNo)
                        .last("LIMIT 30")
        );
        return histories.stream().map(this::toPostVersionHistoryResp).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePost(String postId, String userId) {
        Post post = getById(postId);
        if (post == null) {
            throw new BusinessException(ResultCode.POST_NOT_FOUND);
        }
        Long oldSectionId = post.getSectionId();

        if (isDeletedPost(post)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "帖子已在回收站中");
        }

        if (!canDeleteOrRestorePost(post, userId)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "无权删除该帖子");
        }

        post.setStatus(POST_STATUS_DRAFT);
        post.setAuditStatus(AUDIT_STATUS_DELETED);
        post.setRejectReason(null);
        clearModerationDisplayFlags(post);
        post.setUpdateTime(LocalDateTime.now());
        this.updateById(post);
        postCacheManager.invalidatePostFeedCache(oldSectionId, null);
        postCacheManager.bumpPostDetailCacheVersion(postId);
        // Song：从 Meilisearch 索引中删除（软删除的帖子不索引）
        asyncTaskService.syncPostToSearchAsync(postId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreDeletedPost(String postId, String userId) {
        Post post = getById(postId);
        if (post == null) {
            throw new BusinessException(ResultCode.POST_NOT_FOUND);
        }
        if (!isDeletedPost(post)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该帖子不在回收站中");
        }
        if (!canDeleteOrRestorePost(post, userId)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "无权恢复该帖子");
        }
        if (post.getUpdateTime() == null
                || post.getUpdateTime().plusDays(RESTORE_GRACE_DAYS).isBefore(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "已超过7天冷静期，无法恢复");
        }

        validatePostForPublish(post);
        post.setStatus(POST_STATUS_PUBLISHED);
        post.setAuditStatus(AUDIT_STATUS_APPROVED);
        post.setRejectReason(null);
        post.setUpdateTime(LocalDateTime.now());
        this.updateById(post);
        addPostIdToBloomFilter(postId);
        postCacheManager.invalidatePostFeedCache(null, post.getSectionId());
        postCacheManager.bumpPostDetailCacheVersion(postId);
        // Song：恢复后重新索引到 Meilisearch
        asyncTaskService.syncPostToSearchAsync(postId);

    }

    @Override
    public void featurePost(String postId, String userId) {
        Post post = getById(postId);
        if (post == null) {
            throw new BusinessException(ResultCode.POST_NOT_FOUND);
        }
        if (isDeletedPost(post)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "帖子已删除，请先恢复后再操作");
        }

        if (!canAdminOrModerateAssignedSection(userId, post.getSectionId())) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "无权操作精华");
        }

        post.setIsFeatured(post.getIsFeatured() != null && post.getIsFeatured() == 1 ? 0 : 1);
        post.setUpdateTime(java.time.LocalDateTime.now());
        this.updateById(post);
        postCacheManager.invalidatePostFeedCache(post.getSectionId(), post.getSectionId());
        postCacheManager.bumpPostDetailCacheVersion(post.getId());
    }

    @Override
    public void rejectPost(String postId, String reason, String operatorId) {
        Post post = getById(postId);
        if (post == null) throw new BusinessException(ResultCode.POST_NOT_FOUND);
        ensureModerationPermission(post, operatorId);
        markPostRejected(post, reason);
        this.updateById(post);
        postCacheManager.invalidatePostFeedCache(post.getSectionId(), post.getSectionId());
        postCacheManager.bumpPostDetailCacheVersion(post.getId());
        // 异步通知作者
        if (post.getUserId() != null) {
            String notifyTitle = "您的帖子被打回修改";
            String notifyContent = String.format("您的帖子「%s」被审核人员打回，原因：%s。请修改后重新提交。",
                    post.getTitle(), org.springframework.util.StringUtils.hasText(reason) ? reason : "违规内容");
            asyncTaskService.sendSystemNotificationAsync(post.getUserId(), notifyTitle, notifyContent, post.getId());
        }
    }

    @Override
    public void approvePost(String postId, String operatorId) {
        Post post = getById(postId);
        if (post == null) throw new BusinessException(ResultCode.POST_NOT_FOUND);
        ensureModerationPermission(post, operatorId);
        post.setAuditStatus(AUDIT_STATUS_APPROVED);
        post.setStatus(POST_STATUS_PUBLISHED);
        post.setRejectReason(null);
        post.setUpdateTime(java.time.LocalDateTime.now());
        this.updateById(post);
        addPostIdToBloomFilter(postId);
        postCacheManager.invalidatePostFeedCache(post.getSectionId(), post.getSectionId());
        postCacheManager.bumpPostDetailCacheVersion(post.getId());
    }

    @Override
    public IPage<Post> searchAllList(PostSearchReq postSearchRequest) {
        int page = postSearchRequest.getPage() != null ? postSearchRequest.getPage() : 1;
        int pageSize = clampPageSize(postSearchRequest.getPageSize());
        Page<Post> pagePram = new Page<>(page, pageSize);
        pagePram.setSearchCount(Boolean.TRUE.equals(postSearchRequest.getNeedTotal()));
        if (postSearchRequest.getCursor() != null) {
            pagePram.setCurrent(1);
        }

        String normalizedKeyword = normalizeKeyword(postSearchRequest.getKeyword());
        List<String> keywordTerms = splitKeywordTerms(normalizedKeyword);
        // Song：Meilisearch 路径已用 in() 限定 postId，不需要 FULLTEXT 关键词查询
        KeywordSearchMode initialMode = postSearchRequest.getMeiliPostIds() != null
                ? KeywordSearchMode.NONE : KeywordSearchMode.FULLTEXT;
        try {
            return this.page(pagePram, buildSearchWrapper(postSearchRequest, keywordTerms, initialMode));
        } catch (Exception e) {
            if (postSearchRequest.getMeiliPostIds() != null) {
                // Song：Meilisearch 路径出错不应回退 LIKE（会改变语义），直接抛
                throw e;
            }
            if (keywordTerms.isEmpty() || !shouldFallbackToLikeSearch(e)) {
                throw e;
            }
            log.warn("帖子全文检索不可用，回退到 LIKE 搜索: err={}", e.getMessage());
            return this.page(pagePram, buildSearchWrapper(postSearchRequest, keywordTerms, KeywordSearchMode.LIKE));
        }
    }

    private String resolveNavType(PostSearchReq request) {
        if (request == null) {
            return null;
        }
        if (StringUtils.hasText(request.getNavType())) {
            String navType = request.getNavType().trim().toLowerCase();
            if ("latest".equals(navType) || "hot".equals(navType) || "essence".equals(navType)) {
                return navType;
            }
        }
        if (Boolean.TRUE.equals(request.getIsFeatured())) {
            return "essence";
        }
        if ("hot".equalsIgnoreCase(request.getOrderBy())) {
            return "hot";
        }
        return null;
    }

    private String resolveOrderBy(PostSearchReq request, String navType) {
        if ("hot".equals(navType)) {
            return "hot";
        }
        if ("latest".equals(navType) || "essence".equals(navType)) {
            return "new";
        }
        return request != null && StringUtils.hasText(request.getOrderBy())
                ? request.getOrderBy()
                : "new";
    }

    private boolean hasConcreteCategory(String category) {
        if (!StringUtils.hasText(category)) {
            return false;
        }
        String normalized = category.trim().toLowerCase();
        return !"all".equals(normalized) && !"0".equals(normalized) && !"全部".equals(normalized);
    }

    private Long resolveCategorySectionId(String category) {
        if (!hasConcreteCategory(category)) {
            return null;
        }
        try {
            long sectionId = Long.parseLong(category.trim());
            return sectionId > 0 ? sectionId : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LambdaQueryWrapper<Post> buildSearchWrapper(PostSearchReq request,
                                                        List<String> keywordTerms,
                                                        KeywordSearchMode keywordSearchMode) {
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
                Post::getPostType,
                Post::getCommentDeadline,
                Post::getCommentOncePerUser,
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
                Post::getHasAdoptedAnswer,
                Post::getHeatScore,
                Post::getSentimentScore,
                Post::getCreateTime,
                Post::getUpdateTime,
                Post::getLastReplyAt,
                Post::getLastActivityAt);

        String navType = resolveNavType(request);
        String orderBy = resolveOrderBy(request, navType);
        Long categorySectionId = resolveCategorySectionId(request.getCategory());
        boolean invalidCategory = hasConcreteCategory(request.getCategory()) && categorySectionId == null;
        Long effectiveSectionId = request.getSectionId() != null ? request.getSectionId() : categorySectionId;

        if (invalidCategory) {
            wrapper.eq(Post::getId, "__EMPTY__");
        }

        if (request.getSectionIds() != null && !request.getSectionIds().isEmpty()) {
            wrapper.in(Post::getSectionId, request.getSectionIds());
            wrapper.eq(categorySectionId != null, Post::getSectionId, categorySectionId);
        } else {
            wrapper.eq(effectiveSectionId != null, Post::getSectionId, effectiveSectionId);
        }

        if (Boolean.TRUE.equals(request.getIsFeatured()) || "essence".equals(navType)) {
            wrapper.eq(Post::getIsFeatured, 1);
        }

        if (StringUtils.hasText(request.getTimeRange())) {
            LocalDateTime start = TimeRangeUtils.resolveRangeStart(request.getTimeRange());
            if (start != null) {
                if ("hot".equalsIgnoreCase(orderBy)) {
                    wrapper.ge(Post::getLastActivityAt, start);
                    wrapper.and(w -> w.gt(Post::getViewCount, 0).or().gt(Post::getCommentCount, 0));
                } else {
                    wrapper.ge(Post::getCreateTime, start);
                }
            }
        }

        wrapper.eq(StringUtils.hasText(request.getUserId()), Post::getUserId, request.getUserId());

        String currentUserId = com.campus.trend.campus_pulse.utils.SecurityUtils.getCurrentUserId();
        boolean isQueryingSelf = StringUtils.hasText(request.getUserId())
                && request.getUserId().equals(currentUserId);
        boolean isModerationView = Boolean.TRUE.equals(request.getModerationView());
        boolean isDeletedFilter = AUDIT_STATUS_DELETED.equalsIgnoreCase(request.getAuditStatus());
        boolean includeDeleted = Boolean.TRUE.equals(request.getIncludeDeleted()) || isDeletedFilter;

        if (StringUtils.hasText(request.getAuditStatus())) {
            wrapper.eq(Post::getAuditStatus, request.getAuditStatus());
        }

        if (isDeletedFilter && !isQueryingSelf && !isModerationView) {
            wrapper.eq(Post::getId, "__EMPTY__");
        } else if (!includeDeleted) {
            applyNotDeletedFilter(wrapper);
        }

        if (isModerationView) {
            if (request.getStatus() != null && request.getStatus() != -1) {
                wrapper.eq(Post::getStatus, request.getStatus());
            }
        } else if (isQueryingSelf) {
            if (request.getStatus() != null && request.getStatus() != -1) {
                wrapper.eq(Post::getStatus, request.getStatus());
            } else if (!isDeletedFilter) {
                wrapper.eq(Post::getStatus, POST_STATUS_PUBLISHED);
            }
        } else if (request.getStatus() != null && request.getStatus() != -1
                && !Integer.valueOf(POST_STATUS_PUBLISHED).equals(request.getStatus())) {
            wrapper.eq(Post::getId, "__EMPTY__");
        } else {
            wrapper.eq(Post::getStatus, POST_STATUS_PUBLISHED);
            applyPublicAuditVisibilityFilter(wrapper);
        }

        if (Boolean.TRUE.equals(request.getPinnedOnly())) {
            wrapper.and(w -> w.eq(Post::getGlobalPin, 1).or().eq(Post::getCategoryPin, 1));
        }

        // Song：Meilisearch 路径 —— 用检索返回的 postId 列表做 in() 限定，跳过 FULLTEXT 关键词查询
        if (request.getMeiliPostIds() != null) {
            if (request.getMeiliPostIds().isEmpty()) {
                wrapper.eq(Post::getId, "__EMPTY__");
            } else {
                wrapper.in(Post::getId, request.getMeiliPostIds());
            }
        } else {
            applyKeywordSearch(wrapper, keywordTerms, keywordSearchMode, request.getMatchedAuthorIds());
        }

        if (StringUtils.hasText(request.getTag())) {
            String[] tagArr = request.getTag().trim().split("[,，]+");
            wrapper.and(w -> {
                boolean first = true;
                for (String raw : tagArr) {
                    String t = raw.trim();
                    if (t.isEmpty()) continue;
                    // 同时匹配规范格式和历史带空格格式
                    final String spaced = " " + t;
                    if (first) {
                        w.apply("FIND_IN_SET({0}, tags)", t)
                         .or().apply("FIND_IN_SET({0}, tags)", spaced);
                        first = false;
                    } else {
                        w.or().apply("FIND_IN_SET({0}, tags)", t)
                         .or().apply("FIND_IN_SET({0}, tags)", spaced);
                    }
                }
            });
        }

        List<String> likedIds = resolvePostIdsByRelation(request.getLikedBy(), true);
        if (StringUtils.hasText(request.getLikedBy())) {
            if (likedIds.isEmpty()) {
                wrapper.eq(Post::getId, "__EMPTY__");
            } else {
                wrapper.in(Post::getId, likedIds);
            }
        }

        List<String> collectedIds = resolvePostIdsByRelation(request.getCollectedBy(), false);
        if (StringUtils.hasText(request.getCollectedBy())) {
            if (collectedIds.isEmpty()) {
                wrapper.eq(Post::getId, "__EMPTY__");
            } else {
                wrapper.in(Post::getId, collectedIds);
            }
        }

        boolean shouldApplyPin = !StringUtils.hasText(request.getNavType()) && (!"new".equalsIgnoreCase(orderBy)
                || effectiveSectionId != null
                || (request.getSectionIds() != null && !request.getSectionIds().isEmpty()));
        if (shouldApplyPin) {
            wrapper.orderByDesc(Post::getGlobalPin);
            wrapper.orderByDesc(Post::getCategoryPin);
            wrapper.orderByAsc(Post::getPinOrder);
        }

        if ("hot".equalsIgnoreCase(orderBy)) {
            wrapper.orderByDesc(Post::getViewCount);
            wrapper.orderByDesc(Post::getLikeCount);
            wrapper.orderByDesc(Post::getCommentCount);
            wrapper.orderByDesc(Post::getCollectCount);
            wrapper.orderByDesc(Post::getHeatScore);
            wrapper.orderByDesc(Post::getCreateTime);
            wrapper.orderByDesc(Post::getId);
        } else if ("relevance".equalsIgnoreCase(orderBy) && !keywordTerms.isEmpty()) {
            wrapper.orderByDesc(Post::getHeatScore);
            wrapper.orderByDesc(Post::getLikeCount);
            wrapper.orderByDesc(Post::getCommentCount);
            wrapper.orderByDesc(Post::getCreateTime);
        } else {
            if (request.getCursor() != null) {
                LocalDateTime cursor = request.getCursor();
                String cursorId = request.getCursorId();
                wrapper.and(w -> {
                    w.lt(Post::getCreateTime, cursor);
                    if (StringUtils.hasText(cursorId)) {
                        w.or(w2 -> w2.eq(Post::getCreateTime, cursor).lt(Post::getId, cursorId));
                    }
                });
            }
            wrapper.orderByDesc(Post::getCreateTime);
            wrapper.orderByDesc(Post::getId);
        }
        return wrapper;
    }

    private void applyKeywordSearch(LambdaQueryWrapper<Post> wrapper,
                                    List<String> keywordTerms,
                                    KeywordSearchMode keywordSearchMode,
                                    List<String> matchedAuthorIds) {
        boolean hasKeywords = keywordTerms != null && !keywordTerms.isEmpty()
                && keywordSearchMode != KeywordSearchMode.NONE;
        boolean hasAuthorMatch = matchedAuthorIds != null && !matchedAuthorIds.isEmpty();

        if (!hasKeywords && !hasAuthorMatch) {
            return;
        }

        wrapper.and(outer -> {
            if (hasKeywords && keywordSearchMode == KeywordSearchMode.FULLTEXT) {
                String booleanQuery = buildBooleanModeKeyword(keywordTerms);
                outer.apply("MATCH(title, content, summary, tags) AGAINST ({0} IN BOOLEAN MODE)", booleanQuery);
            } else if (hasKeywords) {
                outer.and(w -> {
                    for (String term : keywordTerms) {
                        w.and(inner -> inner.likeRight(Post::getTitle, term)
                                .or()
                                .likeRight(Post::getSummary, term)
                                .or()
                                .like(Post::getTags, term)
                                .or()
                                .like(Post::getContent, term));
                    }
                });
            }

            if (hasAuthorMatch) {
                if (hasKeywords) {
                    outer.or().in(Post::getUserId, matchedAuthorIds);
                } else {
                    outer.in(Post::getUserId, matchedAuthorIds);
                }
            }
        });
    }

    private List<String> resolvePostIdsByRelation(String userId, boolean likedRelation) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        if (likedRelation) {
            return postLikeService.lambdaQuery()
                    .select(PostLike::getPostId)
                    .eq(PostLike::getUserId, userId)
                    .list()
                    .stream()
                    .map(PostLike::getPostId)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
        }
        return postCollectService.lambdaQuery()
                .select(PostCollect::getPostId)
                .eq(PostCollect::getUserId, userId)
                .list()
                .stream()
                .map(PostCollect::getPostId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String buildBooleanModeKeyword(List<String> keywordTerms) {
        return keywordTerms.stream()
                .map(term -> term.replaceAll("[^\\p{IsHan}A-Za-z0-9_]", ""))
                .filter(StringUtils::hasText)
                .map(term -> "+" + term + "*")
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }

    private boolean shouldFallbackToLikeSearch(Exception e) {
        String message = e.getMessage();
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("fulltext")
                || normalized.contains("against")
                || normalized.contains("syntax")
                || normalized.contains("index");
    }

    // Song：=================== 新增：带作者信息的帖子查询 ===================

    @Override
    public PostResp getPostWithAuthor(String postId) {
        String currentUserId = com.campus.trend.campus_pulse.utils.SecurityUtils.getCurrentUserId();
        if (!StringUtils.hasText(currentUserId)) {
            PostCacheManager.CachedDetail cachedDetail = postCacheManager.readPostDetailCache(postId);
            if (cachedDetail.isHit()) {
                hydratePostAdoptionStatus(cachedDetail.getValue());
                return cachedDetail.getValue();
            }
        }

        Post post = searchByPostId(postId);
        if (post == null) {
            if (!StringUtils.hasText(currentUserId)) {
                postCacheManager.writePostDetailCache(postId, null);
            }
            return null;
        }
        if (!canViewPostDetail(currentUserId, post)) {
            if (!StringUtils.hasText(currentUserId)) {
                postCacheManager.writePostDetailCache(postId, null);
            }
            return null;
        }
        PostResp result = convertToPostResp(post);
        if (!StringUtils.hasText(currentUserId)) {
            postCacheManager.writePostDetailCache(postId, result);
        }
        return result;
    }

    @Override
    public IPage<PostResp> searchPostsWithAuthor(PostSearchReq postSearchRequest) {
        IPage<PostResp> cachedPage = postCacheManager.readPostFeedCache(postSearchRequest);
        if (cachedPage != null) {
            hydratePostFeedAdoptionStatus(cachedPage.getRecords());
            return cachedPage;
        }

        String keyword = postSearchRequest.getKeyword();
        if (StringUtils.hasText(keyword) && keyword.trim().length() >= 2) {
            // Song：Meilisearch 可用时优先用它做关键词检索（中文分词、错字容忍、相关度排序），
            //      返回的 postId 列表交给后续 in() 查询做 hydration + 过滤；不可用则回退作者匹配 + FULLTEXT
            if (searchService.isAvailable()) {
                List<String> meiliIds = searchService.searchPostIds(postSearchRequest);
                if (!meiliIds.isEmpty()) {
                    postSearchRequest.setMeiliPostIds(meiliIds);
                }
            }
            // Song：作者名匹配仍走 DB（Meilisearch 不索引用户昵称）
            if (postSearchRequest.getMeiliPostIds() == null) {
                List<String> authorIds = userService.lambdaQuery()
                        .select(User::getId)
                        .and(q -> q.like(User::getNickname, keyword.trim())
                                .or().like(User::getUsername, keyword.trim()))
                        .eq(User::getStatus, 1)
                        .last("LIMIT 50")
                        .list()
                        .stream().map(User::getId).toList();
                if (!authorIds.isEmpty()) {
                    postSearchRequest.setMatchedAuthorIds(authorIds);
                }
            }
        }

        // Song：1. 先查询帖子分页数据
        IPage<Post> postPage = searchAllList(postSearchRequest);
        List<Post> posts = postPage.getRecords();

        if (posts.isEmpty()) {
            Page<PostResp> emptyPage = new Page<>(postPage.getCurrent(), postPage.getSize(), postPage.getTotal());
            emptyPage.setRecords(new ArrayList<>());
            postCacheManager.writePostFeedCache(postSearchRequest, emptyPage);
            return emptyPage;
        }

        List<String> userIds = posts.stream().map(Post::getUserId).distinct().toList();
        List<String> postIds = posts.stream().map(Post::getId).filter(StringUtils::hasText).distinct().toList();
        List<Long> sectionIds = posts.stream()
                .map(Post::getSectionId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        // Song：3. 批量查询用户
        Map<String, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            userMap.putAll(batchUserService.batchGetUsers(userIds));
        }
        Map<Long, Section> sectionMap = new HashMap<>();
        if (!sectionIds.isEmpty()) {
            List<Section> sections = sectionMapper.selectBatchIds(sectionIds);
            for (Section section : sections) {
                sectionMap.put(section.getId(), section);
            }
        }

        String currentUserId = com.campus.trend.campus_pulse.utils.SecurityUtils.getCurrentUserId();
        Set<String> likedPostIds = Collections.emptySet();
        Set<String> collectedPostIds = Collections.emptySet();
        if (StringUtils.hasText(currentUserId) && !postIds.isEmpty()) {
            likedPostIds = new HashSet<>(postLikeService.lambdaQuery()
                    .select(PostLike::getPostId)
                    .eq(PostLike::getUserId, currentUserId)
                    .in(PostLike::getPostId, postIds)
                    .list()
                    .stream()
                    .map(PostLike::getPostId)
                    .filter(StringUtils::hasText)
                    .toList());
            collectedPostIds = new HashSet<>(postCollectService.lambdaQuery()
                    .select(PostCollect::getPostId)
                    .eq(PostCollect::getUserId, currentUserId)
                    .in(PostCollect::getPostId, postIds)
                    .list()
                    .stream()
                    .map(PostCollect::getPostId)
                    .filter(StringUtils::hasText)
                    .toList());
        }

        // Song：批量查询媒体，避免 N+1
        Map<String, List<MediaObject>> mediaByPost = new HashMap<>();
        if (!postIds.isEmpty()) {
            try {
                List<PostMedia> allMedia = postMediaService.listByPostIds(postIds);
                for (PostMedia row : allMedia) {
                    MediaObject dto = mediaRowToDto(row);
                    if (dto == null || !StringUtils.hasText(row.getPostId())) continue;
                    mediaByPost.computeIfAbsent(row.getPostId(), k -> new ArrayList<>()).add(dto);
                }
            } catch (Exception e) {
                log.debug("批量加载帖子媒体失败, err={}", e.getMessage());
            }
        }

        Set<String> adoptedPostIds = findAdoptedPostIds(postIds);

        List<PostResp> responseList = new ArrayList<>();
        for (Post post : posts) {
            post.setIsLiked(likedPostIds.contains(post.getId()));
            post.setIsCollected(collectedPostIds.contains(post.getId()));
            PostResp resp = convertToPostResp(post, userMap, sectionMap);
            List<MediaObject> postMedia = mediaByPost.get(post.getId());
            if (postMedia != null && !postMedia.isEmpty()) {
                resp.setMediaList(postMedia);
            }
            resp.setHasAdoptedAnswer(adoptedPostIds.contains(post.getId()) ? 1 : 0);
            responseList.add(resp);
        }

        // Song：5. 构造结果页
        Page<PostResp> responsePage = new Page<>(
                postPage.getCurrent(),
                postPage.getSize(),
                postPage.getTotal());
        responsePage.setRecords(responseList);

        postCacheManager.writePostFeedCache(postSearchRequest, responsePage);
        return responsePage;
    }

    @Override
    public IPage<PostResp> searchModerationPosts(PostSearchReq postSearchRequest, String userId) {
        PostSearchReq scopedRequest = postSearchRequest != null ? postSearchRequest : new PostSearchReq();
        scopedRequest.setModerationView(true);
        scopedRequest.setIncludeDeleted(AUDIT_STATUS_DELETED.equalsIgnoreCase(scopedRequest.getAuditStatus()));

        if (com.campus.trend.campus_pulse.utils.PermissionUtils.isUserAdminOrModerator(userId)) {
            // 管理员和全局版主可查全部治理状态。
            if (scopedRequest.getStatus() == null) {
                scopedRequest.setStatus(-1);
            }
            return searchPostsWithAuthor(scopedRequest);
        }

        Set<Long> moderatedSectionIds = sectionModeratorService.getModeratedSectionIds(userId);
        if (moderatedSectionIds.isEmpty()) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "无权访问内容管理");
        }

        List<Long> scopedSectionIds;
        if (scopedRequest.getSectionId() != null) {
            if (!moderatedSectionIds.contains(scopedRequest.getSectionId())) {
                return buildEmptyPostRespPage(scopedRequest);
            }
            scopedSectionIds = List.of(scopedRequest.getSectionId());
        } else if (scopedRequest.getSectionIds() != null && !scopedRequest.getSectionIds().isEmpty()) {
            scopedSectionIds = scopedRequest.getSectionIds().stream()
                    .filter(moderatedSectionIds::contains)
                    .distinct()
                    .toList();
            if (scopedSectionIds.isEmpty()) {
                return buildEmptyPostRespPage(scopedRequest);
            }
        } else {
            scopedSectionIds = new ArrayList<>(moderatedSectionIds);
        }

        if (scopedRequest.getStatus() == null) {
            scopedRequest.setStatus(-1);
        }
        scopedRequest.setSectionId(null);
        scopedRequest.setSectionIds(scopedSectionIds);
        return searchPostsWithAuthor(scopedRequest);
    }

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
        response.setPostType(post.getPostType());
        response.setCommentDeadline(post.getCommentDeadline());
        response.setCommentOncePerUser(post.getCommentOncePerUser());
        response.setIsPinned(post.getIsPinned());
        response.setLocationName(post.getLocationName());
        response.setStatus(post.getStatus());
        response.setAuditStatus(post.getAuditStatus());
        response.setRejectReason(post.getRejectReason());
        response.setViewCount(post.getViewCount());
        response.setLikeCount(post.getLikeCount());
        response.setCollectCount(post.getCollectCount());
        response.setCommentCount(post.getCommentCount());
        response.setHasAdoptedAnswer(post.getHasAdoptedAnswer() != null ? post.getHasAdoptedAnswer() : 0);
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
        response.setIsFeatured(post.getIsFeatured());

        // Song：===== 填充作者信息 =====
        if (post.getIsAnonymous() != null && post.getIsAnonymous() == 1) {
            // Song：匿名帖子
            response.setAuthorName("匿名同学");
            response.setAuthorAvatar(null);
        } else {
            // Song：非匿名帖子
            User author = userMap != null ? userMap.get(post.getUserId()) : null;
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
                response.setAuthorBadgeText(author.getBadgeText());
                response.setAuthorBadgeColor(author.getBadgeColor());
                response.setAuthorBadgeStyle(author.getBadgeStyle());
                // Song：透传作者信任等级（前端展示 TL 徽章）
                // 管理员角色直接 TL4（与 getTrustLevel 兜底一致，避免老数据 trust_level=0 不显示徽章）
                if ("ROLE_ADMIN".equals(role) || "ROLE_SUPER_ADMIN".equals(role)) {
                    response.setAuthorTrustLevel(4);
                } else {
                    response.setAuthorTrustLevel(author.getTrustLevel() != null ? author.getTrustLevel() : 0);
                }
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
                    // Song：把板块的采纳开关透传给前端，前端据此显示采纳按钮（替代写死的 sectionId==11，跨环境/改名均不受影响）
                    response.setAllowAdoption(section.getAllowAdoption());
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
        PostResp resp = convertToPostResp(post, null, null);
        hydratePostAdoptionStatus(resp);
        if (resp != null && StringUtils.hasText(post.getId())) {
            try {
                List<PostMedia> mediaRows = postMediaService.listByPostId(post.getId());
                if (!mediaRows.isEmpty()) {
                    List<MediaObject> media = new ArrayList<>(mediaRows.size());
                    for (PostMedia row : mediaRows) {
                        MediaObject dto = mediaRowToDto(row);
                        if (dto != null) media.add(dto);
                    }
                    resp.setMediaList(media);
                }
            } catch (Exception e) {
                log.debug("加载帖子媒体失败 postId={}, err={}", post.getId(), e.getMessage());
            }
        }
        return resp;
    }

    private void hydratePostAdoptionStatus(PostResp resp) {
        if (resp == null || !StringUtils.hasText(resp.getId())) {
            return;
        }
        try {
            resp.setHasAdoptedAnswer(hasAnswerAdoptionRecord(resp.getId()) ? 1 : 0);
        } catch (Exception e) {
            if (resp.getHasAdoptedAnswer() == null) {
                resp.setHasAdoptedAnswer(0);
            }
            log.debug("反查帖子采纳状态失败 postId={}, err={}", resp.getId(), e.getMessage());
        }
    }

    private void hydratePostFeedAdoptionStatus(List<PostResp> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<String> postIds = new ArrayList<>();
        for (PostResp record : records) {
            if (record != null && StringUtils.hasText(record.getId())) {
                postIds.add(record.getId());
            }
        }
        Set<String> adoptedPostIds = findAdoptedPostIds(postIds);
        for (PostResp record : records) {
            if (record != null && StringUtils.hasText(record.getId())) {
                record.setHasAdoptedAnswer(adoptedPostIds.contains(record.getId()) ? 1 : 0);
            }
        }
    }

    private Set<String> findAdoptedPostIds(List<String> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptySet();
        }
        try {
            List<AnswerAdoption> adoptions = answerAdoptionMapper.selectList(
                    Wrappers.<AnswerAdoption>lambdaQuery()
                            .select(AnswerAdoption::getPostId)
                            .in(AnswerAdoption::getPostId, postIds)
            );
            Set<String> adoptedPostIds = new HashSet<>();
            for (AnswerAdoption adoption : adoptions) {
                if (adoption != null && StringUtils.hasText(adoption.getPostId())) {
                    adoptedPostIds.add(adoption.getPostId());
                }
            }
            return adoptedPostIds;
        } catch (Exception e) {
            log.debug("批量反查帖子采纳状态失败, err={}", e.getMessage());
            return Collections.emptySet();
        }
    }

    private boolean hasAnswerAdoptionRecord(String postId) {
        if (!StringUtils.hasText(postId)) {
            return false;
        }
        Long count = answerAdoptionMapper.selectCount(
                Wrappers.<AnswerAdoption>lambdaQuery().eq(AnswerAdoption::getPostId, postId)
        );
        return count != null && count > 0;
    }

    @Override
    public void refreshPostCaches(String postId) {
        if (!StringUtils.hasText(postId)) {
            return;
        }
        Long sectionId = getSectionIdByPostId(postId);
        postCacheManager.invalidatePostFeedCache(sectionId, sectionId);
        postCacheManager.bumpPostDetailCacheVersion(postId);
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

    private Long getSectionIdByPostId(String postId) {
        Post post = getById(postId);
        return post != null ? post.getSectionId() : null;
    }

    private Page<PostResp> buildEmptyPostRespPage(PostSearchReq request) {
        long current = request != null && request.getPage() != null ? request.getPage() : 1L;
        long size = clampPageSize(request != null ? request.getPageSize() : null);
        Page<PostResp> emptyPage = new Page<>(current, size, 0);
        emptyPage.setRecords(new ArrayList<>());
        return emptyPage;
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
            return Collections.emptyList();
        }
        return Arrays.stream(normalizedKeyword.split("\\s+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .limit(5)
                .toList();
    }

    private void fillPostDraftFields(Post post, PostDraftReq draftRequest) {
        if (draftRequest.getSectionId() != null) {
            Section section = sectionService.getSectionById(draftRequest.getSectionId());
            if (section == null) {
                throw new BusinessException(ResultCode.SECTION_NOT_FOUND);
            }
            post.setSectionId(draftRequest.getSectionId());
        }

        post.setTitle(StringUtils.hasText(draftRequest.getTitle()) ? draftRequest.getTitle().trim() : null);
        post.setContent(StringUtils.hasText(draftRequest.getContent()) ? draftRequest.getContent() : null);
        post.setCoverImage(StringUtils.hasText(draftRequest.getCoverImage()) ? draftRequest.getCoverImage().trim() : null);
        post.setTags(normalizeTags(draftRequest.getTags()));
        post.setIsAnonymous(draftRequest.getIsAnonymous() != null ? draftRequest.getIsAnonymous() : 0);
        applyPostTypeRules(
                post,
                draftRequest.getPostType() != null ? draftRequest.getPostType() : post.getPostType(),
                draftRequest.getCommentDeadline() != null ? draftRequest.getCommentDeadline() : post.getCommentDeadline(),
                draftRequest.getCommentOncePerUser() != null
                        ? draftRequest.getCommentOncePerUser()
                        : Integer.valueOf(1).equals(post.getCommentOncePerUser()));
        post.setLocationName(StringUtils.hasText(draftRequest.getLocationName()) ? draftRequest.getLocationName().trim() : null);
        post.setSummary(buildSummary(post.getContent()));

        List<MediaObject> draftMediaList = draftRequest.getMediaList();
        if (draftMediaList != null && !draftMediaList.isEmpty()) {
            post.setImages(extractAccessUrls(draftMediaList));
            if (!StringUtils.hasText(post.getCoverImage())) {
                post.setCoverImage(firstAccessUrl(draftMediaList));
            }
        } else if (StringUtils.hasText(draftRequest.getImages())) {
            try {
                List<String> images = objectMapper.readValue(draftRequest.getImages(), new TypeReference<List<String>>() {
                });
                post.setImages(images);
            } catch (Exception e) {
                List<String> images = new ArrayList<>();
                images.add(draftRequest.getImages());
                post.setImages(images);
            }
        }
    }

    private void validatePostForPublish(Post post) {
        if (!StringUtils.hasText(post.getTitle()) || post.getTitle().trim().length() < 4
                || post.getTitle().trim().length() > 100) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "文章题目需超过3个字符且不超过100个字符");
        }
        if (!StringUtils.hasText(post.getContent()) || post.getContent().trim().length() < 31) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "文章内容需超过30个字符");
        }
        if (post.getSectionId() == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "板块ID不允许为空");
        }
        if (!StringUtils.hasText(post.getTags())) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "标签不允许为空");
        }
    }

    private void validateDraftForSave(Post post) {
        if (!StringUtils.hasText(post.getTitle())) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "草稿至少需要填写标题");
        }
        if (post.getSectionId() == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "草稿至少需要选择一个板块");
        }
    }

    private enum KeywordSearchMode {
        NONE,
        FULLTEXT,
        LIKE
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pinPost(String postId, String userId) {
        // Song：校验权限 (只有管理员能操作置顶)
        if (!com.campus.trend.campus_pulse.utils.PermissionUtils.isUserAdmin(userId)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "无权进行此操作");
        }

        Post post = getById(postId);
        if (post == null) {
            throw new BusinessException(ResultCode.POST_NOT_FOUND);
        }
        if (isDeletedPost(post)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "帖子已删除，请先恢复后再操作");
        }

        Integer currentPinned = post.getIsPinned() != null ? post.getIsPinned() : 0;
        Integer newPinned = currentPinned == 1 ? 0 : 1;
        post.setIsPinned(newPinned);
        post.setGlobalPin(newPinned); // Song：同步到新字段
        post.setUpdateTime(LocalDateTime.now());
        this.updateById(post);
        postCacheManager.invalidatePostFeedCache(post.getSectionId(), post.getSectionId());
        postCacheManager.bumpPostDetailCacheVersion(post.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setGlobalPin(String postId, String userId, Integer pinOrder, String expireAt) {
        Post post = getById(postId);
        if (post == null) {
            throw new BusinessException(ResultCode.POST_NOT_FOUND);
        }
        if (isDeletedPost(post)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "帖子已删除，请先恢复后再操作");
        }

        boolean isAdmin = com.campus.trend.campus_pulse.utils.PermissionUtils.isUserAdmin(userId);
        if (!isAdmin) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "无权操作全局置顶");
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
        postCacheManager.invalidatePostFeedCache(post.getSectionId(), post.getSectionId());
        postCacheManager.bumpPostDetailCacheVersion(post.getId());

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
            throw new BusinessException(ResultCode.POST_NOT_FOUND);
        }
        if (isDeletedPost(post)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "帖子已删除，请先恢复后再操作");
        }

        if (!canAdminOrModerateAssignedSection(userId, post.getSectionId())) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "无权操作板块置顶");
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
        postCacheManager.invalidatePostFeedCache(post.getSectionId(), post.getSectionId());
        postCacheManager.bumpPostDetailCacheVersion(post.getId());

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
        wrapper.eq(Post::getStatus, POST_STATUS_PUBLISHED);
        applyPublicAuditVisibilityFilter(wrapper);

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

        return posts.stream()
                .map(this::convertToPostResp)
                .collect(java.util.stream.Collectors.toList());
    }

    private void applyPublicVisibilityFilter(LambdaQueryWrapper<Post> wrapper) {
        wrapper.eq(Post::getStatus, POST_STATUS_PUBLISHED);
        applyNotDeletedFilter(wrapper);
        applyPublicAuditVisibilityFilter(wrapper);
    }

    private void applyNotDeletedFilter(LambdaQueryWrapper<Post> wrapper) {
        wrapper.and(w -> w.isNull(Post::getAuditStatus)
                .or()
                .ne(Post::getAuditStatus, AUDIT_STATUS_DELETED));
    }

    private void applyPublicAuditVisibilityFilter(LambdaQueryWrapper<Post> wrapper) {
        wrapper.and(w -> w.isNull(Post::getAuditStatus)
                .or()
                .eq(Post::getAuditStatus, "")
                .or()
                .eq(Post::getAuditStatus, AUDIT_STATUS_PENDING)
                .or()
                .eq(Post::getAuditStatus, AUDIT_STATUS_APPROVED));
    }

    private void ensureModerationPermission(Post post, String operatorId) {
        if (post == null) {
            throw new BusinessException(ResultCode.POST_NOT_FOUND);
        }
        if (isDeletedPost(post)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "帖子已删除，请先恢复后再操作");
        }
        if (!canAdminOrModerateAssignedSection(operatorId, post.getSectionId())) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "无权审核该帖子");
        }
    }

    private void markPostRejected(Post post, String reason) {
        post.setAuditStatus(AUDIT_STATUS_REJECTED);
        post.setStatus(POST_STATUS_DRAFT);
        post.setRejectReason(reason);
        clearModerationDisplayFlags(post);
        post.setUpdateTime(java.time.LocalDateTime.now());
    }

    private void clearModerationDisplayFlags(Post post) {
        post.setIsPinned(0);
        post.setGlobalPin(0);
        post.setCategoryPin(0);
        post.setPinOrder(0);
        post.setPinExpireAt(null);
        post.setIsFeatured(0);
    }

    private boolean canViewPostDetail(String currentUserId, Post post) {
        if (post == null) {
            return false;
        }
        if (isPubliclyVisible(post)) {
            return true;
        }
        if (!StringUtils.hasText(currentUserId)) {
            return false;
        }
        if (currentUserId.equals(post.getUserId())) {
            return true;
        }
        if (com.campus.trend.campus_pulse.utils.PermissionUtils.isUserAdminOrModerator(currentUserId)) {
            return true;
        }
        return post.getSectionId() != null
                && sectionModeratorService.canModerateSection(currentUserId, post.getSectionId());
    }

    private boolean isPubliclyVisible(Post post) {
        return post != null
                && !isDeletedPost(post)
                && Integer.valueOf(POST_STATUS_PUBLISHED).equals(post.getStatus())
                && isApprovedAuditStatus(post.getAuditStatus());
    }

    private boolean isApprovedAuditStatus(String auditStatus) {
        return !StringUtils.hasText(auditStatus)
                || AUDIT_STATUS_PENDING.equalsIgnoreCase(auditStatus)
                || AUDIT_STATUS_APPROVED.equalsIgnoreCase(auditStatus);
    }

    private boolean isDeletedPost(Post post) {
        return post != null && AUDIT_STATUS_DELETED.equalsIgnoreCase(post.getAuditStatus());
    }

    private Post snapshotPost(Post source) {
        return new Post()
                .setId(source.getId())
                .setUserId(source.getUserId())
                .setSectionId(source.getSectionId())
                .setTitle(source.getTitle())
                .setContent(source.getContent())
                .setTags(source.getTags())
                .setCoverImage(source.getCoverImage());
    }

    private boolean hasVisiblePostChange(Post post, com.campus.trend.campus_pulse.dto.request.PostUpdateReq request) {
        if (request == null) {
            return false;
        }
        if (StringUtils.hasText(request.getTitle()) && !Objects.equals(post.getTitle(), request.getTitle())) {
            return true;
        }
        if (StringUtils.hasText(request.getContent()) && !Objects.equals(post.getContent(), request.getContent())) {
            return true;
        }
        if (request.getSectionId() != null && !Objects.equals(post.getSectionId(), request.getSectionId())) {
            return true;
        }
        if (StringUtils.hasText(request.getCoverImage()) && !Objects.equals(post.getCoverImage(), request.getCoverImage())) {
            return true;
        }
        if (StringUtils.hasText(request.getTags()) && !Objects.equals(post.getTags(), normalizeTags(request.getTags()))) {
            return true;
        }
        if (StringUtils.hasText(request.getPostType()) && !Objects.equals(post.getPostType(), normalizePostType(request.getPostType()))) {
            return true;
        }
        if (request.getCommentDeadline() != null && !Objects.equals(post.getCommentDeadline(), request.getCommentDeadline())) {
            return true;
        }
        if (request.getCommentOncePerUser() != null
                && !Objects.equals(Integer.valueOf(Boolean.TRUE.equals(request.getCommentOncePerUser()) ? 1 : 0), post.getCommentOncePerUser())) {
            return true;
        }
        return request.getMediaList() != null || StringUtils.hasText(request.getImages());
    }

    private void savePostVersionHistory(Post before, String editorId, String changeSummary) {
        Integer maxVersion = postVersionHistoryMapper.selectMaxVersionNo(before.getId());
        User editor = userService.getById(editorId);
        String editorName = editor != null && StringUtils.hasText(editor.getNickname())
                ? editor.getNickname()
                : (editor != null ? editor.getUsername() : editorId);

        PostVersionHistory history = new PostVersionHistory()
                .setPostId(before.getId())
                .setVersionNo((maxVersion != null ? maxVersion : 0) + 1)
                .setEditorId(editorId)
                .setEditorName(editorName)
                .setTitle(before.getTitle())
                .setContent(before.getContent())
                .setTags(before.getTags())
                .setSectionId(before.getSectionId())
                .setCoverImage(before.getCoverImage())
                .setChangeSummary(changeSummary)
                .setCreatedAt(LocalDateTime.now());
        postVersionHistoryMapper.insert(history);
    }

    private String buildPostChangeSummary(Post before, Post after, com.campus.trend.campus_pulse.dto.request.PostUpdateReq request) {
        List<String> changes = new ArrayList<>();
        if (!Objects.equals(before.getTitle(), after.getTitle())) changes.add("标题");
        if (!Objects.equals(before.getContent(), after.getContent())) changes.add("正文");
        if (!Objects.equals(before.getTags(), after.getTags())) changes.add("标签");
        if (!Objects.equals(before.getSectionId(), after.getSectionId())) changes.add("板块");
        if (!Objects.equals(before.getCoverImage(), after.getCoverImage())) changes.add("封面");
        if (request != null && (request.getMediaList() != null || StringUtils.hasText(request.getImages()))) changes.add("媒体");
        return changes.isEmpty() ? "内容更新" : "更新了" + String.join("、", changes);
    }

    private PostVersionHistoryResp toPostVersionHistoryResp(PostVersionHistory history) {
        PostVersionHistoryResp resp = new PostVersionHistoryResp();
        resp.setId(history.getId());
        resp.setPostId(history.getPostId());
        resp.setVersionNo(history.getVersionNo());
        resp.setEditorId(history.getEditorId());
        resp.setEditorName(history.getEditorName());
        resp.setTitle(history.getTitle());
        resp.setContent(history.getContent());
        resp.setTags(history.getTags());
        resp.setSectionId(history.getSectionId());
        resp.setCoverImage(history.getCoverImage());
        resp.setChangeSummary(history.getChangeSummary());
        resp.setCreatedAt(history.getCreatedAt());
        return resp;
    }

    private boolean canDeleteOrRestorePost(Post post, String userId) {
        return canManagePost(post, userId);
    }

    private boolean canManagePost(Post post, String userId) {
        if (post == null || !StringUtils.hasText(userId)) {
            return false;
        }
        if (userId.equals(post.getUserId())) {
            return true;
        }
        return canAdminOrModerateAssignedSection(userId, post.getSectionId());
    }

    private boolean canAdminOrModerateAssignedSection(String userId, Long sectionId) {
        if (!StringUtils.hasText(userId)) {
            return false;
        }
        if (com.campus.trend.campus_pulse.utils.PermissionUtils.isUserAdmin(userId)) {
            return true;
        }
        return sectionId != null && sectionModeratorService.isSectionModerator(userId, sectionId);
    }

    private void applyPostTypeRules(Post post,
                                    String requestedPostType,
                                    LocalDateTime commentDeadline,
                                    Boolean commentOncePerUser) {
        String postType = normalizePostType(requestedPostType);
        post.setPostType(postType);

        if (POST_TYPE_LOTTERY.equals(postType)) {
            post.setIsAnonymous(0);
            post.setCommentDeadline(commentDeadline);
            post.setCommentOncePerUser(Boolean.FALSE.equals(commentOncePerUser) ? 0 : 1);
            return;
        }

        post.setCommentDeadline(null);
        post.setCommentOncePerUser(0);
    }

    private String normalizePostType(String rawType) {
        if (!StringUtils.hasText(rawType)) {
            return POST_TYPE_NORMAL;
        }
        String normalized = rawType.trim().toUpperCase(java.util.Locale.ROOT);
        if (POST_TYPE_LOTTERY.equals(normalized)) {
            return POST_TYPE_LOTTERY;
        }
        return POST_TYPE_NORMAL;
    }

}
