package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.dto.response.ShortLinkResolveResp;
import com.campus.trend.campus_pulse.entity.ShortLink;

public interface ShortLinkService extends IService<ShortLink> {

    String createCommentShortLink(String postId, String commentId, String creatorId);

    ShortLinkResolveResp resolve(String code);
}
