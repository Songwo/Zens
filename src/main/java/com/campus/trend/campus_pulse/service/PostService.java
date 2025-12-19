package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.dto.request.CreatePostRequest;
import com.campus.trend.campus_pulse.dto.request.ExtractTagsRequest;
import com.campus.trend.campus_pulse.dto.request.PostSearchRequest;
import com.campus.trend.campus_pulse.dto.request.UpdatePostRequest;
import com.campus.trend.campus_pulse.dto.response.PostResponse;
import com.campus.trend.campus_pulse.entity.SysPost;

import java.util.List;
import java.util.Map;

public interface PostService extends IService<SysPost> {

    SysPost searchByPostId(String postId);

    /**
     * 获取单个帖子详情（包含作者信息和趋势数据）
     */
    PostResponse getPostWithAuthor(String postId);

    /**
     * 分页搜索帖子列表（包含作者信息和趋势数据）
     */
    IPage<PostResponse> searchPostsWithAuthor(PostSearchRequest postSearchRequest);

    void createPost(CreatePostRequest createPostRequest, String userID);

    Map<String, Object> extractTagsAndSummary(ExtractTagsRequest extractTagsRequest);

    void likePost(String postId, String userId);

    void collectPost(String postId, String userId);

    IPage<SysPost> searchAllList(PostSearchRequest postSearchRequest);

    void updatePost(UpdatePostRequest request, String userId);

    void deletePost(String postId, String userId);

    // TODO 待实现列表
    // 1.帖子观看量 2.查询当前用户处于审核中的帖子 ✔
    // 3.查询全部帖子状态（简单） 4.模糊查询帖子内容或者题目 ✔

}
