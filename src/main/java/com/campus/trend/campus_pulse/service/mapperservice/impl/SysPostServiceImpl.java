package com.campus.trend.campus_pulse.service.mapperservice.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.entity.SysPost;
import com.campus.trend.campus_pulse.mapper.SysPostMapper;
import com.campus.trend.campus_pulse.service.mapperservice.SysPostService;
import org.springframework.stereotype.Service;

@Service
public class SysPostServiceImpl extends ServiceImpl<SysPostMapper, SysPost> implements SysPostService {

    @Override
    public SysPost searchByPostId(String postId) {
        return getById(postId);
    }
}
