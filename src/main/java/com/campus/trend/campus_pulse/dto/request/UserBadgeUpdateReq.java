package com.campus.trend.campus_pulse.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员设置用户徽章文字的请求体。
 * badgeText 为 null 或空白表示清除徽章；业务层会 trim 并截断至 20 字。
 */
@Data
public class UserBadgeUpdateReq {

    @Size(max = 50, message = "徽章文字过长")
    private String badgeText;

    /** 纯色样式的颜色 (hex 如 #a855f7)，空=默认色 */
    @Size(max = 20, message = "颜色值过长")
    private String badgeColor;

    /** 样式：solid=纯色 / rainbow=七彩跑马 */
    @Size(max = 16, message = "样式值过长")
    private String badgeStyle;
}
