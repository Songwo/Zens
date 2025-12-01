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
import com.campus.trend.campus_pulse.mapper.SysPostMapper;
import com.campus.trend.campus_pulse.service.PostService;
import com.campus.trend.campus_pulse.utils.GenerateIDUtil;
import com.hankcs.hanlp.HanLP;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PostServiceImpl extends ServiceImpl<SysPostMapper, SysPost> implements PostService {

    @Override
    public SysPost searchByPostId(String postId) {
        viewAdd(postId);
        return getById(postId);
    }

    @Override
    public void createPost(CreatePostRequest createPostRequest,String userId) {
        // TODO 1.检查 category_id 的有效性
        // 2.构造帖子
        String postID = GenerateIDUtil.genId("POST");

        SysPost sysPost = new SysPost();
        sysPost.setId(postID);
        sysPost.setUserId(userId);
        sysPost.setCategoryId(createPostRequest.getCategoryID());
        sysPost.setTitle(createPostRequest.getTitle());
        sysPost.setContent(createPostRequest.getContent());
        sysPost.setTags(createPostRequest.getTags());
        sysPost.setImages(createPostRequest.getImages());
        sysPost.setUpdateTime(LocalDateTime.now());
        // 3.保存
        this.save(sysPost);
    }

    @Override
    public List<String> extractTags(ExtractTagsRequest extractTagsRequest) {
        String pureText = extractTagsRequest.getContent().replaceAll("<[^>]*>", "");
        return HanLP.extractKeyword(pureText, extractTagsRequest.getSize());
    }

    @Override
    public void likePost(String postId) {
        // TODO待实现用户加入喜欢帖子列表
        SysPost sysPost = getById(postId);
        sysPost.setViewCount(sysPost.getLikeCount() + 1);
        this.save(sysPost);
    }

    @Override
    public IPage<SysPost> searchAllList(PostSearchRequest postSearchRequest) {

        Page<SysPost> pagePram = new Page<>(postSearchRequest.getPage(),postSearchRequest.getPageSize());

        LambdaQueryWrapper<SysPost> wrapper = Wrappers.lambdaQuery();

        // 如果帖子分类 ID 不为空则执行 Sql 查询
        wrapper.eq(postSearchRequest.getCategoryID() != null, SysPost::getCategoryId, postSearchRequest.getCategoryID());
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

        return this.page(pagePram,wrapper);
    }

    /******************* 内置方法 ********************/
    // 观看量加 1
    void viewAdd(String postId) {
        SysPost sysPost = lambdaQuery().eq(SysPost::getId,postId).one();
        sysPost.setViewCount(sysPost.getViewCount() + 1);
        this.save(sysPost);
    }

}
