package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.trend.campus_pulse.dto.response.PostRecommendResp;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.Tag;
import com.campus.trend.campus_pulse.entity.UserTagRelation;
import com.campus.trend.campus_pulse.mapper.CategoryMapper;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.mapper.UserMapper;
import com.campus.trend.campus_pulse.service.CollaborativeFilteringService;
import com.campus.trend.campus_pulse.service.PostRecommendService;
import com.campus.trend.campus_pulse.service.TagService;
import com.campus.trend.campus_pulse.service.UserTagRelationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PostRecommendServiceImpl implements PostRecommendService {

    private final UserTagRelationService userTagRelationService;
    private final TagService tagService;
    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final CategoryMapper categoryMapper;
    private final CollaborativeFilteringService collaborativeFilteringService;
    private final StringRedisTemplate redisTemplate;

    @Autowired
    public PostRecommendServiceImpl(UserTagRelationService userTagRelationService,
            TagService tagService,
            PostMapper postMapper,
            UserMapper userMapper,
            CategoryMapper categoryMapper,
            CollaborativeFilteringService collaborativeFilteringService,
            StringRedisTemplate redisTemplate) {
        this.userTagRelationService = userTagRelationService;
        this.tagService = tagService;
        this.postMapper = postMapper;
        this.userMapper = userMapper;
        this.categoryMapper = categoryMapper;
        this.collaborativeFilteringService = collaborativeFilteringService;
        this.redisTemplate = redisTemplate;
    }

    private static final String RECOMMEND_CACHE_KEY = "user:recommend:";

    @Override
    public List<PostRecommendResp> getPostDetailRecommendations(String postId, String userId, int limit) {
        Post currentPost = postMapper.selectById(postId);
        if (currentPost == null) return Collections.emptyList();

        List<Post> recommendations = new ArrayList<>();
        Set<String> recommendedIds = new HashSet<>();
        recommendedIds.add(postId);

        // 1. 同专业推荐 (如果登录且作者/用户有专业信息)
        if (userId != null) {
            var currentUser = userMapper.selectById(userId);
            if (currentUser != null && currentUser.getMajor() != null) {
                List<Post> majorPosts = postMapper.selectList(new LambdaQueryWrapper<Post>()
                        .eq(Post::getStatus, 1)
                        .ne(Post::getId, postId)
                        .last("AND user_id IN (SELECT id FROM sys_user WHERE major = '" + currentUser.getMajor() + "') LIMIT " + limit));
                for (Post p : majorPosts) {
                    if (recommendedIds.add(p.getId())) {
                        recommendations.add(p);
                    }
                }
            }
        }

        // 2. 相同分类或标签 (如果还没满)
        if (recommendations.size() < limit) {
            List<Post> catPosts = postMapper.selectList(new LambdaQueryWrapper<Post>()
                    .eq(Post::getStatus, 1)
                    .eq(Post::getCategoryId, currentPost.getCategoryId())
                    .ne(Post::getId, postId)
                    .last("LIMIT " + limit));
            for (Post p : catPosts) {
                if (recommendedIds.add(p.getId())) {
                    recommendations.add(p);
                }
            }
        }

        // 3. 热度兜底
        if (recommendations.size() < limit) {
            List<Post> hotPosts = getHotPostsList(limit * 2);
            for (Post p : hotPosts) {
                if (recommendedIds.add(p.getId())) {
                    recommendations.add(p);
                }
            }
        }

        // 裁剪到 limit
        List<Post> resultPosts = recommendations.stream().limit(limit).collect(Collectors.toList());
        return convertToRecommendDTO(resultPosts, "相关内容推荐");
    }

    @Override
    public IPage<PostRecommendResp> getHybridRecommendations(String userId, int page, int pageSize) {
        // 1. 尝试从缓存获取
        String cacheKey = RECOMMEND_CACHE_KEY + (userId == null ? "anonymous" : userId);
        // 为了简化，这里演示逻辑，实际分页通常缓存全量列表或按页缓存
        
        List<PostRecommendResp> allRecommendations = new ArrayList<>();

        if (userId == null) {
            // 匿名用户：仅推荐热门
            List<Post> hotPosts = getHotPostsList(50);
            allRecommendations = convertToRecommendDTO(hotPosts, "校园热门推荐");
        } else {
            // 登录用户：混合推荐
            
            // A. 基于兴趣标签 (40%)
            List<Post> interestPosts = recommendPostsList(userId, 20);
            allRecommendations.addAll(convertToRecommendDTO(interestPosts, "基于你的兴趣标签"));

            // B. 协同过滤 (30%)
            List<Post> cfPosts = collaborativeFilteringService.recommendByUserBased(userId, 10);
            allRecommendations.addAll(convertToRecommendDTO(cfPosts, "志趣相投的同学也在看"));

            // C. 热门兜底 (30%)
            List<Post> hotPosts = getHotPostsList(20);
            allRecommendations.addAll(convertToRecommendDTO(hotPosts, "全校都在看"));
        }

        // 2. 去重 & 随机打乱 (增加新鲜感)
        Map<String, PostRecommendResp> uniqueMap = new LinkedHashMap<>();
        for (PostRecommendResp resp : allRecommendations) {
            uniqueMap.putIfAbsent(resp.getId(), resp);
        }
        
        List<PostRecommendResp> resultList = new ArrayList<>(uniqueMap.values());
        Collections.shuffle(resultList);

        // 3. 手动分页
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, resultList.size());
        
        List<PostRecommendResp> paginatedList = new ArrayList<>();
        if (start < resultList.size()) {
            paginatedList = resultList.subList(start, end);
        }

        Page<PostRecommendResp> resultPage = new Page<>(page, pageSize);
        resultPage.setRecords(paginatedList);
        resultPage.setTotal(resultList.size());

        return resultPage;
    }

    private List<PostRecommendResp> convertToRecommendDTO(List<Post> posts, String reason) {
        return posts.stream().map(post -> {
            PostRecommendResp dto = new PostRecommendResp();
            dto.setId(post.getId());
            dto.setTitle(post.getTitle());
            // 摘要逻辑：取内容前100字
            String content = post.getContent();
            dto.setSummary(content != null ? (content.length() > 100 ? content.substring(0, 100) + "..." : content) : "");
            
            // 关联查询作者信息
            var author = userMapper.selectById(post.getUserId());
            if (author != null) {
                dto.setAuthorName(author.getNickname());
                dto.setAuthorAvatar(author.getAvatar());
            } else {
                dto.setAuthorName("未知用户");
            }
            
            // 关联分类
            var cat = categoryMapper.selectById(post.getCategoryId());
            if (cat != null) {
                dto.setCategoryName(cat.getName());
            } else {
                dto.setCategoryName("默认分类");
            }

            // 标签处理
            if (StringUtils.hasText(post.getTags())) {
                dto.setTags(Arrays.asList(post.getTags().split("[\\s#]+")).stream().filter(t -> !t.isEmpty()).collect(Collectors.toList()));
            }

            dto.setViewCount(post.getViewCount());
            dto.setLikeCount(post.getLikeCount());
            dto.setCollectCount(post.getCollectCount());
            dto.setCommentCount(post.getCommentCount());
            dto.setCreateTime(post.getCreateTime().toString());
            dto.setRecommendReason(reason);
            dto.setIsLiked(post.getIsLiked());
            dto.setIsCollected(post.getIsCollected());
            return dto;
        }).collect(Collectors.toList());
    }

    private List<Post> recommendPostsList(String userId, int limit) {
        // 复用原有逻辑但返回 List
        List<UserTagRelation> userTags = userTagRelationService.lambdaQuery()
                .eq(UserTagRelation::getUserId, userId)
                .list();
        if (userTags.isEmpty()) return Collections.emptyList();

        Set<String> tagNames = userTags.stream()
                .map(rel -> tagService.getById(rel.getTagId()))
                .filter(Objects::nonNull)
                .map(Tag::getName)
                .collect(Collectors.toSet());

        return postMapper.selectList(null).stream()
                .filter(post -> post.getStatus() == 1 && containsAnyTag(post.getTags(), tagNames))
                .sorted((a, b) -> Double.compare(calculateScore(b, userTags), calculateScore(a, userTags)))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private List<Post> getHotPostsList(int limit) {
        return postMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Post>()
                .eq(Post::getStatus, 1)
                .orderByDesc(Post::getHeatScore)
                .last("LIMIT " + limit));
    }

    /**
     * 为用户推荐帖子（基于用户关注的标签）
     *
     * @param userId   用户ID
     * @param page     页码
     * @param pageSize 每页大小
     * @return 推荐的帖子列表
     */
    @Override
    public IPage<Post> recommendPosts(String userId, int page, int pageSize) {
        // 如果 userId 为 null（匿名用户），直接返回热门帖子
        if (userId == null) {
            log.info("匿名用户访问，返回热门帖子");
            return getHotPosts(page, pageSize);
        }

        // 1. 获取用户关注的标签
        List<UserTagRelation> userTags = userTagRelationService.lambdaQuery()
                .eq(UserTagRelation::getUserId, userId)
                .list();

        if (userTags.isEmpty()) {
            log.info("用户 [{}] 未关注任何标签，尝试协同过滤推荐或返回热门", userId);

            // 尝试 User-Based 协同过滤 (TODO: 目前实现为空，此处略过)
            // return collaborativeFilteringService.recommendByUserBased(userId, pageSize);

            // 降级：返回热门帖子
            return getHotPosts(page, pageSize);
        }

        // 2. 提取标签名称
        List<Tag> tags = userTags.stream()
                .map(rel -> tagService.getById(rel.getTagId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Set<String> tagNames = tags.stream()
                .map(Tag::getName)
                .collect(Collectors.toSet());

        // 3. 查询包含这些标签的帖子
        List<Post> candidatePosts = postMapper.selectList(null).stream()
                .filter(post -> post.getStatus() != null && post.getStatus() == 1) // 只要状态正常的
                .filter(post -> StringUtils.hasText(post.getTags())) // 有标签的
                .filter(post -> containsAnyTag(post.getTags(), tagNames)) // 包含用户关注的标签
                .collect(Collectors.toList());

        // 4. 计算相关度分数并排序
        List<PostWithScore> scoredPosts = candidatePosts.stream()
                .map(post -> {
                    double score = calculateScore(post, userTags);
                    return new PostWithScore(post, score);
                })
                .sorted((a, b) -> Double.compare(b.score, a.score)) // 降序
                .collect(Collectors.toList());

        // 5. 分页处理
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

        log.info("为用户 [{}] 推荐了 {} 个帖子（基于 {} 个标签）",
                userId, paginatedPosts.size(), tags.size());

        return resultPage;
    }

    /**
     * 计算帖子相关度分数
     *
     * @param post     帖子
     * @param userTags 用户关注的标签
     * @return 相关度分数
     */
    @Override
    public double calculateScore(Post post, List<UserTagRelation> userTags) {
        if (post.getTags() == null || post.getTags().isEmpty()) {
            return 0.0;
        }

        // 1. 标签匹配度分数
        double tagScore = 0.0;
        String[] postTags = post.getTags().split("[\\s#]+");
        Set<String> postTagSet = Arrays.stream(postTags)
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toSet());

        for (UserTagRelation userTag : userTags) {
            Tag tag = tagService.getById(userTag.getTagId());
            if (tag != null && postTagSet.contains(tag.getName())) {
                // 用户兴趣权重 × 标签匹配度(1.0)
                tagScore += userTag.getScore().doubleValue();
            }
        }

        // 2. 时间因子（最近发布的帖子权重更高）
        double timeFactor = calculateTimeFactor(post.getCreateTime());

        // 3. 互动因子（点赞/评论/收藏越多权重越高）
        double engagementFactor = calculateEngagementFactor(post);

        // 综合分数 = 标签分数 × 时间因子 × 互动因子
        double finalScore = tagScore * timeFactor * engagementFactor;

        return finalScore;
    }

    /**
     * 获取用户可能感兴趣的标签
     *
     * @param userId 用户ID
     * @param limit  返回数量
     * @return 推荐的标签列表
     */
    @Override
    public List<Tag> recommendTags(String userId, int limit) {
        // 如果 userId 为 null（匿名用户），直接返回热门标签
        if (userId == null) {
            log.info("匿名用户访问，返回热门标签");
            List<Tag> hotTags = tagService.getHotTags(limit);
            return hotTags;
        }

        // 获取用户已关注的标签
        List<Tag> followedTags = userTagRelationService.getUserFollowingTags(userId);
        Set<Long> followedTagIds = followedTags.stream()
                .map(Tag::getId)
                .collect(Collectors.toSet());

        // 获取热门标签并过滤已关注的
        List<Tag> hotTags = tagService.getHotTags(limit * 2);
        List<Tag> recommendations = hotTags.stream()
                .filter(tag -> !followedTagIds.contains(tag.getId()))
                .limit(limit)
                .collect(Collectors.toList());

        log.info("为用户 [{}] 推荐了 {} 个标签", userId, recommendations.size());

        return recommendations;
    }

    /**
     * 获取热门帖子
     */
    private IPage<Post> getHotPosts(int page, int pageSize) {
        Page<Post> queryPage = new Page<>(page, pageSize);
        return postMapper.selectPage(queryPage,
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Post>()
                        .eq(Post::getStatus, 1)
                        .orderByDesc(Post::getHeatScore)
                        .orderByDesc(Post::getCreateTime));
    }

    /**
     * 检查帖子标签是否包含用户关注的任一标签
     */
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

    /**
     * 计算时间因子（越新的帖子分数越高）
     */
    private double calculateTimeFactor(LocalDateTime createTime) {
        if (createTime == null) {
            return 0.5;
        }

        Duration age = Duration.between(createTime, LocalDateTime.now());
        long ageInDays = age.toDays();

        // 时间衰减公式: 1 / (1 + days / 7)^1.2
        // 0天=1.0, 7天=0.5, 30天=0.16
        return 1.0 / Math.pow(1 + (ageInDays / 7.0), 1.2);
    }

    /**
     * 计算互动因子（点赞/评论/收藏越多分数越高）
     */
    private double calculateEngagementFactor(Post post) {
        int likes = post.getLikeCount() != null ? post.getLikeCount() : 0;
        int comments = post.getCommentCount() != null ? post.getCommentCount() : 0;
        int collects = post.getCollectCount() != null ? post.getCollectCount() : 0;

        // 加权计算: 评论权重 > 收藏 > 点赞
        double engagementScore = likes + comments * 2 + collects * 1.5;

        // 归一化到1.0-2.0之间
        return 1.0 + Math.min(engagementScore / 100.0, 1.0);
    }

    /**
     * 内部类：帖子+分数
     */
    private static class PostWithScore {
        Post post;
        double score;

        PostWithScore(Post post, double score) {
            this.post = post;
            this.score = score;
        }
    }
}
