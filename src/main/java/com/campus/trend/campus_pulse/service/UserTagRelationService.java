package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.entity.SysTag;
import com.campus.trend.campus_pulse.entity.SysUserTagRelation;

import java.math.BigDecimal;
import java.util.List;

public interface UserTagRelationService extends IService<SysUserTagRelation> {

    /**
     * 关注标签
     *
     * @param userId 用户ID
     * @param tagId  标签ID
     * @param score  兴趣权重（1.0-5.0）
     * @return true-关注成功, false-已经关注过
     */
    boolean followTag(String userId, Long tagId, BigDecimal score);

    /**
     * 取消关注标签
     *
     * @param userId 用户ID
     * @param tagId  标签ID
     * @return true-取消成功, false-未关注过
     */
    boolean unfollowTag(String userId, Long tagId);

    /**
     * 判断是否关注
     *
     * @param userId 用户ID
     * @param tagId  标签ID
     * @return true-已关注, false-未关注
     */
    boolean isFollowing(String userId, Long tagId);

    /**
     * 获取用户关注的标签
     *
     * @param userId 用户ID
     * @return 标签列表
     */
    List<SysTag> getUserFollowingTags(String userId);

    /**
     * 更新兴趣权重
     *
     * @param userId 用户ID
     * @param tagId  标签ID
     * @param score  新的权重分数
     * @return true-更新成功, false-未关注该标签
     */
    boolean updateScore(String userId, Long tagId, BigDecimal score);

    /**
     * 切换关注状态
     *
     * @param userId 用户ID
     * @param tagId  标签ID
     * @param score  兴趣权重（默认3.0）
     * @return true-已关注, false-已取消关注
     */
    boolean toggleFollow(String userId, Long tagId, BigDecimal score);
}
