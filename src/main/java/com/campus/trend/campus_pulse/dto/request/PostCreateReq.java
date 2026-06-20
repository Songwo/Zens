package com.campus.trend.campus_pulse.dto.request;

import com.campus.trend.campus_pulse.dto.media.MediaObject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class PostCreateReq {

    @NotNull(message = "板块ID不允许为空")
    private Long sectionId;

    /**
     * Song：文章题目
     */
    @NotBlank(message = "文章题目不允许为空")
    @Size(min = 4, max = 100, message = "文章题目需超过3个字符且不超过100个字符")
    private String title;

    /**
     * Song：文章内容
     */
    @NotBlank(message = "文章内容不允许为空")
    @Size(min = 31, message = "文章内容需超过30个字符")
    private String content;

    /**
     * Song：文章引用图片（兼容字段，可是 JSON 字符串或单 URL）
     */
    private String images;

    /**
     * Song：新版媒体字段。前端上传完成后把返回的 MediaObject 列表原样透传过来。
     * 与 images 互斥；若两者都传，mediaList 优先。
     */
    private List<MediaObject> mediaList;

    /**
     * Song：封面图
     */
    private String coverImage;

    /**
     * Song：文章标签
     */
    @NotBlank(message = "标签不允许为空")
    private String tags;

    /**
     * Song：是否匿名 1:是 0:否
     */
    private Integer isAnonymous;

    /**
     * 帖子类型：NORMAL 普通帖 / LOTTERY 抽奖帖。
     */
    private String postType;

    /**
     * 抽奖帖评论截止时间；为空表示不限制。
     */
    private LocalDateTime commentDeadline;

    /**
     * 抽奖帖是否限制每个用户只能评论一次。
     */
    private Boolean commentOncePerUser;

    private String locationName;

    /**
     * Song：可选的附带投票（一帖 0..1）。为空表示不带投票。
     */
    @Valid
    private PollCreateReq poll;

}
