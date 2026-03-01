package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.entity.Changelog;

import java.util.List;

public interface ChangelogService extends IService<Changelog> {

    /**
     * 获取所有已发布的日志，按 sortOrder 倒序
     */
    List<Changelog> getPublishedList();
}
