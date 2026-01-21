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
import com.campus.trend.campus_pulse.entity.Tag;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.service.CategoryService;
import com.campus.trend.campus_pulse.service.ContentSecurityService;
import com.campus.trend.campus_pulse.service.PostCollectService;
import com.campus.trend.campus_pulse.service.SentimentAnalysisService;
import java.math.BigDecimal;
import com.campus.trend.campus_pulse.service.PostLikeService;
import com.campus.trend.campus_pulse.service.PostService;
import com.campus.trend.campus_pulse.service.TagService;
import com.campus.trend.campus_pulse.service.UserProfileService;
import com.campus.trend.campus_pulse.service.UserService;
import com.campus.trend.campus_pulse.dto.response.PostResp;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.entity.Category;
import com.campus.trend.campus_pulse.utils.IdUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hankcs.hanlp.HanLP;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostServiceImpl extends ServiceImpl<PostMapper, Post> implements PostService {

    private final PostLikeService postLikeService;
    private final PostCollectService postCollectService;
    private final TagService tagService;
    private final UserProfileService userProfileService;
    private final CategoryService categoryService;
    private final ContentSecurityService contentSecurityService;
    private final SentimentAnalysisService sentimentAnalysisService;
    private final UserService userService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Post searchByPostId(String postId) {
        viewAdd(postId);
        Post post = getById(postId);
        if (post != null) {
            try {
                // 尝试获取当前登录用户，若未登录会抛出异常，捕获后忽略即可
                com.campus.trend.campus_pulse.security.AuthUser authUser = com.campus.trend.campus_pulse.utils.SecurityUtils
                        .getAuthenticatedUser();
                String userId = authUser.getUser().getId();

                post.setIsLiked(postLikeService.isLike(postId, userId));
                post.setIsCollected(postCollectService.isCollect(postId, userId));
                log.info("Post detail: postId={}, userId={}, isLiked={}, isCollected={}", postId, userId,
                        post.getIsLiked(), post.getIsCollected());
            } catch (Exception e) {
                // 未登录用户，保持默认值 (null 或 false)
                log.warn("SearchByPostId - User not authenticated or error: {}", e.getMessage());
                post.setIsLiked(false);
                post.setIsCollected(false);
            }
        }
        return post;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createPost(PostCreateReq createPostRequest, String userId) {
        // 1.检查 category_id 的有效性
        if (createPostRequest.getCategoryID() != null && !createPostRequest.getCategoryID().isEmpty()) {
            if (!categoryService.existsById(createPostRequest.getCategoryID())) {
                throw new RuntimeException("分类不存在: " + createPostRequest.getCategoryID());
            }
        }

        // 2. 内容安全检查 (敏感词过滤)
        String title = createPostRequest.getTitle();
        String content = createPostRequest.getContent();

        if (contentSecurityService.containsSensitiveWords(title)) {
            // 策略选择：直接报错拦截，或者替换为 *
            // 这里选择替换，保证用户发布不受阻，但内容合规
            title = contentSecurityService.filterSensitiveWords(title);
        }

        if (contentSecurityService.containsSensitiveWords(content)) {
            content = contentSecurityService.filterSensitiveWords(content);
        }
        // 如果包含 "抢劫"、"自杀" 等严重违规词，可以直接抛异常拦截
        // if (contentSecurityService.containsHighRiskWords(content)) { ... }

        // 3.构造帖子
        String postID = IdUtils.genId("POST");

        Post sysPost = new Post();
        sysPost.setId(postID);
        sysPost.setUserId(userId);
        sysPost.setCategoryId(createPostRequest.getCategoryID());
        sysPost.setTitle(title);
        sysPost.setContent(content);
        sysPost.setCoverImage(createPostRequest.getCoverImage());
        sysPost.setTags(createPostRequest.getTags());

        // 3.1 情感分析
        double sentimentScore = sentimentAnalysisService.analyzeSentiment(title + " " + content);
        sysPost.setSentimentScore(BigDecimal.valueOf(sentimentScore));

        sysPost.setIsAnonymous(createPostRequest.getIsAnonymous());
        sysPost.setLocationName(createPostRequest.getLocationName());

        // 处理图片
        if (StringUtils.hasText(createPostRequest.getImages())) {
            try {
                List<String> images = objectMapper.readValue(createPostRequest.getImages(),
                        new TypeReference<List<String>>() {
                        });
                sysPost.setImages(images);
            } catch (Exception e) {
                // 如果解析失败，尝试作为单个图片处理，或者忽略
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
        sysPost.setStatus(1); // 正常
        sysPost.setAuditStatus("PENDING"); // 待审核
        sysPost.setHeatScore(0.0); // 初始热度

        // 3.保存帖子
        this.save(sysPost);

        // 4.处理标签（自动创建标签并增加热度）
        processPostTags(createPostRequest.getTags());

        // 5.更新用户画像
        userProfileService.incrementTotalPosts(userId);
        userProfileService.updateLastActiveTime(userId);
        userProfileService.updatePreferredCategories(userId, createPostRequest.getCategoryID());
        userProfileService.addContribution(userId, 5); // 发帖贡献值+5

        // 6.更新活跃地点
        if (createPostRequest.getLocationName() != null && !createPostRequest.getLocationName().isEmpty()) {
            userProfileService.updateActiveRegion(userId, createPostRequest.getLocationName());
        }
    }

    /**
     * 处理帖子标签：自动创建标签并增加热度
     *
     * @param tagsString 标签字符串（如"#考研 #Java"）
     */
    private void processPostTags(String tagsString) {
        if (!StringUtils.hasText(tagsString)) {
            return;
        }

        // 解析标签字符串（支持空格或#分隔）
        String[] tagNames = tagsString.split("[\\s#]+");

        for (String tagName : tagNames) {
            String trimmedTag = tagName.trim();
            if (StringUtils.hasText(trimmedTag) && !trimmedTag.isEmpty()) {
                try {
                    // 获取或创建标签
                    Tag tag = tagService.getOrCreateTag(trimmedTag);
                    // 增加标签热度
                    tagService.increaseHeat(tag.getId());
                } catch (Exception e) {
                    log.error("处理标签 " + trimmedTag + " 失败", e);
                }
            }
        }
    }

    @Override
    public Map<String, Object> extractTagsAndSummary(TagsExtractReq extractTagsRequest) {

        // 1. 清洗 HTML，提取纯文本
        String pureText = extractTagsRequest.getContent()
                .replaceAll("<[^>]*>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();

        // 2. 提取关键词
        List<String> tags = HanLP.extractKeyword(
                pureText,
                extractTagsRequest.getTagSize());

        // 3. 提取摘要
        List<String> summary = HanLP.extractSummary(
                pureText,
                extractTagsRequest.getSummarySize());

        // 4. 封装返回
        Map<String, Object> map = new HashMap<>();
        map.put("tags", tags);
        map.put("summary", summary);
        return map;
    }

    @Override
    public void likePost(String postId, String userId) {
        // 委托给 PostLikeService 处理点赞/取消点赞逻辑
        // 该方法会自动处理点赞状态切换并更新点赞数
        postLikeService.toggleLike(postId, userId);
    }

    @Override
    public void collectPost(String postId, String userId) {
        // 委托给 PostCollectService 处理收藏/取消收藏逻辑
        // 该方法会自动处理收藏状态切换并更新收藏数
        postCollectService.toggleCollect(postId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePost(com.campus.trend.campus_pulse.dto.request.PostUpdateReq request, String userId) {
        Post post = getById(request.getPostId());
        if (post == null) {
            throw new RuntimeException("帖子不存在");
        }
        if (!post.getUserId().equals(userId)) {
            throw new RuntimeException("无权修改该帖子");
        }

        boolean contentChanged = false;
        String title = request.getTitle();
        String content = request.getContent();

        // 1. 内容安全检查
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

        // 2. 如果内容变动，重新计算情感得分
        if (contentChanged) {
            double sentimentScore = sentimentAnalysisService
                    .analyzeSentiment(post.getTitle() + " " + post.getContent());
            post.setSentimentScore(BigDecimal.valueOf(sentimentScore));

            // 重新审核状态
            post.setAuditStatus("PENDING");
        }

        // 3. 其他字段
        if (request.getIsAnonymous() != null) {
            post.setIsAnonymous(request.getIsAnonymous());
        }
        if (StringUtils.hasText(request.getLocationName())) {
            post.setLocationName(request.getLocationName());
        }
        if (StringUtils.hasText(request.getCoverImage())) {
            post.setCoverImage(request.getCoverImage());
        }
        if (StringUtils.hasText(request.getTags())) {
            post.setTags(request.getTags());
            processPostTags(request.getTags());
        }

        post.setUpdateTime(LocalDateTime.now());

        // 图片处理 (简单实现: 如果传了就覆盖)
        if (StringUtils.hasText(request.getImages())) {
            try {
                List<String> images = objectMapper.readValue(request.getImages(), new TypeReference<List<String>>() {
                });
                post.setImages(images);
            } catch (Exception e) {
                // ignore or log
            }
        }

        this.updateById(post);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePost(String postId, String userId) {
        Post post = getById(postId);
        if (post == null) {
            throw new RuntimeException("帖子不存在");
        }
        // 校验权限 (作者或管理员，这里暂时只校验作者)
        // 实际场景中管理员权限通常在 Controller 层或 Spring Security 中校验，这里作为兜底
        if (!post.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除该帖子");
        }

        // 逻辑删除
        post.setStatus(0);
        post.setUpdateTime(LocalDateTime.now());
        this.updateById(post);

        // 同时可以考虑扣除用户贡献值等，或者清理浏览记录
        // userProfileService.addContribution(userId, -5);
    }

    @Override
    public IPage<Post> searchAllList(PostSearchReq postSearchRequest) {

        int page = postSearchRequest.getPage() != null ? postSearchRequest.getPage() : 1;
        int pageSize = postSearchRequest.getPageSize() != null ? postSearchRequest.getPageSize() : 10;
        Page<Post> pagePram = new Page<>(page, pageSize);

        LambdaQueryWrapper<Post> wrapper = Wrappers.lambdaQuery();

        // 如果帖子分类 ID 不为空则执行 Sql 查询
        wrapper.eq(postSearchRequest.getCategoryID() != null, Post::getCategoryId,
                postSearchRequest.getCategoryID());
        // 如果帖子用户 ID 不为空则执行 Sql 查询
        wrapper.eq(postSearchRequest.getUserID() != null, Post::getUserId, postSearchRequest.getUserID());
        // 如果帖子状态不为空则执行 Sql 查询
        wrapper.eq(postSearchRequest.getStatus() != null, Post::getStatus, postSearchRequest.getStatus());

        // 如果传入了 keyword，拼接 WHERE (title LIKE %?% OR content LIKE %?%)
        if (StringUtils.hasText(postSearchRequest.getKeyword())) {
            wrapper.and(w -> w.like(Post::getTitle, postSearchRequest.getKeyword())
                    .or()
                    .like(Post::getContent, postSearchRequest.getKeyword()));
        }

        // 如果传入了 tag，拼接 WHERE tags LIKE %tag%
        if (StringUtils.hasText(postSearchRequest.getTag())) {
            wrapper.like(Post::getTags, postSearchRequest.getTag());
        }

        // Filter by Liked
        if (StringUtils.hasText(postSearchRequest.getLikedBy())) {
            wrapper.inSql(Post::getId,
                    "SELECT post_id FROM sys_post_like WHERE user_id = '" + postSearchRequest.getLikedBy() + "'");
        }

        // Filter by Collected
        if (StringUtils.hasText(postSearchRequest.getCollectedBy())) {
            wrapper.inSql(Post::getId, "SELECT post_id FROM sys_post_collect WHERE user_id = '"
                    + postSearchRequest.getCollectedBy() + "'");
        }

        // 根据排序方式排序
        String orderBy = postSearchRequest.getOrderBy();
        if ("hot".equalsIgnoreCase(orderBy)) {
            wrapper.orderByDesc(Post::getHeatScore);
        } else {
            // 默认按最新排序
            wrapper.orderByDesc(Post::getCreateTime);
        }

        return this.page(pagePram, wrapper);
    }

    // =================== 新增：带作者信息的帖子查询 ===================

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
        // 1. 先查询帖子分页数据
        IPage<Post> postPage = searchAllList(postSearchRequest);
        List<Post> posts = postPage.getRecords();

        if (posts.isEmpty()) {
            return new Page<>(postPage.getCurrent(), postPage.getSize(), postPage.getTotal());
        }

        // 2. 批量收集 ID
        List<String> userIds = posts.stream().map(Post::getUserId).distinct().toList();
        List<String> categoryIds = posts.stream().map(Post::getCategoryId).distinct().toList();

        // 3. 批量查询用户和分类
        Map<String, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<User> users = userService.listByIds(userIds);
            for (User user : users) {
                userMap.put(user.getId(), user);
            }
        }

        Map<String, Category> categoryMap = new HashMap<>();
        if (!categoryIds.isEmpty()) {
            List<Category> categories = categoryService.listByIds(categoryIds);
            for (Category category : categories) {
                categoryMap.put(category.getId(), category);
            }
        }

        // 4. 转换每个帖子为 PostResp
        List<PostResp> responseList = new ArrayList<>();
        for (Post post : posts) {
            responseList.add(convertToPostResp(post, userMap, categoryMap));
        }

        // 5. 构造结果页
        Page<PostResp> responsePage = new Page<>(
                postPage.getCurrent(),
                postPage.getSize(),
                postPage.getTotal());
        responsePage.setRecords(responseList);

        return responsePage;
    }

    /**
     * 将 Post 转换为 PostResp（填充作者信息和趋势数据）
     * 优化版：支持传入预加载的 Map
     */
    private PostResp convertToPostResp(Post post, Map<String, User> userMap, Map<String, Category> categoryMap) {
        PostResp response = new PostResp();

        // 复制基本字段
        response.setId(post.getId());
        response.setUserId(post.getUserId());
        response.setCategoryId(post.getCategoryId());
        response.setTitle(post.getTitle());
        response.setContent(post.getContent());
        response.setCoverImage(post.getCoverImage());
        response.setImages(post.getImages());
        response.setTags(post.getTags());
        response.setIsAnonymous(post.getIsAnonymous());
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
        response.setIsLiked(post.getIsLiked());
        response.setIsCollected(post.getIsCollected());
        response.setSentimentScore(post.getSentimentScore());

        // ===== 填充作者信息 =====
        if (post.getIsAnonymous() != null && post.getIsAnonymous() == 1) {
            // 匿名帖子
            response.setAuthorName("匿名同学");
            response.setAuthorAvatar(null);
        } else {
            // 非匿名帖子
            User author = userMap != null ? userMap.get(post.getUserId()) : null;
            // 如果 userMap 为 null (兼容旧方法调用)，则尝试单独查询
            if (userMap == null) {
                 try {
                    author = userService.getById(post.getUserId());
                 } catch (Exception ignored) {}
            }

            if (author != null) {
                response.setAuthorName(author.getNickname() != null ? author.getNickname()
                        : "用户" + post.getUserId().substring(0, 6));
                response.setAuthorAvatar(author.getAvatar());
            } else {
                response.setAuthorName("未知用户");
                response.setAuthorAvatar(null);
            }
        }

        // ===== 填充分类名称 =====
        if (post.getCategoryId() != null) {
            Category category = categoryMap != null ? categoryMap.get(post.getCategoryId()) : null;
             if (categoryMap == null) {
                 try {
                    category = categoryService.getById(post.getCategoryId());
                 } catch (Exception ignored) {}
            }

            if (category != null) {
                response.setCategoryName(category.getName());
            }
        }

        // ===== 填充情感标签 =====
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

        // ===== 填充趋势等级 =====
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

        // ===== 填充 AI 摘要 =====
        fillSummary(response, post);

        return response;
    }

    private PostResp convertToPostResp(Post post) {
        return convertToPostResp(post, null, null);
    }

    private void fillSummary(PostResp response, Post post) {
        if (StringUtils.hasText(post.getContent())) {
            try {
                String pureText = post.getContent()
                        .replaceAll("<[^>]*>", "")
                        .replaceAll("&nbsp;", " ")
                        .replaceAll("\\s+", " ")
                        .trim();
                if (pureText.length() > 20) {
                    List<String> summaryList = HanLP.extractSummary(pureText, 1);
                    if (summaryList != null && !summaryList.isEmpty()) {
                        response.setSummary(summaryList.get(0));
                    } else {
                        response.setSummary(pureText.length() > 100 ? pureText.substring(0, 100) + "..." : pureText);
                    }
                } else {
                    response.setSummary(pureText);
                }
            } catch (Exception e) {
                log.warn("AI 摘要生成失败: {}", e.getMessage());
                String pureText = post.getContent().replaceAll("<[^>]*>", "").trim();
                response.setSummary(pureText.length() > 100 ? pureText.substring(0, 100) + "..." : pureText);
            }
        }
    }

    /******************* 内置方法 ********************/
    // 观看量加 1
    void viewAdd(String postId) {
        Post sysPost = lambdaQuery().eq(Post::getId, postId).one();
        if (sysPost != null) {
            sysPost.setViewCount(sysPost.getViewCount() + 1);
            this.saveOrUpdate(sysPost);
        }
    }

}
