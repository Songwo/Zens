package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.trend.campus_pulse.dto.response.SectionResp;
import com.campus.trend.campus_pulse.dto.response.SectionStatsAggResp;
import com.campus.trend.campus_pulse.entity.Section;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.mapper.SectionMapper;
import com.campus.trend.campus_pulse.service.SectionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Song：板块服务实现
 */
@Slf4j
@Service
public class SectionServiceImpl implements SectionService {

    private static final long SECTION_CACHE_TTL_SECONDS = 60L;
    private static final String ALL_SECTIONS_CACHE_KEY = "section:list:all";
    private static final String ACTIVE_SECTIONS_CACHE_KEY = "section:list:active";

    @Autowired
    private SectionMapper sectionMapper;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Override
    public List<SectionResp> getAllSections() {
        return getOrLoadSections(ALL_SECTIONS_CACHE_KEY, () -> {
            LambdaQueryWrapper<Section> wrapper = new LambdaQueryWrapper<>();
            wrapper.orderByAsc(Section::getSortOrder);
            return sectionMapper.selectList(wrapper);
        });
    }

    @Override
    public List<SectionResp> getActiveSections() {
        return getOrLoadSections(ACTIVE_SECTIONS_CACHE_KEY, () -> {
            LambdaQueryWrapper<Section> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Section::getStatus, 1)
                    .orderByAsc(Section::getSortOrder);
            return sectionMapper.selectList(wrapper);
        });
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
        SectionStatsAggResp stats = null;
        try {
            LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
            List<SectionStatsAggResp> statsList = postMapper.aggregateSectionStats(List.of(id), todayStart);
            if (statsList != null && !statsList.isEmpty()) {
                stats = statsList.get(0);
            }
        } catch (Exception e) {
            log.warn("查询板块详情统计失败: sectionId={}, err={}", id, e.getMessage());
        }
        return convertToResp(section, stats);
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
        clearSectionCaches();
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
        clearSectionCaches();
        log.info("更新板块: {}", section.getName());
        return section;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSection(Long id) {
        sectionMapper.deleteById(id);
        clearSectionCaches();
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
        clearSectionCaches();
        log.info("切换板块状态: {} -> {}", id, status);
    }

    private List<SectionResp> getOrLoadSections(String cacheKey, Supplier<List<Section>> loader) {
        List<SectionResp> cached = readSectionCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<Section> sections = loader.get();
        List<SectionResp> responses = buildSectionResponses(sections);
        writeSectionCache(cacheKey, responses);
        return responses;
    }

    private List<SectionResp> buildSectionResponses(List<Section> sections) {
        if (sections == null || sections.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> sectionIds = sections.stream()
                .map(Section::getId)
                .filter(id -> id != null && id > 0)
                .toList();

        Map<Long, SectionStatsAggResp> statsMap = Collections.emptyMap();
        if (!sectionIds.isEmpty()) {
            try {
                LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
                statsMap = postMapper.aggregateSectionStats(sectionIds, todayStart).stream()
                        .collect(Collectors.toMap(SectionStatsAggResp::getSectionId, item -> item, (left, right) -> left));
            } catch (Exception e) {
                log.warn("批量查询板块统计失败: {}", e.getMessage());
            }
        }

        Map<Long, SectionStatsAggResp> finalStatsMap = statsMap;
        return sections.stream()
                .map(section -> convertToResp(section, finalStatsMap.get(section.getId())))
                .collect(Collectors.toList());
    }

    private SectionResp convertToResp(Section section, SectionStatsAggResp stats) {
        SectionResp resp = new SectionResp();
        BeanUtils.copyProperties(section, resp);
        resp.setPostCount(stats != null && stats.getPostCount() != null ? stats.getPostCount() : 0L);
        resp.setTodayCount(stats != null && stats.getTodayCount() != null ? stats.getTodayCount() : 0L);
        resp.setHeatScore(stats != null && stats.getHeatScore() != null ? stats.getHeatScore() : 0L);
        return resp;
    }

    private List<SectionResp> readSectionCache(String cacheKey) {
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (!StringUtils.hasText(cached)) {
                return null;
            }
            return objectMapper.readValue(cached, new TypeReference<List<SectionResp>>() {});
        } catch (Exception e) {
            log.warn("读取板块缓存失败: key={}, err={}", cacheKey, e.getMessage());
            try {
                stringRedisTemplate.delete(cacheKey);
            } catch (Exception ignored) {
            }
            return null;
        }
    }

    private void writeSectionCache(String cacheKey, List<SectionResp> responses) {
        try {
            stringRedisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(responses),
                    SECTION_CACHE_TTL_SECONDS,
                    java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("写入板块缓存失败: key={}, err={}", cacheKey, e.getMessage());
        }
    }

    private void clearSectionCaches() {
        try {
            stringRedisTemplate.delete(List.of(ALL_SECTIONS_CACHE_KEY, ACTIVE_SECTIONS_CACHE_KEY));
        } catch (Exception e) {
            log.warn("清理板块缓存失败: {}", e.getMessage());
        }
    }
}
