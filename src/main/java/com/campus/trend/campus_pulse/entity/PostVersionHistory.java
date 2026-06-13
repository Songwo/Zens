package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("post_version_history")
public class PostVersionHistory implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String postId;
    private Integer versionNo;
    private String editorId;
    private String editorName;
    private String title;
    private String content;
    private String tags;
    private Long sectionId;
    private String coverImage;
    private String changeSummary;
    private LocalDateTime createdAt;
}
