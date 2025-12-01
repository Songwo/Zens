package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.entity.SysPost;

public interface PostService extends IService<SysPost> {

    SysPost searchByPostId(String postId);

}
