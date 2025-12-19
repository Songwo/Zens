package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class CreatePostRequest {

    /**
     * 分类 ID
     */
    @NotBlank(message = "文章分类ID不允许为空")
    private String categoryID;

    /**
     * 文章题目
     */
    @NotBlank(message = "文章题目不允许为空")
    @Size(min = 5, max = 100, message = "文章题目长度需在5-100字符之间")
    private String title;

    /**
     * 文章内容
     */
    @NotBlank(message = "文章内容不允许为空")
    @Size(min = 30, message = "文章内容最小为30字符")
    private String content;

    /**
     * 文章引用图片
     */
    private String images;

    /**
     * 文章标签
     */
    @NotBlank(message = "标签不允许为空")
    private String tags;

    /**
     * 是否匿名 1:是 0:否
     */
    private Integer isAnonymous;

    private String locationName;

}
