package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.entity.PostSeries;

import java.util.List;

/**
 * 帖子系列服务接口
 */
public interface PostSeriesService extends IService<PostSeries> {

    /**
     * 创建帖子系列
     * @param title 标题
     * @param description 描述
     * @param userId 创建者ID
     * @return 系列ID
     */
    Long createSeries(String title, String description, String userId);

    /**
     * 添加帖子到系列
     * @param seriesId 系列ID
     * @param postId 帖子ID
     * @param userId 操作者ID
     */
    void addPostToSeries(Long seriesId, String postId, String userId);

    /**
     * 从系列中移除帖子
     * @param seriesId 系列ID
     * @param postId 帖子ID
     * @param userId 操作者ID
     */
    void removePostFromSeries(Long seriesId, String postId, String userId);

    /**
     * 获取系列下的所有帖子
     * @param seriesId 系列ID
     * @return 帖子列表
     */
    List<Object> getSeriesPosts(Long seriesId);

    /**
     * 获取用户创建的系列
     * @param userId 用户ID
     * @return 系列列表
     */
    List<PostSeries> getUserSeries(String userId);

    /**
     * 更新系列排序
     * @param seriesId 系列ID
     * @param postId 帖子ID
     * @param newOrderIndex 新排序
     * @param userId 操作者ID
     */
    void updateSeriesOrder(Long seriesId, String postId, Integer newOrderIndex, String userId);
}
