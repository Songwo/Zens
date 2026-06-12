package com.campus.trend.campus_pulse.service.post;

import com.campus.trend.campus_pulse.dto.media.MediaObject;
import com.campus.trend.campus_pulse.entity.PostMedia;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 帖子相关的无状态纯函数工具集。
 *
 * <p>从 {@code PostServiceImpl} 抽离而来：摘要生成、Markdown 提纯、标签归一化、
 * 媒体对象转换等逻辑均不依赖任何 Spring Bean 或实例状态，集中于此便于复用与测试。
 * 调用方通过静态导入使用，迁移前后行为完全一致。
 */
public final class PostSupport {

    private PostSupport() {
    }

    public static final int SUMMARY_MAX_LEN = 150;

    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 30;

    /**
     * 页大小钳制:空/非正取默认值,超上限取上限。
     * feed 查询与缓存 Key 构建共用,保证两侧口径一致。
     */
    public static int clampPageSize(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(requested, MAX_PAGE_SIZE);
    }

    // 预编译正则，避免 stripMarkdown 每次调用重复编译
    private static final Pattern MD_HTML_TAG      = Pattern.compile("<[^>]*>");
    private static final Pattern MD_CODE_BLOCK     = Pattern.compile("```[\\s\\S]*?```");
    private static final Pattern MD_CODE_BLOCK2    = Pattern.compile("```.*");
    private static final Pattern MD_INLINE_CODE    = Pattern.compile("`[^`]+`");
    private static final Pattern MD_HEADING_START  = Pattern.compile("^#{1,6}\\s+", Pattern.MULTILINE);
    private static final Pattern MD_BOLD_STAR      = Pattern.compile("\\*\\*([^*]+)\\*\\*");
    private static final Pattern MD_BOLD_UNDER     = Pattern.compile("__([^_]+)__");
    private static final Pattern MD_ITALIC_STAR    = Pattern.compile("\\*([^*]+)\\*");
    private static final Pattern MD_ITALIC_UNDER   = Pattern.compile("_([^_]+)_");
    private static final Pattern MD_STRIKE         = Pattern.compile("~~([^~]+)~~");
    private static final Pattern MD_HR_LINE        = Pattern.compile("^[-*]{3,}$", Pattern.MULTILINE);
    private static final Pattern MD_BLOCKQUOTE     = Pattern.compile("^>\\s+", Pattern.MULTILINE);
    private static final Pattern MD_LINK           = Pattern.compile("\\[([^\\]]+)\\]\\([^)]+\\)");
    private static final Pattern MD_IMAGE          = Pattern.compile("!\\[([^\\]]*)\\]\\([^)]+\\)");
    private static final Pattern MD_LIST_UNORDER   = Pattern.compile("^[\\-*+]\\s+", Pattern.MULTILINE);
    private static final Pattern MD_LIST_ORDER     = Pattern.compile("^\\d+\\.\\s+", Pattern.MULTILINE);
    private static final Pattern MD_WHITESPACE     = Pattern.compile("\\s+");

    /**
     * 由正文内容生成摘要：先去除 Markdown 标记，再截断到 {@link #SUMMARY_MAX_LEN}。
     */
    public static String buildSummary(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        return truncate(stripMarkdown(content), SUMMARY_MAX_LEN);
    }

    /**
     * 截断文本到指定长度，超长追加省略号。
     */
    public static String truncate(String text, int maxLen) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String cleaned = text.trim();
        if (cleaned.length() <= maxLen) {
            return cleaned;
        }
        return cleaned.substring(0, maxLen) + "...";
    }

    /**
     * 去除 Markdown / HTML 标记，仅保留可读纯文本。
     */
    public static String stripMarkdown(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        String text = content;
        text = MD_HTML_TAG.matcher(text).replaceAll("");
        text = MD_CODE_BLOCK.matcher(text).replaceAll("");
        text = MD_CODE_BLOCK2.matcher(text).replaceAll("");
        text = MD_INLINE_CODE.matcher(text).replaceAll("");
        text = text.replace("`", "");
        text = MD_HEADING_START.matcher(text).replaceAll("");
        text = MD_BOLD_STAR.matcher(text).replaceAll("$1");
        text = MD_BOLD_UNDER.matcher(text).replaceAll("$1");
        text = MD_ITALIC_STAR.matcher(text).replaceAll("$1");
        text = MD_ITALIC_UNDER.matcher(text).replaceAll("$1");
        text = MD_STRIKE.matcher(text).replaceAll("$1");
        text = MD_HR_LINE.matcher(text).replaceAll("");
        text = MD_BLOCKQUOTE.matcher(text).replaceAll("");
        text = MD_IMAGE.matcher(text).replaceAll("");
        text = MD_LINK.matcher(text).replaceAll("$1");
        text = MD_LIST_UNORDER.matcher(text).replaceAll("");
        text = MD_LIST_ORDER.matcher(text).replaceAll("");
        text = text.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").replace("&quot;", "\"");
        text = MD_WHITESPACE.matcher(text).replaceAll(" ");
        return text.trim();
    }

    /**
     * 归一化标签字符串：支持逗号 / 空格 / # 分隔，去空并以英文逗号重新拼接。
     */
    public static String normalizeTags(String tags) {
        if (!StringUtils.hasText(tags)) return null;
        String[] parts = tags.split("[,，\\s#]+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) {
                if (sb.length() > 0) sb.append(',');
                sb.append(t);
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * 从媒体列表抽取全部可访问 URL，用于回填老的 images 字段。
     */
    public static List<String> extractAccessUrls(List<MediaObject> mediaList) {
        if (mediaList == null || mediaList.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> urls = new ArrayList<>(mediaList.size());
        for (MediaObject media : mediaList) {
            if (media != null && StringUtils.hasText(media.getAccessUrl())) {
                urls.add(media.getAccessUrl());
            }
        }
        return urls;
    }

    /**
     * 取媒体列表中第一个可作为封面的 URL（优先 coverUrl，其次 accessUrl）。
     */
    public static String firstAccessUrl(List<MediaObject> mediaList) {
        if (mediaList == null) {
            return null;
        }
        for (MediaObject media : mediaList) {
            if (media != null) {
                if (StringUtils.hasText(media.getCoverUrl())) {
                    return media.getCoverUrl();
                }
                if (StringUtils.hasText(media.getAccessUrl())) {
                    return media.getAccessUrl();
                }
            }
        }
        return null;
    }

    /**
     * 将持久层 {@link PostMedia} 行转换为对外的 {@link MediaObject}。
     */
    public static MediaObject mediaRowToDto(PostMedia row) {
        if (row == null) {
            return null;
        }
        return MediaObject.builder()
                .fileId(row.getFileId())
                .mediaType(row.getMediaType())
                .accessUrl(row.getAccessUrl())
                .coverUrl(row.getCoverUrl())
                .mimeType(row.getMimeType())
                .originalName(row.getOriginalName())
                .sizeBytes(row.getSizeBytes())
                .width(row.getWidth())
                .height(row.getHeight())
                .durationSeconds(row.getDurationSeconds())
                .build();
    }
}
