package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建/更新分类请求DTO
 */
@Data
public class CategoryReq {

    /**
     * 分类ID（更新时必填）
     */
    private String id;

    /**
     * 分类名称
     */
    @NotBlank(message = "分类名称不能为空")
    private String name;

    /**
     * 分类代码
     */
    @NotBlank(message = "分类代码不能为空")
    private String code;

    /**
     * 分类图标URL
     */
    private String icon;

    /**
     * 排序权重（越小越靠前）
     */
    private Integer sort;

}
