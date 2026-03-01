package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.trend.campus_pulse.dto.response.SectionResp;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.entity.Section;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.mapper.SectionMapper;
import com.campus.trend.campus_pulse.service.SectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Song：板块服务实现
 */
@Slf4j
@Service
public class SectionServiceImpl implements SectionService {

    @Autowired
    private SectionMapper sectionMapper;

    @Autowired
    private PostMapper postMapper;

    @Override
    public List<SectionResp> getAllSections() {
        LambdaQueryWrapper<Section> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(Section::getSortOrder);
        List<Section> sections = sectionMapper.selectList(wrapper);
        return sections.stream()
                .map(this::convertToResp)
                .collect(Collectors.toList());
    }

    @Override
    public List<SectionResp> getActiveSections() {
        LambdaQueryWrapper<Section> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Section::getStatus, 1)
                .orderByAsc(Section::getSortOrder);
        List<Section> sections = sectionMapper.selectList(wrapper);
        return sections.stream()
                .map(this::convertToResp)
                .collect(Collectors.toList());
    }

    @Override
    public Section getSectionById(Long id) {
        return sectionMapper.selectById(id);
    }

    @Override
    public SectionResp getSectionRespById(Long id) {
        Section section = sectionMapper.selectById(id);
        if (section == null) {
            return null;
        }
        return convertToResp(section);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Section createSection(Section section) {
        section.setCreatedAt(LocalDateTime.now());
        if (section.getStatus() == null) {
            section.setStatus(1);
        }
        if (section.getSortOrder() == null) {
            section.setSortOrder(0);
        }
        sectionMapper.insert(section);
        log.info("创建板块: {}", section.getName());
        return section;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Section updateSection(Section section) {
        Section existing = sectionMapper.selectById(section.getId());
        if (existing == null) {
            throw new IllegalArgumentException("板块不存在");
        }
        sectionMapper.updateById(section);
        log.info("更新板块: {}", section.getName());
        return section;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSection(Long id) {
        sectionMapper.deleteById(id);
        log.info("删除板块: {}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleSectionStatus(Long id, Integer status) {
        Section section = sectionMapper.selectById(id);
        if (section == null) {
            throw new IllegalArgumentException("板块不存在");
        }
        section.setStatus(status);
        sectionMapper.updateById(section);
        log.info("切换板块状态: {} -> {}", id, status);
    }

    private SectionResp convertToResp(Section section) {
        SectionResp resp = new SectionResp();
        BeanUtils.copyProperties(section, resp);

        // Song：查询该板块下的帖子数量 (总数和今日新增)
        try {
            Long totalCount = postMapper.selectCount(
                    new LambdaQueryWrapper<Post>()
                            .eq(Post::getSectionId, section.getId())
                            .eq(Post::getStatus, 1));
            resp.setPostCount(totalCount != null ? totalCount : 0L);

            // Song：今日起止时间
            LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
            Long todayCount = postMapper.selectCount(
                    new LambdaQueryWrapper<Post>()
                            .eq(Post::getSectionId, section.getId())
                            .eq(Post::getStatus, 1)
                            .ge(Post::getCreateTime, todayStart));
            resp.setTodayCount(todayCount != null ? todayCount : 0L);
        } catch (Exception e) {
            log.warn("查询板块帖子数量失败: sectionId={}", section.getId());
            resp.setPostCount(0L);
            resp.setTodayCount(0L);
        }

        return resp;
    }
}
