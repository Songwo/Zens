package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.dto.response.OneboxPreviewResp;
import com.campus.trend.campus_pulse.service.OneboxService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Onebox 链接预览实现。
 *
 * Song：策略 ——
 * - 已知 provider（YouTube/Bilibili/Twitter/GitHub）：正则解析 URL 提取嵌入 ID，不抓 HTML
 * - 其它：抓目标页 HTML（≤ 256KB，3s 超时），正则提取 og:* meta
 * - Redis 缓存 1 小时（key: onebox:preview:{md5(url)}）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OneboxServiceImpl implements OneboxService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 3000;
    private static final int MAX_HTML_BYTES = 256 * 1024;
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    // Song：已知 provider URL 解析
    private static final Pattern YOUTUBE_PATTERN = Pattern.compile(
            "^(?:https?://)?(?:www\\.|m\\.)?(?:youtube\\.com/(?:watch\\?v=|embed/|shorts/)|youtu\\.be/)([A-Za-z0-9_-]{6,})");
    private static final Pattern BILIBILI_PATTERN = Pattern.compile(
            "^(?:https?://)?(?:www\\.|m\\.)?bilibili\\.com/video/(BV[A-Za-z0-9]+)");
    private static final Pattern TWITTER_PATTERN = Pattern.compile(
            "^(?:https?://)?(?:www\\.|mobile\\.)?(?:twitter|x)\\.com/.+/status/(\\d+)");

    // Song：OG meta 正则（大小写不敏感，兼容单双引号）
    private static final Pattern OG_TITLE = Pattern.compile(
            "<meta[^>]+property=[\"']og:title[\"'][^>]+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern OG_DESC = Pattern.compile(
            "<meta[^>]+property=[\"']og:description[\"'][^>]+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern OG_IMAGE = Pattern.compile(
            "<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern OG_SITE = Pattern.compile(
            "<meta[^>]+property=[\"']og:site_name[\"'][^>]+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_TAG = Pattern.compile(
            "<title[^>]*>([^<]+)</title>", Pattern.CASE_INSENSITIVE);
    private static final Pattern META_DESC = Pattern.compile(
            "<meta[^>]+name=[\"']description[\"'][^>]+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    @Override
    public OneboxPreviewResp fetchAndParse(String url) {
        if (!StringUtils.hasText(url)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "url 不能为空");
        }
        String normalized = url.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "仅支持 http/https 链接");
        }

        // Song：先查缓存
        String cacheKey = "onebox:preview:" + md5(normalized);
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                OneboxPreviewResp resp = objectMapper.readValue(cached, OneboxPreviewResp.class);
                resp.setCached(true);
                return resp;
            }
        } catch (Exception e) {
            log.debug("读取 Onebox 缓存失败 url={}, err={}", normalized, e.getMessage());
        }

        OneboxPreviewResp resp = parseWithoutCache(normalized);
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(resp),
                    CACHE_TTL.toMinutes(), java.util.concurrent.TimeUnit.MINUTES);
        } catch (Exception e) {
            log.debug("写入 Onebox 缓存失败 url={}, err={}", normalized, e.getMessage());
        }
        resp.setCached(false);
        return resp;
    }

    private OneboxPreviewResp parseWithoutCache(String url) {
        OneboxPreviewResp resp = new OneboxPreviewResp().setUrl(url);

        // Song：已知 provider 优先用 URL 解析（快、不抓 HTML）
        Matcher yt = YOUTUBE_PATTERN.matcher(url);
        if (yt.find()) {
            return resp.setProvider("youtube")
                    .setEmbedId(yt.group(1))
                    .setEmbeddable(true)
                    .setSiteName("YouTube")
                    .setTitle("YouTube 视频")
                    .setImage("https://i.ytimg.com/vi/" + yt.group(1) + "/hqdefault.jpg");
        }
        Matcher bili = BILIBILI_PATTERN.matcher(url);
        if (bili.find()) {
            return resp.setProvider("bilibili")
                    .setEmbedId(bili.group(1))
                    .setEmbeddable(true)
                    .setSiteName("哔哩哔哩")
                    .setTitle("B站视频 " + bili.group(1));
        }
        Matcher tw = TWITTER_PATTERN.matcher(url);
        if (tw.find()) {
            return resp.setProvider("twitter")
                    .setEmbedId(tw.group(1))
                    .setEmbeddable(false)
                    .setSiteName("X (Twitter)")
                    .setTitle("X 帖子");
        }
        if (url.contains("github.com")) {
            resp.setProvider("github").setSiteName("GitHub");
            // Song：GitHub 卡片前端已有专门处理，这里只回填 siteName
            return resp;
        }

        // Song：通用 OG 抓取
        String hostname;
        try {
            hostname = URI.create(url).getHost();
            if (hostname != null && hostname.startsWith("www.")) {
                hostname = hostname.substring(4);
            }
        } catch (Exception e) {
            hostname = "";
        }
        resp.setProvider("generic").setSiteName(hostname);

        String html;
        try {
            html = fetchHtml(url);
        } catch (Exception e) {
            log.debug("Onebox 抓取 HTML 失败 url={}, err={}", url, e.getMessage());
            // Song：抓取失败返回最小信息，前端降级显示 hostname 卡片
            resp.setTitle(hostname);
            return resp;
        }

        resp.setTitle(extractGroup(OG_TITLE.matcher(html), TITLE_TAG.matcher(html)));
        resp.setDescription(extractGroup(OG_DESC.matcher(html), META_DESC.matcher(html)));
        Matcher img = OG_IMAGE.matcher(html);
        if (img.find()) {
            resp.setImage(img.group(1));
        }
        Matcher site = OG_SITE.matcher(html);
        if (site.find()) {
            resp.setSiteName(site.group(1));
        }
        if (!StringUtils.hasText(resp.getTitle())) {
            resp.setTitle(hostname);
        }
        return resp;
    }

    private String extractGroup(Matcher primary, Matcher fallback) {
        if (primary.find()) {
            return primary.group(1);
        }
        if (fallback.find()) {
            return fallback.group(1);
        }
        return null;
    }

    /**
     * Song：抓取目标页 HTML，跟随重定向，限制大小避免超大页面拖垮服务。
     */
    private String fetchHtml(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        URLConnection conn = url.openConnection();
        if (!(conn instanceof HttpURLConnection http)) {
            throw new IOException("仅支持 HTTP/HTTPS");
        }
        http.setRequestMethod("GET");
        http.setConnectTimeout(CONNECT_TIMEOUT_MS);
        http.setReadTimeout(READ_TIMEOUT_MS);
        http.setInstanceFollowRedirects(true);
        // Song：伪装常规浏览器 UA，避免部分站点拦截
        http.setRequestProperty("User-Agent", "CampusPulse-Onebox/1.0 (compatible; bot)");
        http.setRequestProperty("Accept", "text/html,application/xhtml+xml");
        int code = http.getResponseCode();
        if (code < 200 || code >= 400) {
            throw new IOException("HTTP " + code);
        }
        try (var in = http.getInputStream()) {
            byte[] buf = in.readNBytes(MAX_HTML_BYTES);
            return new String(buf, StandardCharsets.UTF_8);
        }
    }

    private String md5(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
