package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.request.PostSearchReq;
import com.campus.trend.campus_pulse.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文档列表接口。当前项目底层内容实体复用帖子模型。
 */
@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final PostService postService;

    @PostMapping("/list")
    public Result<?> list(@RequestBody PostSearchReq request) {
        return Result.success(postService.searchPostsWithAuthor(request));
    }
}