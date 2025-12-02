package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.Result;
import com.campus.trend.campus_pulse.dto.request.CreatePostRequest;
import com.campus.trend.campus_pulse.dto.request.ExtractTagsRequest;
import com.campus.trend.campus_pulse.dto.request.PostSearchRequest;
import com.campus.trend.campus_pulse.security.AuthSysUser;
import com.campus.trend.campus_pulse.service.PostService;
import com.campus.trend.campus_pulse.utils.GetUserDetail;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sys-post")
public class PostController {

    private final PostService postService;

    @Autowired
    public PostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping("/{id}")
    public Result<?> searchByPostId(@PathVariable String id) {
        return Result.success(postService.searchByPostId(id));
    }

    @PostMapping("/create-post")
    public Result<?> createPost(@Valid @RequestBody CreatePostRequest createPostRequest) {
        AuthSysUser authSysUser = GetUserDetail.getAuthenticatedUser();
        postService.createPost(createPostRequest,authSysUser.getSysUser().getId());
        return Result.success();
    }

    @GetMapping("/extract-tags")
    public Result<?> extractTags(@Valid @RequestBody ExtractTagsRequest extractTagsRequest){
        return Result.success(postService.extractTagsAndSummary(extractTagsRequest));
    }

    @PostMapping("/search-lists")
    public Result<?> searchList(@RequestBody PostSearchRequest postSearchRequest){
        return Result.success(postService.searchAllList(postSearchRequest));
    }


}
