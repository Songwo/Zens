package com.campus.trend.campus_pulse.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.config.properties.MeilisearchProperties;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.service.SearchService;
import com.campus.trend.campus_pulse.utils.PermissionUtils;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Song：搜索引擎管理（仅管理员）—— 状态查询 + 全量重建索引
 */
@Slf4j
@RestController
@RequestMapping("/admin/search")
@RequiredArgsConstructor
public class SearchAdminController {

    private final SearchService searchService;
    private final MeilisearchProperties meilisearchProperties;
    private final PostMapper postMapper;

    /** 搜索引擎状态（前端管理后台展示用） */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        ensureAdmin();
        Map<String, Object> info = new HashMap<>();
        info.put("engine", meilisearchProperties.isEnabled() ? "meilisearch" : "mysql-fulltext");
        info.put("enabled", meilisearchProperties.isEnabled());
        info.put("available", searchService.isAvailable());
        info.put("host", meilisearchProperties.getHost());
        info.put("index", meilisearchProperties.getPostsIndex());
        return Result.success(info);
    }

    /**
     * 全量重建 Meilisearch 索引（管理员触发）。
     * 分页读取已发布帖子（APPROVED + PUBLISHED）批量索引。
     * 返回索引的帖子数。Meilisearch 未启用时返回 -1。
     */
    @PostMapping("/reindex")
    public Result<Map<String, Object>> reindex(@RequestParam(defaultValue = "500") int batchSize) {
        ensureAdmin();
        if (!meilisearchProperties.isEnabled()) {
            Map<String, Object> r = new HashMap<>();
            r.put("indexed", -1);
            r.put("message", "Meilisearch 未启用（campus.meilisearch.enabled=false）");
            return Result.success(r);
        }
        int safeBatch = Math.min(Math.max(batchSize, 50), 2000);
        searchService.reindexAll();

        int total = 0;
        int page = 1;
        while (true) {
            Page<Post> pageReq = new Page<>(page, safeBatch);
            Page<Post> result = postMapper.selectPage(pageReq,
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Post>()
                            .eq(Post::getStatus, 1)
                            .eq(Post::getAuditStatus, "APPROVED"));
            if (result.getRecords().isEmpty()) {
                break;
            }
            for (Post post : result.getRecords()) {
                searchService.indexPost(post);
                total++;
            }
            if (result.getCurrent() >= result.getPages()) {
                break;
            }
            page++;
        }
        log.info("Meilisearch 全量重建完成, 索引 {} 篇帖子, operator={}", total, SecurityUtils.getCurrentUserId());
        Map<String, Object> r = new HashMap<>();
        r.put("indexed", total);
        r.put("message", "全量重建完成");
        return Result.success(r);
    }

    private void ensureAdmin() {
        String userId = SecurityUtils.getCurrentUserId();
        if (userId == null || !PermissionUtils.isUserAdmin(userId)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "仅管理员可操作搜索引擎管理");
        }
    }
}
