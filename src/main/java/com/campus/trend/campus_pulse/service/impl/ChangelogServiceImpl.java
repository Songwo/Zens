package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.entity.Changelog;
import com.campus.trend.campus_pulse.mapper.ChangelogMapper;
import com.campus.trend.campus_pulse.service.ChangelogService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChangelogServiceImpl extends ServiceImpl<ChangelogMapper, Changelog> implements ChangelogService {

    @Override
    public List<Changelog> getPublishedList() {
        return lambdaQuery()
                .eq(Changelog::getStatus, 1)
                .orderByDesc(Changelog::getSortOrder)
                .list();
    }

    @Override
    public List<Changelog> getAdminList() {
        return lambdaQuery()
                .orderByDesc(Changelog::getSortOrder)
                .orderByDesc(Changelog::getCreatedAt)
                .list();
    }
}
