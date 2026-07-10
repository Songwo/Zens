package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.config.properties.AgentServiceProperties;
import com.campus.trend.campus_pulse.dto.request.CommunityQaAskReq;
import com.campus.trend.campus_pulse.dto.request.PostSearchReq;
import com.campus.trend.campus_pulse.dto.response.PostResp;
import com.campus.trend.campus_pulse.service.AgentGatewayService;
import com.campus.trend.campus_pulse.service.PostService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
public class AgentGatewayServiceImpl implements AgentGatewayService {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final AgentServiceProperties properties;
    private final PostService postService;
    private final RestTemplate restTemplate;

    public AgentGatewayServiceImpl(AgentServiceProperties properties, PostService postService) {
        this.properties = properties;
        this.postService = postService;
        this.restTemplate = new RestTemplate(buildRequestFactory(properties));
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    @Override
    public Map<String, Object> health() {
        if (!isEnabled()) {
            return disabledHealth();
        }
        return exchange("/health", HttpMethod.GET, null);
    }

    @Override
    public Map<String, Object> diagnostics() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", isEnabled());
        data.put("baseUrl", properties.getBaseUrl());
        data.put("connectTimeoutMs", properties.getConnectTimeoutMs());
        data.put("readTimeoutMs", properties.getReadTimeoutMs());
        data.put("checkedAt", Instant.now().toString());

        if (!isEnabled()) {
            data.put("status", "disabled");
            data.put("reachable", false);
            data.put("latencyMs", 0);
            data.put("health", disabledHealth());
            data.put("advice", List.of("检查 campus.agent.enabled 配置，并在启用后重启 Java 后端"));
            return data;
        }

        long startedAt = System.nanoTime();
        try {
            Map<String, Object> health = exchange("/health", HttpMethod.GET, null);
            long latencyMs = elapsedMs(startedAt);
            data.put("status", String.valueOf(health.getOrDefault("status", "unknown")));
            data.put("reachable", true);
            data.put("latencyMs", latencyMs);
            data.put("backend", health.get("backend"));
            data.put("postgres", health.get("postgres"));
            data.put("mysql", health.get("mysql"));
            data.put("mysqlReplica", health.getOrDefault("mysql_replica", health.get("mysql")));
            data.put("replicaReadOnly", health.get("replica_read_only"));
            data.put("replicaReadOnlyRequired", health.get("replica_read_only_required"));
            data.put("llmEnabled", health.get("llm_enabled"));
            data.put("llmConfigured", health.get("llm_configured"));
            data.put("llmStatus", health.get("llm_status"));
            data.put("llmModel", health.get("llm_model"));
            data.put("health", health);
            data.put("advice", buildHealthAdvice(health));
            return data;
        } catch (IllegalStateException ex) {
            data.put("status", "down");
            data.put("reachable", false);
            data.put("latencyMs", elapsedMs(startedAt));
            data.put("error", ex.getMessage());
            data.put("advice", List.of(
                    "确认 agent-service 已启动并监听 " + properties.getBaseUrl(),
                    "检查 campus.agent.base-url 与 AGENT_SERVICE_PORT 是否一致",
                    "确认 Agent 服务能连接 MySQL/PostgreSQL 检索库"));
            return data;
        }
    }

    @Override
    public Map<String, Object> ask(CommunityQaAskReq request) {
        if (!isEnabled()) {
            return fallbackAsk(request, "spring-search-fallback", "agent_disabled");
        }
        try {
            Map<String, Object> response = exchange("/v1/community-qa/ask", HttpMethod.POST, buildPayload(request));
            if (hasAgentSearchHits(response)) {
                return response;
            }
            return fallbackAsk(request, "agent-empty+spring-search-fallback", "agent_empty");
        } catch (IllegalStateException ex) {
            log.warn("Agent ask using community fallback: {}", ex.getMessage());
            return fallbackAsk(request, "agent-unavailable+spring-search-fallback", "agent_unavailable");
        }
    }

    @Override
    public Map<String, Object> search(CommunityQaAskReq request) {
        if (!isEnabled()) {
            return fallbackSearch(request, "spring-search-fallback", "agent_disabled");
        }
        try {
            Map<String, Object> response = exchange("/v1/community-qa/search", HttpMethod.POST, buildPayload(request));
            if (hasAgentSearchHits(response)) {
                return response;
            }
            return fallbackSearch(request, "agent-empty+spring-search-fallback", "agent_empty");
        } catch (IllegalStateException ex) {
            log.warn("Agent search using community fallback: {}", ex.getMessage());
            return fallbackSearch(request, "agent-unavailable+spring-search-fallback", "agent_unavailable");
        }
    }

    @Override
    public StreamingResponseBody askStream(CommunityQaAskReq request) {
        if (!isEnabled()) {
            throw new IllegalStateException("Agent 服务未启用，请先检查 campus.agent.enabled 配置");
        }
        String url = properties.getBaseUrl().replaceAll("/+$", "") + "/v1/community-qa/ask-stream";
        Map<String, Object> payload = buildPayload(request);
        return outputStream -> {
            try {
                restTemplate.execute(
                        url,
                        HttpMethod.POST,
                        clientHttpRequest -> writeStreamingRequest(clientHttpRequest, payload),
                        response -> {
                            if (response.getBody() != null) {
                                StreamUtils.copy(response.getBody(), outputStream);
                                outputStream.flush();
                            }
                            return null;
                        }
                );
            } catch (RestClientException ex) {
                log.warn("Agent 流式服务调用失败: url={}, err={}", url, ex.getMessage());
                writeSseError(outputStream, "Agent 服务暂时不可用，请稍后重试");
            }
        };
    }

    private Map<String, Object> exchange(String path, HttpMethod method, Map<String, Object> body) {
        if (!isEnabled()) {
            throw new IllegalStateException("Agent 服务未启用，请先检查 campus.agent.enabled 配置");
        }
        String url = properties.getBaseUrl().replaceAll("/+$", "") + path;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<?> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, method, entity, MAP_TYPE);
            Map<String, Object> data = response.getBody();
            if (data == null) {
                throw new IllegalStateException("Agent 服务返回了空响应");
            }
            return data;
        } catch (RestClientException ex) {
            log.warn("Agent 服务调用失败: method={}, url={}, err={}", method, url, ex.getMessage());
            throw new IllegalStateException("Agent 服务暂时不可用，请稍后重试");
        }
    }

    private Map<String, Object> buildPayload(CommunityQaAskReq request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("question", request.getQuestion());
        payload.put("retrieval_query", request.getRetrievalQuery());
        payload.put("conversation_context", request.getConversationContext());
        payload.put("section_id", request.getSectionId());
        payload.put("limit", request.getLimit());
        payload.put("include_comments", request.getIncludeComments());
        payload.put("comments_per_post", request.getCommentsPerPost());
        return payload;
    }

    private boolean hasAgentSearchHits(Map<String, Object> response) {
        if (response == null || response.isEmpty()) {
            return false;
        }
        return listSize(response.get("citations")) > 0 || listSize(response.get("related_posts")) > 0;
    }

    private int listSize(Object value) {
        return value instanceof List<?> list ? list.size() : 0;
    }

    private Map<String, Object> fallbackSearch(CommunityQaAskReq request, String backend, String reason) {
        PostSearchReq postSearchReq = new PostSearchReq();
        postSearchReq.setKeyword(resolveRetrievalKeyword(request));
        postSearchReq.setSectionId(request.getSectionId());
        postSearchReq.setPage(1);
        postSearchReq.setPageSize(resolveLimit(request));
        postSearchReq.setNeedTotal(true);
        postSearchReq.setStatus(1);
        postSearchReq.setOrderBy("relevance");

        IPage<PostResp> page = postService.searchPostsWithAuthor(postSearchReq);
        List<PostResp> records = page != null && page.getRecords() != null ? page.getRecords() : List.of();

        List<Map<String, Object>> citations = new ArrayList<>();
        List<Map<String, Object>> relatedPosts = new ArrayList<>();
        int index = 1;
        for (PostResp post : records) {
            if (!StringUtils.hasText(post.getId())) {
                continue;
            }
            citations.add(buildCitation(post, index));
            relatedPosts.add(buildRelatedPost(post));
            index++;
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("citations", citations);
        response.put("related_posts", relatedPosts);
        response.put("hit_count", records.size());
        response.put("backend", backend);
        response.put("fallback_reason", reason);
        return response;
    }

    private Map<String, Object> fallbackAsk(CommunityQaAskReq request, String backend, String reason) {
        long startedAt = System.nanoTime();
        Map<String, Object> search = fallbackSearch(request, backend, reason);
        long totalMs = elapsedMs(startedAt);

        List<Map<String, Object>> citations = mapList(search.get("citations"));
        List<Map<String, Object>> relatedPosts = mapList(search.get("related_posts"));
        int hitCount = toInt(search.get("hit_count"), citations.size() + relatedPosts.size());

        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("backend", backend);
        trace.put("retrieval_ms", totalMs);
        trace.put("llm_ms", 0);
        trace.put("total_ms", totalMs);
        trace.put("hit_count", hitCount);
        trace.put("fallback_reason", reason);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("answer", buildFallbackAnswer(resolveRetrievalKeyword(request), citations, relatedPosts));
        response.put("confidence", estimateFallbackConfidence(citations));
        response.put("citations", citations);
        response.put("related_posts", relatedPosts);
        response.put("trace", trace);
        response.put("backend", backend);
        response.put("fallback_reason", reason);
        return response;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                results.add((Map<String, Object>) map);
            }
        }
        return results;
    }

    private String buildFallbackAnswer(
            String question,
            List<Map<String, Object>> citations,
            List<Map<String, Object>> relatedPosts) {
        String displayQuestion = StringUtils.hasText(question) ? question.trim() : "当前问题";
        if (citations.isEmpty()) {
            if (!relatedPosts.isEmpty()) {
                StringBuilder builder = new StringBuilder();
                builder.append("社区里暂时没有检索到与“").append(displayQuestion).append("”高度相关的历史讨论。\n");
                builder.append("不过普通搜索找到了部分弱相关帖子，可以先作为排查入口：");
                int index = 1;
                for (Map<String, Object> item : relatedPosts.stream().limit(2).toList()) {
                    builder.append("\n").append(index).append(". 《")
                            .append(safeObjectText(item.get("title"), "未命名帖子"))
                            .append("》：")
                            .append(safeObjectText(item.get("summary"), "这条帖子和当前关键词有部分交集"));
                    index++;
                }
                builder.append("\n如果继续追问，建议补充报错、版本、部署方式或业务场景，Agent 命中会更稳定。");
                return builder.toString();
            }
            return "社区里暂时没有检索到与“" + displayQuestion + "”高度相关的历史讨论。建议补充报错、版本、部署方式或业务场景后再试一次。";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("根据社区历史讨论，和“").append(displayQuestion).append("”最接近的信息主要有：");
        int count = 0;
        for (Map<String, Object> item : citations) {
            if (count >= 3) {
                break;
            }
            String index = safeObjectText(item.get("index"), String.valueOf(count + 1));
            builder.append("\n").append(index).append(". 《")
                    .append(safeObjectText(item.get("title"), "未命名帖子"))
                    .append("》提到：")
                    .append(safeObjectText(item.get("excerpt"), "暂无摘录"))
                    .append(" [").append(index).append("]");
            count++;
        }
        builder.append("\n建议优先打开下面的引用帖查看上下文；如果实际环境不同，再补充具体报错或部署信息。");
        return builder.toString();
    }

    private String estimateFallbackConfidence(List<Map<String, Object>> citations) {
        if (citations.size() >= 5) {
            return "high";
        }
        if (citations.size() >= 2) {
            return "medium";
        }
        return "low";
    }

    private String resolveRetrievalKeyword(CommunityQaAskReq request) {
        if (request != null && StringUtils.hasText(request.getRetrievalQuery())) {
            return request.getRetrievalQuery().trim();
        }
        return request != null && StringUtils.hasText(request.getQuestion())
                ? request.getQuestion().trim()
                : "";
    }

    private int resolveLimit(CommunityQaAskReq request) {
        int limit = request != null && request.getLimit() != null ? request.getLimit() : 5;
        return Math.max(1, Math.min(10, limit));
    }

    private Map<String, Object> buildCitation(PostResp post, int index) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("index", index);
        item.put("post_id", post.getId());
        item.put("title", safeText(post.getTitle(), "未命名帖子"));
        item.put("section_name", safeText(post.getSectionName(), ""));
        item.put("excerpt", buildExcerpt(post));
        item.put("source_type", "post");
        item.put("url", buildPostUrl(post));
        return item;
    }

    private Map<String, Object> buildRelatedPost(PostResp post) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("post_id", post.getId());
        item.put("title", safeText(post.getTitle(), "未命名帖子"));
        item.put("section_name", safeText(post.getSectionName(), ""));
        item.put("summary", buildExcerpt(post));
        item.put("tags", parseTags(post.getTags()));
        item.put("score", buildFallbackScore(post));
        item.put("url", buildPostUrl(post));
        return item;
    }

    private String buildExcerpt(PostResp post) {
        String text = firstText(post.getSummary(), post.getContent(), post.getTitle());
        return truncate(cleanText(text), 140);
    }

    private String firstText(String... values) {
        return Arrays.stream(values)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .findFirst()
                .orElse("");
    }

    private String cleanText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text
                .replaceAll("<[^>]+>", " ")
                .replaceAll("[#*_>`\\[\\](){}|]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text == null ? "" : text;
        }
        return text.substring(0, Math.max(0, maxLength - 1)).trim() + "…";
    }

    private String safeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String safeObjectText(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value);
        return StringUtils.hasText(text) ? text.trim() : fallback;
    }

    private int toInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private long elapsedMs(long startedAtNano) {
        return Math.max(0L, (System.nanoTime() - startedAtNano) / 1_000_000L);
    }

    private List<String> buildHealthAdvice(Map<String, Object> health) {
        List<String> advice = new ArrayList<>();
        String status = safeObjectText(health.get("status"), "");
        if (!"ok".equalsIgnoreCase(status)) {
            advice.add("Agent 服务已响应但处于非 ok 状态，请检查检索库连接");
        }
        Object mysqlReplicaStatus = health.getOrDefault("mysql_replica", health.get("mysql"));
        if ("down".equalsIgnoreCase(safeObjectText(mysqlReplicaStatus, ""))) {
            advice.add("MySQL 只读副本不可用，检查 AGENT_MYSQL_REPLICA_* 配置和副本网络连接");
        }
        if ("mysql".equalsIgnoreCase(safeObjectText(health.get("backend"), ""))
                && Boolean.FALSE.equals(health.get("replica_read_only"))) {
            advice.add("Agent 当前连接到可写 MySQL；生产环境应指向只读副本并启用 AGENT_MYSQL_REQUIRE_READ_ONLY");
        }
        if ("down".equalsIgnoreCase(safeObjectText(health.get("postgres"), ""))) {
            advice.add("PostgreSQL 检索库不可用，检查 AGENT_POSTGRES_DSN");
        }
        if (!Boolean.TRUE.equals(health.get("llm_enabled"))) {
            advice.add("LLM 未启用时会使用社区证据兜底回答；需要生成式回答请配置 AGENT_LLM_ENABLED 与 API Key");
        } else if (!Boolean.TRUE.equals(health.get("llm_configured"))) {
            advice.add("LLM 已启用但缺少 API Key，请检查 AGENT_LLM_API_KEY");
        }
        if (advice.isEmpty()) {
            advice.add("Agent 服务可达，检索后端和回答链路状态正常");
        }
        return advice;
    }

    private List<String> parseTags(String rawTags) {
        if (!StringUtils.hasText(rawTags)) {
            return List.of();
        }
        return Arrays.stream(rawTags.split("[,，#\\s]+"))
                .map(tag -> tag.replaceAll("[\\[\\]\"]", "").trim())
                .filter(StringUtils::hasText)
                .distinct()
                .limit(6)
                .toList();
    }

    private double buildFallbackScore(PostResp post) {
        int views = Objects.requireNonNullElse(post.getViewCount(), 0);
        int comments = Objects.requireNonNullElse(post.getCommentCount(), 0);
        int likes = Objects.requireNonNullElse(post.getLikeCount(), 0);
        return Math.round((1.0 + Math.log1p(views) * 0.08 + comments * 0.04 + likes * 0.03) * 1000.0) / 1000.0;
    }

    private String buildPostUrl(PostResp post) {
        return "/t/" + post.getId();
    }

    private Map<String, Object> disabledHealth() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "disabled");
        data.put("backend", "none");
        data.put("postgres", "not_configured");
        data.put("mysql", "not_configured");
        data.put("mysql_replica", "not_configured");
        data.put("replica_read_only", null);
        data.put("replica_read_only_required", false);
        return data;
    }

    private SimpleClientHttpRequestFactory buildRequestFactory(AgentServiceProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        return factory;
    }

    private void writeStreamingRequest(ClientHttpRequest request, Map<String, Object> payload) throws IOException {
        request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        request.getHeaders().setAccept(List.of(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON));
        byte[] body = JsonHolder.toBytes(payload);
        StreamUtils.copy(body, request.getBody());
    }

    private void writeSseError(java.io.OutputStream outputStream, String message) throws IOException {
        String escaped = message
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", " ")
                .replace("\n", " ");
        String event = "event: error\ndata: {\"message\":\"" + escaped + "\"}\n\n";
        outputStream.write(event.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    private static final class JsonHolder {
        private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
                new com.fasterxml.jackson.databind.ObjectMapper();

        private JsonHolder() {
        }

        private static byte[] toBytes(Map<String, Object> payload) throws IOException {
            return OBJECT_MAPPER.writeValueAsBytes(payload);
        }
    }
}
