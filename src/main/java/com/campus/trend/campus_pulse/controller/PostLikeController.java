package com.campus.trend.campus_pulse.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.trend.campus_pulse.common.Result;
import com.campus.trend.campus_pulse.entity.SysPost;
import com.campus.trend.campus_pulse.security.AuthSysUser;
import com.campus.trend.campus_pulse.service.PostLikeService;
import com.campus.trend.campus_pulse.utils.GetUserDetail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 帖子点赞控制器
 * 处理帖子点赞、取消点赞、查询点赞状态等功能
 */
@Slf4j
@RestController
@RequestMapping("/post-like")
public class PostLikeController {

    private final PostLikeService postLikeService;

    @Autowired
    public PostLikeController(PostLikeService postLikeService) {
        this.postLikeService = postLikeService;
    }

    /**
     * 切换点赞状态（如果未点赞则点赞，如果已点赞则取消）
     *
     * @param postId 帖子ID
     * @return 点赞状态结果
     */
    @PostMapping("/{postId}/toggle")
    public Result<?> toggleLike(@PathVariable String postId) {
        AuthSysUser authSysUser = GetUserDetail.getAuthenticatedUser();
        String userId = authSysUser.getSysUser().getId();

        boolean isLiked = postLikeService.toggleLike(postId, userId);

        Map<String, Object> resultData = new HashMap<>();
        resultData.put("isLiked", isLiked);
        resultData.put("message", isLiked ? "点赞成功" : "取消点赞成功");

        return Result.success(resultData);
    }

    /**
     * 点赞状态
     *
     * @param postId 帖子ID
     * @return 点赞状态
     */
    @GetMapping("/{postId}/status")
    public Result<?> checkLikeStatus(@PathVariable String postId) {
        AuthSysUser authSysUser = GetUserDetail.getAuthenticatedUser();
        String userId = authSysUser.getSysUser().getId();

        boolean isLiked = postLikeService.isLike(postId, userId);

        Map<String, Object> resultData = new HashMap<>();
        resultData.put("isLiked", isLiked);

        return Result.success(resultData);
    }

    /**
     * 根据用户ID获取该用户点赞的所有帖子（支持分页）
     *
     * @param page     页码（可选，默认1）
     * @param pageSize 每页大小（可选，默认20）
     * @return 用户点赞的帖子列表
     */
    @GetMapping("/user")
    public Result<?> getUserLikedPosts(
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "20") int pageSize) {
        AuthSysUser authSysUser = GetUserDetail.getAuthenticatedUser();
        IPage<SysPost> likedPostsPage = postLikeService.getPostLikeWithPage(authSysUser.getSysUser().getId(), page, pageSize);
        return Result.success(likedPostsPage);
    }
}
