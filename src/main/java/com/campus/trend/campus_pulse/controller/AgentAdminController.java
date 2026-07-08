package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.dto.request.CommunityQaAskReq;
import com.campus.trend.campus_pulse.service.AgentGatewayService;
import com.campus.trend.campus_pulse.utils.PermissionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({"/admin/agent", "/api/admin/agent"})
public class AgentAdminController {

    private final AgentGatewayService agentGatewayService;

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Result<Map<String, Object>> denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        return Result.success(agentGatewayService.diagnostics());
    }

    @PostMapping("/smoke-test")
    public Result<Map<String, Object>> smokeTest(@RequestBody(required = false) CommunityQaAskReq request) {
        Result<Map<String, Object>> denied = requireAdmin();
        if (denied != null) {
            return denied;
        }

        CommunityQaAskReq payload = normalizeSmokeRequest(request);
        long startedAt = System.nanoTime();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("checkedAt", Instant.now().toString());
        data.put("question", payload.getQuestion());
        try {
            Map<String, Object> response = agentGatewayService.ask(payload);
            data.put("ok", true);
            data.put("latencyMs", elapsedMs(startedAt));
            data.put("response", response);
            return Result.success(data);
        } catch (Exception ex) {
            log.warn("Agent smoke test failed: {}", ex.getMessage());
            data.put("ok", false);
            data.put("latencyMs", elapsedMs(startedAt));
            data.put("error", ex.getMessage());
            return Result.success(data);
        }
    }

    private CommunityQaAskReq normalizeSmokeRequest(CommunityQaAskReq request) {
        CommunityQaAskReq payload = request != null ? request : new CommunityQaAskReq();
        String question = payload.getQuestion();
        if (!StringUtils.hasText(question)) {
            question = "deepseek";
        }
        question = question.trim();
        if (question.length() > 400) {
            question = question.substring(0, 400);
        }
        payload.setQuestion(question);
        if (!StringUtils.hasText(payload.getRetrievalQuery())) {
            payload.setRetrievalQuery(question);
        }
        if (payload.getLimit() == null || payload.getLimit() < 1 || payload.getLimit() > 20) {
            payload.setLimit(6);
        }
        if (payload.getIncludeComments() == null) {
            payload.setIncludeComments(true);
        }
        if (payload.getCommentsPerPost() == null || payload.getCommentsPerPost() < 0 || payload.getCommentsPerPost() > 10) {
            payload.setCommentsPerPost(2);
        }
        return payload;
    }

    private long elapsedMs(long startedAtNano) {
        return Math.max(0L, (System.nanoTime() - startedAtNano) / 1_000_000L);
    }

    private <T> Result<T> requireAdmin() {
        if (!PermissionUtils.isAdmin()) {
            return Result.error(ResultCode.NO_PERMISSION, "仅管理员可访问 Agent 管理面板");
        }
        return null;
    }
}
