package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.dto.request.CreatePostRequest;
import com.campus.trend.campus_pulse.dto.request.ExtractTagsRequest;
import com.campus.trend.campus_pulse.entity.SysPost;
import com.campus.trend.campus_pulse.mapper.SysPostMapper;
import com.campus.trend.campus_pulse.service.PostService;
import com.campus.trend.campus_pulse.utils.GenerateIDUtil;
import com.hankcs.hanlp.HanLP;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PostServiceImpl extends ServiceImpl<SysPostMapper, SysPost> implements PostService {

    @Override
    public SysPost searchByPostId(String postId) {
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

}
