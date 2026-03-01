package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.dto.request.PostCreateReq;
import com.campus.trend.campus_pulse.dto.request.TagsExtractReq;
import com.campus.trend.campus_pulse.dto.request.PostSearchReq;
import com.campus.trend.campus_pulse.dto.request.PostUpdateReq;
import com.campus.trend.campus_pulse.dto.response.PostResp;
import com.campus.trend.campus_pulse.entity.Post;

import java.util.Map;

public interface PostService extends IService<Post> {

    Post searchByPostId(String postId);

    /**
     * Song：获取单个帖子详情（包含作者信息和趋势数据）
     */
    PostResp getPostWithAuthor(String postId);

    /**
     * Song：分页搜索帖子列表（包含作者信息和趋势数据）
     */
    IPage<PostResp> searchPostsWithAuthor(PostSearchReq postSearchRequest);

    void createPost(PostCreateReq createPostRequest, String userID);

    Map<String, Object> extractTagsAndSummary(TagsExtractReq extractTagsRequest);

    /**
     * Song：重新生成帖子摘要
     *
     * Song：说明
     */
    String regenerateSummary(String postId, String userId);

    void likePost(String postId, String userId);

    void collectPost(String postId, String userId);

    IPage<Post> searchAllList(PostSearchReq postSearchRequest);

    void updatePost(PostUpdateReq request, String userId);

    void deletePost(String postId, String userId);

    void pinPost(String postId, String userId);

    void setGlobalPin(String postId, String userId, Integer pinOrder, String expireAt);

    void setCategoryPin(String postId, String userId, Integer pinOrder, String expireAt);

    java.util.List<com.campus.trend.campus_pulse.dto.response.PostResp> getPinnedPosts(Long sectionId);

    void featurePost(String postId, String userId);

    // Song：待办 待实现列表
    // Song：1.帖子观看量 2.查询当前用户处于审核中的帖子 ✔
    // Song：3.查询全部帖子状态（简单） 4.模糊查询帖子内容或者题目 ✔

}
