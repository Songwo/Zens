package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.dto.request.CreatePostRequest;
import com.campus.trend.campus_pulse.dto.request.ExtractTagsRequest;
import com.campus.trend.campus_pulse.dto.request.PostSearchRequest;
import com.campus.trend.campus_pulse.entity.SysPost;

import java.util.List;

public interface PostService extends IService<SysPost> {

    SysPost searchByPostId(String postId);

    void createPost(CreatePostRequest createPostRequest,String userID);

    List<String> extractTags(ExtractTagsRequest extractTagsRequest);

    void likePost(String postId);

    IPage<SysPost> searchAllList(PostSearchRequest postSearchRequest);

    // TODO 待实现列表
    //  1.帖子观看量 2.查询当前用户处于审核中的帖子 ✔
    //  3.查询全部帖子状态（简单） 4.模糊查询帖子内容或者题目 ✔

}
