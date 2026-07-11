package com.campus.trend.campus_pulse.seo;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequiredArgsConstructor
public class SeoDocumentController {

    private final SeoDocumentService seoDocumentService;

    @GetMapping(value = {"/sitemap.xml", "/api/seo/sitemap.xml"}, produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        return cached(seoDocumentService.sitemap(), MediaType.APPLICATION_XML);
    }

    @GetMapping(value = {"/feed.xml", "/rss.xml", "/api/seo/feed.xml"}, produces = "application/rss+xml;charset=UTF-8")
    public ResponseEntity<String> rss() {
        return cached(seoDocumentService.rss(), MediaType.parseMediaType("application/rss+xml;charset=UTF-8"));
    }

    @GetMapping(value = {"/robots.txt", "/api/seo/robots.txt"}, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> robots() {
        return cached(seoDocumentService.robots(), MediaType.TEXT_PLAIN);
    }

    private ResponseEntity<String> cached(String body, MediaType contentType) {
        return ResponseEntity.ok()
                .contentType(contentType)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(15)).cachePublic())
                .body(body);
    }
}
