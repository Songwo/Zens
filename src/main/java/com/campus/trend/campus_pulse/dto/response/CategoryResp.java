package com.campus.trend.campus_pulse.dto.response;

import lombok.Data;

/**
 * 分类响应DTO
 */
@Data
public class CategoryResp {

    /**
     * 分类ID
     */
    private String id;

    /**
     * 分类名称
     */
    private String name;

    /**
     * 分类代码
     */
    private String code;

    /**
     * 分类图标URL
     */
    private String icon;

    /**
     * 排序权重
     */
    private Integer sort;

    /**
     * 该分类下的帖子数量
     */
    private Long postCount;

}
