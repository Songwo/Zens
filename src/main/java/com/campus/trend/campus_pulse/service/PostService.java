package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.dto.request.PostCreateReq;
import com.campus.trend.campus_pulse.dto.request.TagsExtractReq;
import com.campus.trend.campus_pulse.dto.request.PostSearchReq;
import com.campus.trend.campus_pulse.dto.request.PostUpdateReq;
import com.campus.trend.campus_pulse.dto.response.PostResp;
import com.campus.trend.campus_pulse.entity.Post;

import java.util.List;
import java.util.Map;

public interface PostService extends IService<Post> {

    Post searchByPostId(String postId);

    /**
     * 获取单个帖子详情（包含作者信息和趋势数据）
     */
    PostResp getPostWithAuthor(String postId);

    /**
     * 分页搜索帖子列表（包含作者信息和趋势数据）
     */
    IPage<PostResp> searchPostsWithAuthor(PostSearchReq postSearchRequest);

    void createPost(PostCreateReq createPostRequest, String userID);

    Map<String, Object> extractTagsAndSummary(TagsExtractReq extractTagsRequest);

    void likePost(String postId, String userId);

    void collectPost(String postId, String userId);

    IPage<Post> searchAllList(PostSearchReq postSearchRequest);

    void updatePost(PostUpdateReq request, String userId);

    void deletePost(String postId, String userId);

    // TODO 待实现列表
    // 1.帖子观看量 2.查询当前用户处于审核中的帖子 ✔
    // 3.查询全部帖子状态（简单） 4.模糊查询帖子内容或者题目 ✔

}
