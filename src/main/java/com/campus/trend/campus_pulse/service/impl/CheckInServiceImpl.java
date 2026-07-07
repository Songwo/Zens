package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.dto.response.CheckInStatusResp;
import com.campus.trend.campus_pulse.entity.CheckIn;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.mapper.CheckInMapper;
import com.campus.trend.campus_pulse.service.CheckInService;
import com.campus.trend.campus_pulse.service.LevelService;
import com.campus.trend.campus_pulse.service.UserPointsService;
import com.campus.trend.campus_pulse.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class CheckInServiceImpl extends ServiceImpl<CheckInMapper, CheckIn> implements CheckInService {

    private final UserService userService;
    private final LevelService levelService;
    private final UserPointsService userPointsService;

    // 签到奖励：基础值 + 每满 N 天的连续加成
    private static final int BASE_EXP = 5;
    private static final int BASE_POINTS = 2;
    private static final int STREAK_BONUS_EVERY = 7;
    private static final int STREAK_BONUS_EXP = 10;
    private static final int STREAK_BONUS_POINTS = 10;

    @Override
    public CheckInStatusResp getStatus(String userId) {
        LocalDate today = LocalDate.now();
        CheckInStatusResp resp = new CheckInStatusResp();
        resp.setCheckedToday(getByUserAndDate(userId, today) != null);
        resp.setContinuousDays(currentStreak(userId, today));
        Long total = lambdaQuery().eq(CheckIn::getUserId, userId).count();
        resp.setTotalDays(total != null ? total : 0L);
        resp.setMonthDates(monthCheckedDates(userId, today));

        User user = userService.getById(userId);
        resp.setTotalPoints(user != null && user.getPoints() != null ? user.getPoints() : 0);
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CheckInStatusResp checkIn(String userId) {
        LocalDate today = LocalDate.now();
        if (getByUserAndDate(userId, today) != null) {
            throw new BusinessException("今天已经签到过了");
        }

        // 连续天数：昨天有记录则在其基础上 +1，否则从 1 重新开始
        CheckIn yesterday = getByUserAndDate(userId, today.minusDays(1));
        int continuousDays = (yesterday != null && yesterday.getContinuousDays() != null
                ? yesterday.getContinuousDays() : 0) + 1;

        int rewardExp = BASE_EXP;
        int rewardPoints = BASE_POINTS;
        if (continuousDays % STREAK_BONUS_EVERY == 0) {
            rewardExp += STREAK_BONUS_EXP;
            rewardPoints += STREAK_BONUS_POINTS;
        }

        // 1) 写签到记录
        CheckIn record = new CheckIn()
                .setUserId(userId)
                .setCheckInDate(today)
                .setContinuousDays(continuousDays)
                .setRewardPoints(rewardPoints)
                .setRewardExp(rewardExp)
                .setCreateTime(LocalDateTime.now());
        save(record);

        // 2) 写积分:统一走积分收口(原子更新 + sys_point_txn 账本,幂等键=签到日期,天然防重复入账)
        Map<String, Object> earnResult = userPointsService.earn(
                userId, rewardPoints, "checkin", today.toString(), "每日签到");
        int totalPoints = earnResult.get("pointsAfter") instanceof Number n ? n.intValue() : 0;

        // 3) 发经验并触发升级（复用现有等级服务）
        levelService.addExperience(userId, rewardExp, "每日签到");
        levelService.processLevelUpgrade(userId);

        log.info("用户[{}] 签到成功：连续{}天，+{}经验 +{}积分", userId, continuousDays, rewardExp, rewardPoints);

        CheckInStatusResp resp = new CheckInStatusResp();
        resp.setCheckedToday(true);
        resp.setContinuousDays(continuousDays);
        Long total = lambdaQuery().eq(CheckIn::getUserId, userId).count();
        resp.setTotalDays(total != null ? total : 0L);
        resp.setMonthDates(monthCheckedDates(userId, today));
        resp.setRewardExp(rewardExp);
        resp.setRewardPoints(rewardPoints);
        resp.setTotalPoints(totalPoints);
        return resp;
    }

    private CheckIn getByUserAndDate(String userId, LocalDate date) {
        return lambdaQuery()
                .eq(CheckIn::getUserId, userId)
                .eq(CheckIn::getCheckInDate, date)
                .one();
    }

    /** 当前连续天数：以今天或昨天的记录为准，取其 continuousDays；都没有则 0 */
    private int currentStreak(String userId, LocalDate today) {
        CheckIn t = getByUserAndDate(userId, today);
        if (t != null && t.getContinuousDays() != null) {
            return t.getContinuousDays();
        }
        CheckIn y = getByUserAndDate(userId, today.minusDays(1));
        return y != null && y.getContinuousDays() != null ? y.getContinuousDays() : 0;
    }

    /** 本月已签到日期列表（ISO yyyy-MM-dd） */
    private List<String> monthCheckedDates(String userId, LocalDate today) {
        LocalDate first = today.withDayOfMonth(1);
        LocalDate last = today.withDayOfMonth(today.lengthOfMonth());
        List<CheckIn> rows = lambdaQuery()
                .eq(CheckIn::getUserId, userId)
                .ge(CheckIn::getCheckInDate, first)
                .le(CheckIn::getCheckInDate, last)
                .list();
        List<String> dates = new ArrayList<>();
        for (CheckIn r : rows) {
            if (r.getCheckInDate() != null) {
                dates.add(r.getCheckInDate().toString());
            }
        }
        return dates;
    }
}
