package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.request.PostCreateReq;
import com.campus.trend.campus_pulse.dto.request.PostDraftReq;
import com.campus.trend.campus_pulse.dto.request.TagsExtractReq;
import com.campus.trend.campus_pulse.dto.request.PostSearchReq;
import com.campus.trend.campus_pulse.security.AuthUser;
import com.campus.trend.campus_pulse.service.PostService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * Song：帖子控制器
 * Song：提供帖子的查询、创建、更新、删除、点赞、收藏、置顶等接口
 */
@RestController
@RequestMapping("/post")
@Slf4j
public class PostController {

    private final PostService postService;

    @Autowired
    public PostController(PostService postService) {
        this.postService = postService;
    }

    /* Song：说明 */
    @GetMapping("/{id}")
    public Result<?> searchByPostId(@PathVariable String id) {
        return Result.success(postService.getPostWithAuthor(id));
    }

    /* Song：创建新帖子 */
    @PostMapping("/create-post")
    public Result<?> createPost(@Valid @RequestBody PostCreateReq createPostRequest) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        postService.createPost(createPostRequest, authUser.getUser().getId());
        return Result.success();
    }

    @PostMapping("/save-draft")
    public Result<?> saveDraft(@RequestBody PostDraftReq draftRequest) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        return Result.success(postService.saveDraft(draftRequest, authUser.getUser().getId()));
    }

    @PostMapping("/extract-tags")
    public Result<?> extractTags(@Valid @RequestBody TagsExtractReq extractTagsRequest) {
        String userId = SecurityUtils.getCurrentUserId();
        log.info("请求 AI 提取标签: userId={}, tagSize={}, summarySize={}, contentLength={}",
                userId,
                extractTagsRequest.getTagSize(),
                extractTagsRequest.getSummarySize(),
                extractTagsRequest.getContent() != null ? extractTagsRequest.getContent().length() : 0);
        return Result.success(postService.extractTagsAndSummary(extractTagsRequest));
    }

    /**
     * Song：重新生成帖子摘要（作者/管理员/版主）
     */
    @PostMapping("/{id}/summary/regenerate")
    public Result<?> regenerateSummary(@PathVariable String id) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        String summary = postService.regenerateSummary(id, authUser.getUser().getId());
        return Result.success(summary);
    }

    @GetMapping("/{id}/versions")
    public Result<?> listVersionHistory(@PathVariable String id) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        return Result.success(postService.listVersionHistory(id, authUser.getUser().getId()));
    }

    /* Song：分页搜索帖子列表（含作者信息） */
    @PostMapping("/search-lists")
    public Result<?> searchList(@RequestBody PostSearchReq postSearchRequest) {
        return Result.success(postService.searchPostsWithAuthor(postSearchRequest));
    }

    /* Song：版务内容列表（管理员/板块版主） */
    @PostMapping("/moderation-list")
    public Result<?> moderationList(@RequestBody PostSearchReq postSearchRequest) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        return Result.success(postService.searchModerationPosts(postSearchRequest, authUser.getUser().getId()));
    }

    /* Song：点赞/取消点赞 */
    @PostMapping("/{id}/like")
    public Result<?> likePost(@PathVariable String id) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        postService.likePost(id, authUser.getUser().getId());
        return Result.success();
    }

    /* Song：收藏/取消收藏 */
    @PostMapping("/{id}/collect")
    public Result<?> collectPost(@PathVariable String id) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        postService.collectPost(id, authUser.getUser().getId());
        return Result.success();
    }

    /* Song：置顶/取消置顶帖子（限管理员） - 旧接口，保持兼容 */
    @PostMapping("/{id}/pin")
    public Result<?> pinPost(@PathVariable String id) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        postService.pinPost(id, authUser.getUser().getId());
        return Result.success();
    }

    /* Song：全局置顶/取消全局置顶（限管理员） */
    @PostMapping("/{id}/global-pin")
    public Result<?> setGlobalPin(
            @PathVariable String id,
            @RequestBody(required = false) java.util.Map<String, Object> body) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        Integer pinOrder = body != null && body.containsKey("pinOrder")
            ? (Integer) body.get("pinOrder") : null;
        String expireAt = body != null && body.containsKey("expireAt")
            ? (String) body.get("expireAt") : null;
        postService.setGlobalPin(id, authUser.getUser().getId(), pinOrder, expireAt);
        return Result.success();
    }

    /* Song：板块置顶/取消板块置顶（限管理员/版主） */
    @PostMapping("/{id}/category-pin")
    public Result<?> setCategoryPin(
            @PathVariable String id,
            @RequestBody(required = false) java.util.Map<String, Object> body) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        Integer pinOrder = body != null && body.containsKey("pinOrder")
            ? (Integer) body.get("pinOrder") : null;
        String expireAt = body != null && body.containsKey("expireAt")
            ? (String) body.get("expireAt") : null;
        postService.setCategoryPin(id, authUser.getUser().getId(), pinOrder, expireAt);
        return Result.success();
    }

    /* Song：获取置顶帖子列表 */
    @GetMapping("/pinned")
    public Result<?> getPinnedPosts(@RequestParam(required = false) Long sectionId) {
        return Result.success(postService.getPinnedPosts(sectionId));
    }

    /* Song：加精/取消加精帖子（限管理员/版主） */
    @PostMapping("/{id}/feature")
    public Result<?> featurePost(@PathVariable String id) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        postService.featurePost(id, authUser.getUser().getId());
        return Result.success();
    }

    /* Song：更新帖子内容（管理员或作者本人） */
    @PostMapping("/update-post")
    public Result<?> updatePost(
            @Valid @RequestBody com.campus.trend.campus_pulse.dto.request.PostUpdateReq updatePostRequest) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        postService.updatePost(updatePostRequest, authUser.getUser().getId());
        return Result.success();
    }

    /* Song：删除帖子（管理员或作者本人） */
    @DeleteMapping("/{id}")
    public Result<?> deletePost(@PathVariable String id) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        postService.deletePost(id, authUser.getUser().getId());
        return Result.success();
    }

    /* 恢复软删除帖子（作者/管理员/版主，删除后7天内） */
    @PostMapping("/{id}/restore")
    public Result<?> restoreDeletedPost(@PathVariable String id) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        postService.restoreDeletedPost(id, authUser.getUser().getId());
        return Result.success();
    }

    /* 打回帖子（管理员/版主）*/
    @PostMapping("/{id}/reject")
    public Result<?> rejectPost(@PathVariable String id,
                                @RequestBody(required = false) java.util.Map<String, String> body) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        postService.rejectPost(id, reason, authUser.getUser().getId());
        return Result.success();
    }

    /* 通过审核（管理员/版主）*/
    @PostMapping("/{id}/approve")
    public Result<?> approvePost(@PathVariable String id) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        postService.approvePost(id, authUser.getUser().getId());
        return Result.success();
    }
}
