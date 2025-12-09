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
import com.campus.trend.campus_pulse.entity.SysPostCollect;
import com.campus.trend.campus_pulse.mapper.SysPostCollectMapper;
import com.campus.trend.campus_pulse.mapper.SysPostMapper;
import com.campus.trend.campus_pulse.service.PostLikeService;
import com.campus.trend.campus_pulse.service.PostService;
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
    private final SysPostCollectMapper sysPostCollectMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public SysPost searchByPostId(String postId) {
        viewAdd(postId);
        return getById(postId);
    }

    @Override
    public void createPost(CreatePostRequest createPostRequest, String userId) {
        // 1.检查 category_id 的有效性 (TODO)

        // 2.构造帖子
        String postID = GenerateIDUtil.genId("POST");

        SysPost sysPost = new SysPost();
        sysPost.setId(postID);
        sysPost.setUserId(userId);
        sysPost.setCategoryId(createPostRequest.getCategoryID());
        sysPost.setTitle(createPostRequest.getTitle());
        sysPost.setContent(createPostRequest.getContent());
        sysPost.setTags(createPostRequest.getTags());
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

        // 3.保存
        this.save(sysPost);
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
    @Transactional(rollbackFor = Exception.class)
    public void collectPost(String postId, String userId) {
        // 1. 查询是否已经收藏
        SysPostCollect exist = sysPostCollectMapper.selectOne(Wrappers.<SysPostCollect>lambdaQuery()
                .eq(SysPostCollect::getPostId, postId)
                .eq(SysPostCollect::getUserId, userId));

        SysPost post = getById(postId);
        if (post == null) {
            throw new RuntimeException("帖子不存在");
        }

        if (exist != null) {
            // 取消收藏
            sysPostCollectMapper.deleteById(exist.getId());
            post.setCollectCount(Math.max(0, post.getCollectCount() - 1));
        } else {
            // 收藏
            SysPostCollect collect = new SysPostCollect();
            collect.setPostId(postId);
            collect.setUserId(userId);
            collect.setCreateTime(LocalDateTime.now());
            sysPostCollectMapper.insert(collect);
            post.setCollectCount(post.getCollectCount() + 1);
        }
        updateById(post);
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
