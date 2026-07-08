package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.annotation.RateLimit;
import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.request.CommunityQaAskReq;
import com.campus.trend.campus_pulse.service.AgentGatewayService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/agent")
@Slf4j
public class AgentController {

    private final AgentGatewayService agentGatewayService;

    public AgentController(AgentGatewayService agentGatewayService) {
        this.agentGatewayService = agentGatewayService;
    }

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.success(agentGatewayService.health());
    }

    @PostMapping("/community-qa/ask")
    @RateLimit(key = "agent_community_qa_ask", limit = 12, windowSeconds = 60)
    public Result<?> ask(@Valid @RequestBody CommunityQaAskReq request) {
        try {
            return Result.success(agentGatewayService.ask(request));
        } catch (IllegalStateException ex) {
            log.warn("Agent ask failed: {}", ex.getMessage());
            return Result.failed(ex.getMessage());
        }
    }

    @PostMapping("/community-qa/search")
    @RateLimit(key = "agent_community_qa_search", limit = 20, windowSeconds = 60)
    public Result<?> search(@Valid @RequestBody CommunityQaAskReq request) {
        try {
            return Result.success(agentGatewayService.search(request));
        } catch (IllegalStateException ex) {
            log.warn("Agent search failed: {}", ex.getMessage());
            return Result.failed(ex.getMessage());
        }
    }

    @PostMapping(value = "/community-qa/ask-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(key = "agent_community_qa_ask_stream", limit = 12, windowSeconds = 60)
    public ResponseEntity<StreamingResponseBody> askStream(@Valid @RequestBody CommunityQaAskReq request) {
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .header("Cache-Control", "no-cache")
                    .header("X-Accel-Buffering", "no")
                    .body(agentGatewayService.askStream(request));
        } catch (IllegalStateException ex) {
            log.warn("Agent ask stream failed: {}", ex.getMessage());
            return ResponseEntity.status(503)
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(outputStream -> {
                        String payload = "event: error\ndata: {\"message\":\"" +
                                ex.getMessage().replace("\"", "\\\"") +
                                "\"}\n\n";
                        outputStream.write(payload.getBytes(StandardCharsets.UTF_8));
                    });
        }
    }
}
