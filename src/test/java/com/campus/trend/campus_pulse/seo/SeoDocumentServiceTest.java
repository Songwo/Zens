package com.campus.trend.campus_pulse.seo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SeoDocumentServiceTest {

    private SeoPostMapper mapper;
    private SeoDocumentService service;

    @BeforeEach
    void setUp() {
        mapper = mock(SeoPostMapper.class);
        service = new SeoDocumentService(mapper);
        ReflectionTestUtils.setField(service, "configuredSiteUrl", "https://www.allinsong.top/");
    }

    @Test
    void sitemapUsesAbsoluteCanonicalUrlsAndPublishedPostTimestamps() {
        SeoPostDocument post = post("POST_62", "标题 & XML", "摘要", "正文");
        post.setUpdateTime(LocalDateTime.of(2026, 7, 11, 9, 30));
        when(mapper.selectPublishedPosts()).thenReturn(List.of(post));

        String xml = service.sitemap();

        assertThat(xml).contains("<loc>https://www.allinsong.top/</loc>")
                .contains("<loc>https://www.allinsong.top/t/p10</loc>")
                .contains("<lastmod>2026-07-11</lastmod>")
                .doesNotContain("<loc>/");
    }

    @Test
    void rssEscapesUserContentAndStripsMarkdown() {
        SeoPostDocument post = post("plain-id", "A & B <C>", "", "## Hello [Zens](https://example.com) & friends");
        post.setCreateTime(LocalDateTime.of(2026, 7, 11, 8, 0));
        when(mapper.selectPublishedPosts()).thenReturn(List.of(post));

        String rss = service.rss();

        assertThat(rss).contains("<title>A &amp; B &lt;C&gt;</title>")
                .contains("Hello Zens &amp; friends")
                .contains("https://www.allinsong.top/t/plain-id")
                .contains("<pubDate>");
    }

    @Test
    void robotsBlocksPrivateAndDuplicateResultPages() {
        String robots = service.robots();

        assertThat(robots).contains("Disallow: /admin/")
                .contains("Disallow: /search")
                .contains("Sitemap: https://www.allinsong.top/sitemap.xml");
    }

    private SeoPostDocument post(String id, String title, String summary, String content) {
        SeoPostDocument post = new SeoPostDocument();
        post.setId(id);
        post.setTitle(title);
        post.setSummary(summary);
        post.setContent(content);
        return post;
    }
}
