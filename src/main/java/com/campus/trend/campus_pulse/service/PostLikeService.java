package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.PostLike;

public interface PostLikeService extends IService<PostLike> {

    /**
     * Song：判断用户是否已点赞该帖子
     *
     */
    boolean isLike(String postId, String userId);

    IPage<Post> getPostLikeWithPage(String userId, int page, int pageSize);

    /**
     * Song：用户点赞帖子
     *
     */
    boolean likePost(String postId, String userId);

    /**
     * Song：用户取消点赞帖子
     *
     */
    boolean unlikePost(String postId, String userId);

    /**
     * Song：切换点赞状态（点赞/取消点赞）
     *
     */
    boolean toggleLike(String postId, String userId);

}
