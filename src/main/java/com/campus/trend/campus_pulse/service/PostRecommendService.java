package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.trend.campus_pulse.entity.SysPost;
import com.campus.trend.campus_pulse.entity.SysTag;
import com.campus.trend.campus_pulse.entity.SysUserTagRelation;

import java.util.List;

public interface PostRecommendService {

    /**
     * 为用户推荐帖子（基于用户关注的标签）
     *
     * @param userId   用户ID
     * @param page     页码
     * @param pageSize 每页大小
     * @return 推荐的帖子列表
     */
    IPage<SysPost> recommendPosts(String userId, int page, int pageSize);

    /**
     * 计算帖子相关度分数
     *
     * @param post     帖子
     * @param userTags 用户关注的标签
     * @return 相关度分数
     */
    double calculateScore(SysPost post, List<SysUserTagRelation> userTags);

    /**
     * 获取用户可能感兴趣的标签
     *
     * @param userId 用户ID
     * @param limit  返回数量
     * @return 推荐的标签列表
     */
    List<SysTag> recommendTags(String userId, int limit);
}
