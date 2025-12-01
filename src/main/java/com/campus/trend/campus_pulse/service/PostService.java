package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.dto.request.CreatePostRequest;
import com.campus.trend.campus_pulse.dto.request.ExtractTagsRequest;
import com.campus.trend.campus_pulse.entity.SysPost;

import java.util.List;

public interface PostService extends IService<SysPost> {

    SysPost searchByPostId(String postId);

    void createPost(CreatePostRequest createPostRequest,String userID);

    List<String> extractTags(ExtractTagsRequest extractTagsRequest);

}
