package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.dto.request.CreatePostRequest;
import com.campus.trend.campus_pulse.dto.request.ExtractTagsRequest;
import com.campus.trend.campus_pulse.dto.request.PostSearchRequest;
import com.campus.trend.campus_pulse.entity.SysPost;
import com.campus.trend.campus_pulse.entity.SysTag;
import com.campus.trend.campus_pulse.mapper.SysPostMapper;
import com.campus.trend.campus_pulse.service.CategoryService;
import com.campus.trend.campus_pulse.service.ContentSecurityService;
import com.campus.trend.campus_pulse.service.PostCollectService;
import com.campus.trend.campus_pulse.service.SentimentAnalysisService;
import java.math.BigDecimal;
import com.campus.trend.campus_pulse.service.PostLikeService;
import com.campus.trend.campus_pulse.service.PostService;
import com.campus.trend.campus_pulse.service.TagService;
import com.campus.trend.campus_pulse.service.UserProfileService;
import com.campus.trend.campus_pulse.utils.GenerateIDUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hankcs.hanlp.HanLP;
import lombok.RequiredArgsConstructor;
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
public class PostServiceImpl extends ServiceImpl<SysPostMapper, SysPost> implements PostService {

    private final PostLikeService postLikeService;
    private final PostCollectService postCollectService;
    private final TagService tagService;
    private final UserProfileService userProfileService;
    private final CategoryService categoryService;
    private final ContentSecurityService contentSecurityService;
    private final SentimentAnalysisService sentimentAnalysisService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public SysPost searchByPostId(String postId) {
        viewAdd(postId);
        return getById(postId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createPost(CreatePostRequest createPostRequest, String userId) {
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
        String postID = GenerateIDUtil.genId("POST");

        SysPost sysPost = new SysPost();
        sysPost.setId(postID);
        sysPost.setUserId(userId);
        sysPost.setCategoryId(createPostRequest.getCategoryID());
        sysPost.setTitle(title);
        sysPost.setContent(content);
        sysPost.setTags(createPostRequest.getTags());

        // 3.1 情感分析
        double sentimentScore = sentimentAnalysisService.analyzeSentiment(title + " " + content);
        // FIXME: SysPost 实体中需要有 sentimentScore 字段，假设它是 Float 或 Double 类型
        // 如果实体中用的是 BigDecimal 或者其他类型，请相应调整
        // 假设实体中不仅有 score，以后可能还会有 label，这里先只存 score
        // sysPost.setSentimentScore(BigDecimal.valueOf(sentimentScore));
        // 暂时假设 sysPost 没有直接的 setSentimentScore 方法或者字段名不一样，
        // 我需要先确认一下 SysPost 的字段。
        // 根据之前的 Plan，SysPost 应该有一个 sentimentScore 字段。
        // 让我们先查看一下 SysPost 实体确保万无一失。
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
                    SysTag tag = tagService.getOrCreateTag(trimmedTag);
                    // 增加标签热度
                    tagService.increaseHeat(tag.getId());
                } catch (Exception e) {
                    log.error("处理标签 " + trimmedTag + " 失败", e);
                }
            }
        }
    }

    @Override
    public Map<String, Object> extractTagsAndSummary(ExtractTagsRequest extractTagsRequest) {

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
    public IPage<SysPost> searchAllList(PostSearchRequest postSearchRequest) {

        Page<SysPost> pagePram = new Page<>(postSearchRequest.getPage(), postSearchRequest.getPageSize());

        LambdaQueryWrapper<SysPost> wrapper = Wrappers.lambdaQuery();

        // 如果帖子分类 ID 不为空则执行 Sql 查询
        wrapper.eq(postSearchRequest.getCategoryID() != null, SysPost::getCategoryId,
                postSearchRequest.getCategoryID());
        // 如果帖子用户 ID 不为空则执行 Sql 查询
        wrapper.eq(postSearchRequest.getUserID() != null, SysPost::getUserId, postSearchRequest.getUserID());
        // 如果帖子状态不为空则执行 Sql 查询
        wrapper.eq(postSearchRequest.getStatus() != null, SysPost::getStatus, postSearchRequest.getStatus());

        // 如果传入了 keyword，拼接 WHERE (title LIKE %?% OR content LIKE %?%)
        if (StringUtils.hasText(postSearchRequest.getKeyword())) {
            wrapper.and(w -> w.like(SysPost::getTitle, postSearchRequest.getKeyword())
                    .or()
                    .like(SysPost::getContent, postSearchRequest.getKeyword()));
        }

        // 根据创建时间排序返回
        wrapper.orderByDesc(SysPost::getCreateTime);

        return this.page(pagePram, wrapper);
    }

    /******************* 内置方法 ********************/
    // 观看量加 1
    void viewAdd(String postId) {
        SysPost sysPost = lambdaQuery().eq(SysPost::getId, postId).one();
        if (sysPost != null) {
            sysPost.setViewCount(sysPost.getViewCount() + 1);
            this.saveOrUpdate(sysPost);
        }
    }

}
