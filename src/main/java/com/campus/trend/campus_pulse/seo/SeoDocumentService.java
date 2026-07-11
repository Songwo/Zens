package com.campus.trend.campus_pulse.seo;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SeoDocumentService {

    private static final String SITE_NAME = "Zens 开放社区";
    private static final String SITE_DESCRIPTION = "一个友善、真实、鼓励长期表达的开放社区。技术可以讨论，生活也可以分享。";
    private static final String BASE62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final Pattern MARKDOWN_LINK = Pattern.compile("!?\\[([^]]*)\\]\\([^)]*\\)");
    private static final Pattern MARKDOWN_MARKS = Pattern.compile("[`*_>#~|{}\\[\\]]+");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final DateTimeFormatter SITEMAP_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter RSS_DATE = DateTimeFormatter.RFC_1123_DATE_TIME;

    private final SeoPostMapper seoPostMapper;

    @Value("${campus.site.url:https://www.allinsong.top}")
    private String configuredSiteUrl;

    public String sitemap() {
        List<SeoPostDocument> posts = seoPostMapper.selectPublishedPosts();
        StringBuilder xml = new StringBuilder(4096 + posts.size() * 180);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        appendUrl(xml, "/", null, "hourly", "1.0");
        appendUrl(xml, "/sections", null, "daily", "0.8");
        appendUrl(xml, "/hot", null, "hourly", "0.8");
        appendUrl(xml, "/featured", null, "daily", "0.8");
        appendUrl(xml, "/about", null, "monthly", "0.5");
        appendUrl(xml, "/terms", null, "yearly", "0.3");
        appendUrl(xml, "/privacy", null, "yearly", "0.3");
        for (SeoPostDocument post : posts) {
            appendUrl(xml, "/t/" + encodePostId(post.getId()), latest(post), "weekly", "0.7");
        }
        return xml.append("</urlset>\n").toString();
    }

    public String rss() {
        List<SeoPostDocument> posts = seoPostMapper.selectPublishedPosts();
        StringBuilder xml = new StringBuilder(4096 + Math.min(posts.size(), 50) * 500);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<rss version=\"2.0\" xmlns:atom=\"http://www.w3.org/2005/Atom\">\n<channel>\n")
                .append("<title>").append(escapeXml(SITE_NAME)).append("</title>\n")
                .append("<link>").append(siteUrl()).append("/</link>\n")
                .append("<description>").append(escapeXml(SITE_DESCRIPTION)).append("</description>\n")
                .append("<language>zh-CN</language>\n")
                .append("<atom:link href=\"").append(siteUrl()).append("/feed.xml\" rel=\"self\" type=\"application/rss+xml\" />\n");
        posts.stream().limit(50).forEach(post -> appendRssItem(xml, post));
        return xml.append("</channel>\n</rss>\n").toString();
    }

    public String robots() {
        return """
                User-agent: *
                Allow: /
                Disallow: /admin/
                Disallow: /auth
                Disallow: /me
                Disallow: /settings
                Disallow: /messages
                Disallow: /notifications
                Disallow: /search
                Disallow: /api/

                Sitemap: %s/sitemap.xml
                """.formatted(siteUrl());
    }

    private void appendUrl(StringBuilder xml, String path, LocalDateTime modified, String frequency, String priority) {
        xml.append("  <url>\n    <loc>").append(escapeXml(siteUrl() + path)).append("</loc>\n");
        if (modified != null) {
            xml.append("    <lastmod>").append(SITEMAP_DATE.format(modified)).append("</lastmod>\n");
        }
        xml.append("    <changefreq>").append(frequency).append("</changefreq>\n")
                .append("    <priority>").append(priority).append("</priority>\n  </url>\n");
    }

    private void appendRssItem(StringBuilder xml, SeoPostDocument post) {
        String link = siteUrl() + "/t/" + encodePostId(post.getId());
        String description = summarize(post);
        xml.append("<item>\n<title>").append(escapeXml(post.getTitle())).append("</title>\n")
                .append("<link>").append(escapeXml(link)).append("</link>\n")
                .append("<guid isPermaLink=\"true\">").append(escapeXml(link)).append("</guid>\n")
                .append("<description>").append(escapeXml(description)).append("</description>\n");
        LocalDateTime published = post.getCreateTime() != null ? post.getCreateTime() : post.getUpdateTime();
        if (published != null) {
            xml.append("<pubDate>").append(RSS_DATE.format(published.atZone(ZoneId.of("Asia/Shanghai")))).append("</pubDate>\n");
        }
        xml.append("</item>\n");
    }

    private String summarize(SeoPostDocument post) {
        String source = hasText(post.getSummary()) ? post.getSummary() : post.getContent();
        if (!hasText(source)) return "阅读 Zens 社区帖子";
        String text = MARKDOWN_LINK.matcher(source).replaceAll("$1");
        text = HTML_TAG.matcher(text).replaceAll(" ");
        text = MARKDOWN_MARKS.matcher(text).replaceAll(" ");
        text = WHITESPACE.matcher(text).replaceAll(" ").trim();
        return text.length() > 240 ? text.substring(0, 237) + "..." : text;
    }

    private LocalDateTime latest(SeoPostDocument post) {
        return post.getUpdateTime() != null ? post.getUpdateTime() : post.getCreateTime();
    }

    private String siteUrl() {
        String value = hasText(configuredSiteUrl) ? configuredSiteUrl.trim() : "https://www.allinsong.top";
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    static String encodePostId(String id) {
        if (!hasText(id) || !id.startsWith("POST_")) return id;
        try {
            BigInteger number = new BigInteger(id.substring(5));
            if (BigInteger.ZERO.equals(number)) return "p0";
            StringBuilder encoded = new StringBuilder();
            BigInteger base = BigInteger.valueOf(62);
            while (number.signum() > 0) {
                BigInteger[] division = number.divideAndRemainder(base);
                encoded.append(BASE62.charAt(division[1].intValue()));
                number = division[0];
            }
            return "p" + encoded.reverse();
        } catch (NumberFormatException ignored) {
            return id;
        }
    }

    private static String escapeXml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
