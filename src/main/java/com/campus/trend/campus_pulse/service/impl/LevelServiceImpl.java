package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.trend.campus_pulse.dto.response.LevelExpRecordPageResp;
import com.campus.trend.campus_pulse.dto.response.LevelExpRecordResp;
import com.campus.trend.campus_pulse.dto.response.LevelInfoResp;
import com.campus.trend.campus_pulse.entity.LevelExpLog;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.mapper.LevelExpLogMapper;
import com.campus.trend.campus_pulse.mapper.UserMapper;
import com.campus.trend.campus_pulse.service.LevelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class LevelServiceImpl implements LevelService {

    private final UserMapper userMapper;
    private final LevelExpLogMapper levelExpLogMapper;

    private static final int[] LEVEL_THRESHOLDS = {
            0, // Song：说明
            100, // Song：说明
            300, // Song：说明
            600, // Song：说明
            1000, // Song：说明
            1500, // Song：说明
            2100, // Song：说明
            2800, // Song：说明
            3600, // Song：说明
            4500 // Song：说明
    };
    private static final int LV6_REQUIRED_EXP = 1500;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "user:info", key = "#userId")
    public void addExperience(String userId, int exp, String reason) {
        // Song：支持负增量（取消点赞/收藏对称扣回），经验保底不为负
        if (userId == null || exp == 0) return;

        if (userMapper.addExperienceAtomic(userId, exp) == 0) return;
        User user = userMapper.selectById(userId);
        if (user == null) return;
        int currentExp = user.getExperience() != null ? user.getExperience() : 0;
        int calculatedLevel = calculateLevel(currentExp);
        userMapper.raiseLevelAtLeast(userId, calculatedLevel);

        // Song：经验日志记录失败不影响主流程
        try {
            LevelExpLog expLog = new LevelExpLog()
                    .setUserId(userId)
                    .setExpDelta(exp)
                    .setReason(reason)
                    .setCreateTime(LocalDateTime.now());
            levelExpLogMapper.insert(expLog);
        } catch (Exception e) {
            log.warn("写入经验日志失败: userId={}, exp={}, reason={}, err={}",
                    userId, exp, reason, e.getMessage());
        }

        log.debug("用户[{}] 获得 {} 经验 ({}), 当前经验: {}", userId, exp, reason, currentExp);
    }

    @Override
    public LevelInfoResp getUserLevelInfo(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) return null;

        int experience = user.getExperience() != null ? user.getExperience() : 0;
        int level = user.getLevel() != null ? user.getLevel() : 1;

        int calculatedLevel = calculateLevel(experience);
        int currentLevelExp = LEVEL_THRESHOLDS[Math.min(calculatedLevel - 1, LEVEL_THRESHOLDS.length - 1)];
        int nextLevelExp;
        double progress;

        if (calculatedLevel >= LEVEL_THRESHOLDS.length) {
            nextLevelExp = LEVEL_THRESHOLDS[LEVEL_THRESHOLDS.length - 1];
            progress = 100.0;
        } else {
            nextLevelExp = LEVEL_THRESHOLDS[calculatedLevel];
            int rangeExp = nextLevelExp - currentLevelExp;
            int currentRangeExp = experience - currentLevelExp;
            progress = rangeExp > 0 ? Math.min(100.0, (currentRangeExp * 100.0) / rangeExp) : 100.0;
        }

        LevelInfoResp resp = new LevelInfoResp();
        resp.setLevel(Math.max(level, calculatedLevel));
        resp.setExperience(experience);
        resp.setCurrentLevelExp(currentLevelExp);
        resp.setNextLevelExp(nextLevelExp);
        resp.setProgress(Math.round(progress * 100.0) / 100.0);
        resp.setLastUpgrade(user.getUpdateTime());
        return resp;
    }

    @Override
    public LevelExpRecordPageResp getExperienceRecords(String userId, Integer days, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);

        LambdaQueryWrapper<LevelExpLog> wrapper = new LambdaQueryWrapper<LevelExpLog>()
                .eq(LevelExpLog::getUserId, userId)
                .orderByDesc(LevelExpLog::getCreateTime);

        if (days != null && days > 0) {
            wrapper.ge(LevelExpLog::getCreateTime, LocalDateTime.now().minusDays(days));
        }

        Page<LevelExpLog> queryPage = new Page<>(safePage, safePageSize);
        Page<LevelExpLog> resultPage = levelExpLogMapper.selectPage(queryPage, wrapper);

        List<LevelExpRecordResp> records = new ArrayList<>();
        for (LevelExpLog item : resultPage.getRecords()) {
            LevelExpRecordResp resp = new LevelExpRecordResp();
            resp.setId(item.getId());
            resp.setExpDelta(item.getExpDelta());
            resp.setReason(item.getReason());
            resp.setCreateTime(item.getCreateTime());
            records.add(resp);
        }

        // 历史兼容：旧版本累计的经验可能没有日志，给前端返回一条兜底记录
        if (resultPage.getTotal() == 0) {
            User user = userMapper.selectById(userId);
            int experience = user != null && user.getExperience() != null ? user.getExperience() : 0;
            if (experience > 0) {
                if (safePage == 1) {
                    LevelExpRecordResp fallback = new LevelExpRecordResp();
                    fallback.setId(-1L);
                    fallback.setExpDelta(experience);
                    fallback.setReason("历史累计经验（记录功能上线前）");
                    fallback.setCreateTime(LocalDateTime.now());
                    records.add(fallback);
                }
                return new LevelExpRecordPageResp(records, 1L, safePage, safePageSize, 1L);
            }
        }

        return new LevelExpRecordPageResp(
                records,
                resultPage.getTotal(),
                resultPage.getCurrent(),
                resultPage.getSize(),
                resultPage.getPages());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "user:info", key = "#userId")
    public void processLevelUpgrade(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) return;

        int experience = user.getExperience() != null ? user.getExperience() : 0;
        int currentLevel = user.getLevel() != null ? user.getLevel() : 1;
        int newLevel = calculateLevel(experience);

        if (newLevel > currentLevel) {
            userMapper.raiseLevelAtLeast(userId, newLevel);
            log.info("用户[{}] 升级: Lv{} -> Lv{}, 经验: {}", userId, currentLevel, newLevel, experience);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchUpgradeAllUsers() {
        log.info("开始批量升级所有用户等级...");
        List<User> users = userMapper.selectList(null);
        int upgraded = 0;

        for (User user : users) {
            if (user.getStatus() != null && user.getStatus() != 1) continue;

            int experience = user.getExperience() != null ? user.getExperience() : 0;
            int currentLevel = user.getLevel() != null ? user.getLevel() : 1;
            int newLevel = calculateLevel(experience);

            if (newLevel > currentLevel) {
                userMapper.raiseLevelAtLeast(user.getId(), newLevel);
                upgraded++;
                log.info("用户[{}] 升级: Lv{} -> Lv{}", user.getId(), currentLevel, newLevel);
            }
        }

        log.info("批量升级完成, 共升级 {} 名用户", upgraded);
    }

    private int calculateLevel(int experience) {
        int level = 1;
        for (int i = LEVEL_THRESHOLDS.length - 1; i >= 0; i--) {
            if (experience >= LEVEL_THRESHOLDS[i]) {
                level = i + 1;
                break;
            }
        }
        return level;
    }
}
