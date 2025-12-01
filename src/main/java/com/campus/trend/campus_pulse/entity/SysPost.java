package com.campus.trend.campus_pulse.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.sql.Timestamp;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true) // 开启链式操作
@TableName("sys_post") // 映射数据库表名
public class SysPost implements Serializable {

    @TableId(value = "id")
    private String id;
    /**
     * 关联的用户 ID
    */
    private String userId;
    /**
     * 分类的 ID
    */
    private String categoryId;
    /**
     * 文章的题目
    */
    private String title;
    /**
     * 文章的内容
    */
    private String content;
    /**
     * 文章的图片
    */
    private String images;
    /**
     * 文章的标签
    */
    private String tags;
    /**
     * 文章浏览量
    */
    private Integer viewCount;
    /**
     * 文章喜欢量
    */
    private Integer likeCount;
    /**
     * 讨厌分数，趋势分析
    */
    private Double heatScore;
    /**
     * 文章状态
    */
    private String status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
