package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.entity.CommentCollect;

public interface CommentCollectService extends IService<CommentCollect> {

    boolean isCollected(String commentId, String userId);

    boolean toggleCollect(String commentId, String userId);
}
