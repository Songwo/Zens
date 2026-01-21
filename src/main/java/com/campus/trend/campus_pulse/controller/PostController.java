package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.request.PostCreateReq;
import com.campus.trend.campus_pulse.dto.request.TagsExtractReq;
import com.campus.trend.campus_pulse.dto.request.PostSearchReq;
import com.campus.trend.campus_pulse.security.AuthUser;
import com.campus.trend.campus_pulse.service.PostService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/post")
public class PostController {

    private final PostService postService;

    @Autowired
    public PostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping("/{id}")
    public Result<?> searchByPostId(@PathVariable String id) {
        return Result.success(postService.getPostWithAuthor(id));
    }

    @PostMapping("/create-post")
    public Result<?> createPost(@Valid @RequestBody PostCreateReq createPostRequest) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        postService.createPost(createPostRequest, authUser.getUser().getId());
        return Result.success();
    }

    @PostMapping("/extract-tags")
    public Result<?> extractTags(@Valid @RequestBody TagsExtractReq extractTagsRequest) {
        return Result.success(postService.extractTagsAndSummary(extractTagsRequest));
    }

    @PostMapping("/search-lists")
    public Result<?> searchList(@RequestBody PostSearchReq postSearchRequest) {
        return Result.success(postService.searchPostsWithAuthor(postSearchRequest));
    }

    @PostMapping("/{id}/like")
    public Result<?> likePost(@PathVariable String id) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        postService.likePost(id, authUser.getUser().getId());
        return Result.success();
    }

    @PostMapping("/{id}/collect")
    public Result<?> collectPost(@PathVariable String id) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        postService.collectPost(id, authUser.getUser().getId());
        return Result.success();
    }

    @PostMapping("/update-post")
    public Result<?> updatePost(
            @RequestBody com.campus.trend.campus_pulse.dto.request.PostUpdateReq updatePostRequest) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        postService.updatePost(updatePostRequest, authUser.getUser().getId());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<?> deletePost(@PathVariable String id) {
        AuthUser authUser = SecurityUtils.getAuthenticatedUser();
        postService.deletePost(id, authUser.getUser().getId());
        return Result.success();
    }
}
