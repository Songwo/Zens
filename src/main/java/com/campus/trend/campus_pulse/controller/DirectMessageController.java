package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.request.DirectMessageSendReq;
import com.campus.trend.campus_pulse.service.DirectMessageService;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/dm")
@Tag(name = "私信管理", description = "用户私信会话与消息接口")
public class DirectMessageController {

    private final DirectMessageService directMessageService;

    @PostMapping("/send")
    @Operation(summary = "发送私信")
    public Result<Void> send(@Valid @RequestBody DirectMessageSendReq req) {
        String userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.failed("请先登录");
        }
        directMessageService.sendMessage(userId, req);
        return Result.success();
    }

    @GetMapping("/conversations")
    @Operation(summary = "会话列表")
    public Result<?> listConversations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        String userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.failed("请先登录");
        }
        return Result.success(directMessageService.listConversations(userId, page, pageSize));
    }

    @GetMapping("/messages/{peerId}")
    @Operation(summary = "会话消息列表")
    public Result<?> listMessages(
            @PathVariable String peerId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int pageSize) {
        String userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.failed("请先登录");
        }
        return Result.success(directMessageService.listMessages(userId, peerId, page, pageSize));
    }

    @PostMapping("/read/{peerId}")
    @Operation(summary = "标记会话为已读")
    public Result<Void> markRead(@PathVariable String peerId) {
        String userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.failed("请先登录");
        }
        directMessageService.markConversationRead(userId, peerId);
        return Result.success();
    }

    @GetMapping("/unread-count")
    @Operation(summary = "未读私信数")
    public Result<Long> unreadCount() {
        String userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return Result.success(0L);
        }
        long count = directMessageService.getUnreadCount(userId);
        return Result.success(count);
    }
}
