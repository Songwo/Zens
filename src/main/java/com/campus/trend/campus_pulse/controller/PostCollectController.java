package com.campus.trend.campus_pulse.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.trend.campus_pulse.common.Result;
import com.campus.trend.campus_pulse.entity.SysPost;
import com.campus.trend.campus_pulse.security.AuthSysUser;
import com.campus.trend.campus_pulse.service.PostCollectService;
import com.campus.trend.campus_pulse.utils.GetUserDetail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 帖子收藏控制器
 * 处理帖子收藏、取消收藏、查询收藏状态等功能
 */
@Slf4j
@RestController
@RequestMapping("/post-collect")
public class PostCollectController {

    private final PostCollectService postCollectService;

    @Autowired
    public PostCollectController(PostCollectService postCollectService) {
        this.postCollectService = postCollectService;
    }

    /**
     * 切换收藏状态（如果未收藏则收藏，如果已收藏则取消）
     *
     * @param postId 帖子ID
     * @return 收藏状态结果
     */
    @PostMapping("/{postId}/toggle")
    public Result<?> toggleCollect(@PathVariable String postId) {
        AuthSysUser authSysUser = GetUserDetail.getAuthenticatedUser();
        String userId = authSysUser.getSysUser().getId();

        boolean isCollected = postCollectService.toggleCollect(postId, userId);

        Map<String, Object> resultData = new HashMap<>();
        resultData.put("isCollected", isCollected);
        resultData.put("message", isCollected ? "收藏成功" : "取消收藏成功");

        return Result.success(resultData);
    }

    /**
     * 检查当前用户是否收藏了指定帖子
     *
     * @param postId 帖子ID
     * @return 收藏状态
     */
    @GetMapping("/{postId}/status")
    public Result<?> checkCollectStatus(@PathVariable String postId) {
        AuthSysUser authSysUser = GetUserDetail.getAuthenticatedUser();
        String userId = authSysUser.getSysUser().getId();

        boolean isCollected = postCollectService.isCollect(postId, userId);

        Map<String, Object> resultData = new HashMap<>();
        resultData.put("isCollected", isCollected);

        return Result.success(resultData);
    }

    /**
     * 根据用户ID获取该用户收藏的所有帖子（支持分页）
     *
     * @param userId   用户ID
     * @param page     页码（可选，默认1）
     * @param pageSize 每页大小（可选，默认20）
     * @return 用户收藏的帖子列表
     */
    @GetMapping("/user/{userId}")
    public Result<?> getUserCollectedPosts(
            @PathVariable String userId,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "20") int pageSize) {
        IPage<SysPost> collectedPostsPage = postCollectService.getPostCollectWithPage(userId, page, pageSize);

        return Result.success(collectedPostsPage);
    }
}
