package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.request.ShortLinkCommentReq;
import com.campus.trend.campus_pulse.dto.response.ShortLinkCreateResp;
import com.campus.trend.campus_pulse.service.ShortLinkService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/short-link")
@RequiredArgsConstructor
public class ShortLinkController {

    private final ShortLinkService shortLinkService;

    @PostMapping("/comment")
    public Result<ShortLinkCreateResp> createCommentShortLink(@Valid @RequestBody ShortLinkCommentReq req) {
        String userId = SecurityUtils.getCurrentUserId();
        String code = shortLinkService.createCommentShortLink(req.getPostId(), req.getCommentId(), userId);
        return Result.success(new ShortLinkCreateResp(code));
    }

    @GetMapping("/{code}")
    public Result<?> resolve(@PathVariable String code) {
        return Result.success(shortLinkService.resolve(code));
    }
}
