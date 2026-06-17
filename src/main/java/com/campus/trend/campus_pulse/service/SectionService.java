package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.dto.response.SectionResp;
import com.campus.trend.campus_pulse.entity.Section;

import java.util.List;

/**
 * Song：板块服务接口
 */
public interface SectionService {

    /**
     * Song：获取所有板块列表
     */
    List<SectionResp> getAllSections();

    /**
     * Song：获取启用的板块列表（带缓存）
     */
    List<SectionResp> getActiveSections();

    /**
     * 别名方法，用于缓存预热
     */
    default List<SectionResp> listActiveSections() {
        return getActiveSections();
    }

    Section getSectionById(Long id);

    SectionResp getSectionRespById(Long id);

    /**
     * Song：创建板块
     */
    Section createSection(Section section);

    /**
     * Song：更新板块
     */
    Section updateSection(Section section);

    /**
     * Song：删除板块
     */
    void deleteSection(Long id);

    /**
     * Song：启用/禁用板块
     */
    void toggleSectionStatus(Long id, Integer status);
}
