package com.campus.trend.campus_pulse.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 帖子事件 DTO
 * 用于 WebSocket 实时推送
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostEvent {

    /**
     * 事件类型
     */
    private EventType type;

    /**
     * 帖子ID
     */
    private String postId;

    /**
     * 板块ID（用于板块级别的推送）
     */
    private Long sectionId;

    /**
     * 帖子标题
     */
    private String title;

    /**
     * 作者名称
     */
    private String authorName;

    /**
     * 作者头像
     */
    private String authorAvatar;

    /**
     * 更新的字段数据（JSON格式）
     */
    private UpdateData data;

    /**
     * 事件时间
     */
    private LocalDateTime timestamp;

    /**
     * 事件类型枚举
     */
    public enum EventType {
        POST_CREATED,       // 新帖创建
        POST_REPLIED,       // 新回复
        POST_VIEWED,        // 浏览量更新
        POST_LIKED,         // 点赞数更新
        POST_COLLECTED,     // 收藏数更新
        PIN_UPDATED,        // 置顶状态更新
        CATEGORY_PIN_UPDATED, // 板块置顶更新
        POST_DELETED,       // 帖子删除
        POST_UPDATED        // 帖子内容更新
    }

    /**
     * 更新数据
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateData {
        private Integer viewCount;
        private Integer likeCount;
        private Integer collectCount;
        private Integer commentCount;
        private LocalDateTime lastReplyAt;
        private LocalDateTime lastActivityAt;
        private Integer globalPin;
        private Integer categoryPin;
        private Integer pinOrder;
    }
}
