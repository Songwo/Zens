package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.dto.request.PostSearchReq;
import com.campus.trend.campus_pulse.entity.Post;

import java.util.List;

/**
 * 搜索服务抽象 —— 支持运行时在 Meilisearch 与 MySQL FULLTEXT 之间切换。
 *
 * Song：开启 campus.meilisearch.enabled=true 时使用 Meilisearch（中文分词、错字容忍、相关度排序），
 * 关闭或不可用时自动降级到 MySQL FULLTEXT BOOLEAN MODE。
 */
public interface SearchService {

    /** 搜索引擎是否可用（Meilisearch 实现会 ping，FULLTEXT 实现恒为 true） */
    boolean isAvailable();

    /**
     * 关键词搜索帖子，返回 postId 列表（按相关度排序）。
     * 仅做关键词检索，过滤条件（板块/标签/时间）由调用方在 DB 层用 in() 二次过滤。
     * 返回空列表表示无结果或引擎不可用，调用方应回退到 FULLTEXT。
     */
    List<String> searchPostIds(PostSearchReq req);

    /** 索引/更新单个帖子（发布、编辑、恢复时调用） */
    void indexPost(Post post);

    /** 从索引中删除帖子（硬删除时调用） */
    void deletePost(String postId);

    /** 全量重建索引（管理员触发，用于初始化或修复） */
    int reindexAll();
}
