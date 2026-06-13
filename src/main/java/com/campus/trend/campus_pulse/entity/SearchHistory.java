package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 搜索历史表
 */
@Data
@Accessors(chain = true)
@TableName(value = "search_history", autoResultMap = true)
public class SearchHistory implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 搜索关键词
     */
    private String keyword;

    /**
     * 结果数量
     */
    private Integer resultCount;

    /**
     * 点击的帖子ID
     */
    private String clickedPostId;

    /**
     * 搜索过滤条件
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> searchFilters;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
