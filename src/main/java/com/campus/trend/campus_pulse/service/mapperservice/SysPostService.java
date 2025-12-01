package com.campus.trend.campus_pulse.service.mapperservice;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.entity.SysPost;

public interface SysPostService extends IService<SysPost> {

    SysPost searchByPostId(String postId);

}
