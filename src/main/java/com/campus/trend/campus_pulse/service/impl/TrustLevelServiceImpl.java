package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.config.properties.TrustLevelProperties;
import com.campus.trend.campus_pulse.dto.response.TrustInfoResp;
import com.campus.trend.campus_pulse.dto.response.TrustMetricsSnapshot;
import com.campus.trend.campus_pulse.entity.TrustEvent;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.mapper.TrustEventMapper;
import com.campus.trend.campus_pulse.mapper.TrustMetricsMapper;
import com.campus.trend.campus_pulse.mapper.UserMapper;
import com.campus.trend.campus_pulse.service.NotificationService;
import com.campus.trend.campus_pulse.service.TrustLevelService;
import com.campus.trend.campus_pulse.utils.PermissionUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 信任等级服务实现（借鉴 Discourse TL0-TL4 社区自治模型）
 *
 * Song：等级与权限双轴分离 —— 经验值等级(level)反映资历，信任等级(trust_level)反映行为质量。
 * TL0-TL3 由行为指标自动晋升，TL3 不活跃会降级，TL4 仅管理员手动。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrustLevelServiceImpl implements TrustLevelService {

    public static final int TL_NEW_USER = 0;
    public static final int TL_BASIC = 1;
    public static final int TL_MEMBER = 2;
    public static final int TL_REGULAR = 3;
    public static final int TL_LEADER = 4;

    private static final String[] LEVEL_LABELS = {"新人", "基础", "成员", "常客", "领袖"};
    private static final String[] LEVEL_DESC = {
            "新注册用户，部分功能受限以防止垃圾内容",
            "已通过基础门槛，可发布外链",
            "活跃社区成员，可上传附件、使用私信",
            "社区常客，举报权重更高，可参与自治",
            "社区领袖，由管理员授予，拥有高级治理权限"
    };
    private static final List<List<String>> LEVEL_PRIVILEGES = List.of(
            List.of("浏览", "评论", "点赞"),
            List.of("发布外链", "搜索无限制"),
            List.of("上传附件", "私信", "创建标签"),
            List.of("举报权重 5x", "标记问题内容", "可被选为版主"),
            List.of("置顶帖子", "合并/分割帖子", "编辑他人帖子")
    );

    private final UserMapper userMapper;
    private final TrustMetricsMapper trustMetricsMapper;
    private final TrustEventMapper trustEventMapper;
    private final TrustLevelProperties props;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    @Override
    public int getTrustLevel(String userId) {
        if (!StringUtils.hasText(userId)) return TL_NEW_USER;
        User user = userMapper.selectById(userId);
        if (user == null) return TL_NEW_USER;
        // Song：管理员/超级管理员默认享有最高信任等级，不受行为指标限制
        // 避免"管理员发外链被自己写的 TL0 拦截"这种割裂体验
        if (isAdminUser(user)) {
            return TL_LEADER;
        }
        return normalizeLevel(user.getTrustLevel());
    }

    @Override
    public boolean isUserTrusted(String userId, int minLevel) {
        return getTrustLevel(userId) >= minLevel;
    }

    @Override
    public boolean canPostExternalLinks(String userId) {
        return isUserTrusted(userId, TL_BASIC);
    }

    @Override
    public boolean canUploadAttachments(String userId) {
        return isUserTrusted(userId, TL_MEMBER);
    }

    @Override
    public int flagWeight(String userId) {
        int tl = getTrustLevel(userId);
        if (tl >= TL_REGULAR) return 5;
        if (tl >= TL_MEMBER) return 3;
        return 1;
    }

    @Override
    public List<TrustInfoResp.LevelSpec> getLevelSpecs() {
        return buildLevelSpecs();
    }

    @Override
    public TrustInfoResp getUserTrustInfo(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        int tl = isAdminUser(user) ? TL_LEADER : normalizeLevel(user.getTrustLevel());
        TrustInfoResp.Metrics metrics = buildMetrics(user);
        TrustInfoResp resp = new TrustInfoResp()
                .setTrustLevel(tl)
                .setLevelLabel(LEVEL_LABELS[Math.min(tl, LEVEL_LABELS.length - 1)])
                .setSilenced(user.getSilencedUntil() != null && user.getSilencedUntil().isAfter(LocalDateTime.now()))
                .setSilencedUntil(user.getSilencedUntil())
                .setAsOf(LocalDateTime.now())
                .setWindowDays(props.getTl3().getWindowDays())
                .setDataStatus("LIVE")
                .setMetrics(metrics);

        resp.setLevels(buildLevelSpecs());
        List<TrustInfoResp.MetricProgress> progress = buildMetricProgress(tl, metrics);
        resp.setMetricProgress(progress);
        resp.setOverallProgress(progress.isEmpty()
                ? 100
                : (int) Math.round(progress.stream()
                        .mapToInt(TrustInfoResp.MetricProgress::getPercent)
                        .average()
                        .orElse(0)));
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "user:info", key = "#userId")
    public boolean recalculateAndPromote(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) return false;
        if (isAdminUser(user)) return false;
        // Song：管理员手动授予的 TL4 不被自动降级，但其它等级正常重算
        int current = user.getTrustLevel() == null ? TL_NEW_USER : user.getTrustLevel();
        int target = computeTargetLevel(user);
        if (target == current) return false;

        return applyLevelChange(user, current, target,
                target > current ? "自动晋升" : "自动降级（活跃度不达标）", buildMetrics(user));
    }

    @Override
    @CacheEvict(value = "user:info", allEntries = true)
    public int batchRecalculateAllActiveUsers() {
        // Song：仅重算最近 windowDays 内活跃过的用户，避免全表扫描
        LocalDateTime since = LocalDateTime.now().minusDays(props.getTl3().getWindowDays());
        List<User> users = userMapper.selectList(
                Wrappers.<User>lambdaQuery()
                        .ge(User::getLastActiveTime, since)
                        .ne(User::getStatus, 2)
                        .isNotNull(User::getLastActiveTime)
        );
        int changed = 0;
        for (User user : users) {
            try {
                if (recalculateAndPromote(user.getId())) {
                    changed++;
                }
            } catch (Exception e) {
                log.warn("重算信任等级失败 userId={}, err={}", user.getId(), e.getMessage());
            }
        }
        log.info("信任等级批量重算完成: 扫描 {} 人, 变更 {} 人", users.size(), changed);
        return changed;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "user:info", key = "#userId")
    public void setTrustLevel(String operatorId, String userId, int newLevel, String reason) {
        if (!PermissionUtils.isUserAdmin(operatorId)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "仅管理员可手动设置信任等级");
        }
        if (newLevel < TL_NEW_USER || newLevel > TL_LEADER) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "信任等级必须在 0-4 之间");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        int old = user.getTrustLevel() == null ? TL_NEW_USER : user.getTrustLevel();
        if (old == newLevel) return;
        boolean changed = applyLevelChange(user, old, newLevel,
                "管理员操作：" + (StringUtils.hasText(reason) ? reason : "无"), buildMetrics(user));
        if (!changed) {
            throw new BusinessException(ResultCode.FAILED, "用户信任等级刚刚发生变化，请刷新后重试");
        }
    }

    @Override
    public java.util.List<com.campus.trend.campus_pulse.entity.TrustEvent> getRecentEvents(int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.campus.trend.campus_pulse.entity.TrustEvent> p =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(safePage, safeSize);
        return trustEventMapper.selectPage(p,
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<com.campus.trend.campus_pulse.entity.TrustEvent>lambdaQuery()
                        .orderByDesc(com.campus.trend.campus_pulse.entity.TrustEvent::getCreateTime))
                .getRecords();
    }

    @Override
    public java.util.List<com.campus.trend.campus_pulse.entity.TrustEvent> getEventsByUserId(String userId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 50);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.campus.trend.campus_pulse.entity.TrustEvent> p =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(safePage, safeSize);
        return trustEventMapper.selectPage(p,
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<com.campus.trend.campus_pulse.entity.TrustEvent>lambdaQuery()
                        .eq(com.campus.trend.campus_pulse.entity.TrustEvent::getUserId, userId)
                        .orderByDesc(com.campus.trend.campus_pulse.entity.TrustEvent::getCreateTime))
                .getRecords();
    }

    // ====================== 核心计算 ======================

    /**
     * Song：按 Discourse 规则计算用户应处的信任等级。
     * - TL0→TL1→TL2 单调晋升（满足即升，不会因不活跃降）
     * - TL3 需在 windowDays 内持续达标，不满足自动降回 TL2
     * - TL4 永不自动调整（手动授予保持不变）
     */
    private int computeTargetLevel(User user) {
        int current = user.getTrustLevel() == null ? TL_NEW_USER : user.getTrustLevel();
        if (current == TL_LEADER) {
            // Song：TL4 手动授予，自动重算不动它
            return TL_LEADER;
        }
        TrustInfoResp.Metrics m = buildMetrics(user);
        // Song：TL3 是会降级的等级 —— 先判断是否仍满足 TL3，不满足则掉回 TL2
        if (current >= TL_REGULAR) {
            if (!meetsTl3(m)) {
                return TL_MEMBER;
            }
            return TL_REGULAR;
        }
        // Song：TL0-TL2 单调晋升。历史已获得的基础/成员等级不会因窗口数据变化被降级。
        int earned = TL_NEW_USER;
        if (meetsTl2(m)) {
            earned = TL_MEMBER;
        } else if (meetsTl1(m)) {
            earned = TL_BASIC;
        }
        return Math.max(current, earned);
    }

    private boolean meetsTl1(TrustInfoResp.Metrics m) {
        TrustLevelProperties.Tl1 c = props.getTl1();
        return m.getDaysSinceRegister() >= c.getRequiresDays()
                && m.getPostsEnteredRecent() >= c.getRequiresPostsEntered()
                && m.getPostsReadRecent() >= c.getRequiresPostsRead()
                && (m.getReadTimeSec() / 60.0) >= c.getRequiresReadMinutes();
    }

    private boolean meetsTl2(TrustInfoResp.Metrics m) {
        TrustLevelProperties.Tl2 c = props.getTl2();
        return m.getDaysVisited() >= c.getRequiresDaysVisited()
                && m.getPostsEnteredRecent() >= c.getRequiresPostsEntered()
                && m.getPostsReadRecent() >= c.getRequiresPostsRead()
                && (m.getReadTimeSec() / 60.0) >= c.getRequiresReadMinutes()
                && m.getLikesReceived() >= c.getRequiresLikesReceived();
    }

    private boolean meetsTl3(TrustInfoResp.Metrics m) {
        TrustLevelProperties.Tl3 c = props.getTl3();
        return m.getDaysVisitedRecent() >= c.getRequiresDaysVisited()
                && m.getPostsReadRecent() >= c.getRequiresPostsRead()
                && m.getLikesReceived() >= c.getRequiresLikesReceived()
                && m.getLikesGiven() >= c.getRequiresLikesGiven()
                && m.getPostsCreated() >= c.getRequiresPostsCreated();
    }

    private boolean applyLevelChange(User user, int oldLevel, int newLevel, String reason,
                                     TrustInfoResp.Metrics metrics) {
        if (userMapper.updateTrustLevelIfCurrent(user.getId(), oldLevel, newLevel) == 0) {
            log.info("信任等级并发变更已跳过 userId={}, expected=TL{}, target=TL{}",
                    user.getId(), oldLevel, newLevel);
            return false;
        }

        TrustEvent event = new TrustEvent()
                .setUserId(user.getId())
                .setOldLevel(oldLevel)
                .setNewLevel(newLevel)
                .setReason(reason)
                .setCreateTime(LocalDateTime.now());
        try {
            event.setMetricsJson(objectMapper.writeValueAsString(metrics));
        } catch (Exception e) {
            log.debug("序列化 TL 指标快照失败 userId={}", user.getId());
        }
        trustEventMapper.insert(event);

        // Song：晋升/降级都通知用户
        boolean promote = newLevel > oldLevel;
        String title = promote ? "你的信任等级提升了" : "你的信任等级发生了变化";
        String content = String.format("你的信任等级从 %s 变更为 %s（%s）。",
                LEVEL_LABELS[Math.min(oldLevel, 4)], LEVEL_LABELS[Math.min(newLevel, 4)], reason);
        try {
            notificationService.createNotification(user.getId(), null, "系统",
                    null, title, content, 0, null);
        } catch (Exception e) {
            log.warn("信任等级变更通知失败 userId={}", user.getId());
        }
        log.info("信任等级变更 userId={} : TL{} → TL{} ({})", user.getId(), oldLevel, newLevel, reason);
        return true;
    }

    // ====================== 指标聚合 ======================

    private TrustInfoResp.Metrics buildMetrics(User user) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowSince = now.minusDays(props.getTl3().getWindowDays());
        TrustMetricsSnapshot snapshot = trustMetricsMapper.selectSnapshot(user.getId(), windowSince);
        if (snapshot == null) {
            throw new IllegalStateException("用户行为画像聚合结果为空");
        }

        TrustInfoResp.Metrics m = new TrustInfoResp.Metrics();
        m.setDaysSinceRegister(user.getCreateTime() == null ? 0
                : Duration.between(user.getCreateTime(), now).toDays());
        m.setDaysVisited(safeInt(snapshot.getDaysVisited()));
        m.setDaysVisitedRecent(safeInt(snapshot.getDaysVisitedRecent()));
        m.setPostsEnteredRecent(safeInt(snapshot.getPostsEnteredRecent()));
        m.setPostsReadRecent(safeInt(snapshot.getPostsReadRecent()));
        m.setReadTimeSec(snapshot.getReadTimeSec());
        m.setLikesReceived(safeInt(snapshot.getLikesReceived()));
        m.setLikesGiven(safeInt(snapshot.getLikesGiven()));
        m.setPostsCreated(safeInt(snapshot.getPostsCreated()));
        return m;
    }

    private int safeInt(long value) {
        return (int) Math.min(Math.max(value, 0), Integer.MAX_VALUE);
    }

    private int normalizeLevel(Integer level) {
        return Math.min(Math.max(level == null ? TL_NEW_USER : level, TL_NEW_USER), TL_LEADER);
    }

    private boolean isAdminUser(User user) {
        if (user == null || user.getRole() == null) return false;
        return "ROLE_ADMIN".equals(user.getRole()) || "ROLE_SUPER_ADMIN".equals(user.getRole());
    }

    private List<TrustInfoResp.MetricProgress> buildMetricProgress(int currentLevel, TrustInfoResp.Metrics m) {
        List<TrustInfoResp.MetricProgress> items = new ArrayList<>();
        if (currentLevel >= TL_REGULAR) {
            return items;
        }
        if (currentLevel == TL_NEW_USER) {
            TrustLevelProperties.Tl1 c = props.getTl1();
            addProgress(items, "daysSinceRegister", "注册时间", m.getDaysSinceRegister(), c.getRequiresDays(), "天");
            addProgress(items, "postsEnteredRecent", "进入不同帖子", m.getPostsEnteredRecent(), c.getRequiresPostsEntered(), "篇");
            addProgress(items, "postsReadRecent", "有效阅读", m.getPostsReadRecent(), c.getRequiresPostsRead(), "次");
            addProgress(items, "readMinutes", "累计阅读", m.getReadTimeSec() / 60, c.getRequiresReadMinutes(), "分钟");
        } else if (currentLevel == TL_BASIC) {
            TrustLevelProperties.Tl2 c = props.getTl2();
            addProgress(items, "daysVisited", "累计访问", m.getDaysVisited(), c.getRequiresDaysVisited(), "天");
            addProgress(items, "postsEnteredRecent", "进入不同帖子", m.getPostsEnteredRecent(), c.getRequiresPostsEntered(), "篇");
            addProgress(items, "postsReadRecent", "有效阅读", m.getPostsReadRecent(), c.getRequiresPostsRead(), "次");
            addProgress(items, "readMinutes", "累计阅读", m.getReadTimeSec() / 60, c.getRequiresReadMinutes(), "分钟");
            addProgress(items, "likesReceived", "收到点赞", m.getLikesReceived(), c.getRequiresLikesReceived(), "次");
        } else {
            TrustLevelProperties.Tl3 c = props.getTl3();
            addProgress(items, "daysVisitedRecent", "窗口访问", m.getDaysVisitedRecent(), c.getRequiresDaysVisited(), "天");
            addProgress(items, "postsReadRecent", "窗口阅读", m.getPostsReadRecent(), c.getRequiresPostsRead(), "次");
            addProgress(items, "likesReceived", "收到点赞", m.getLikesReceived(), c.getRequiresLikesReceived(), "次");
            addProgress(items, "likesGiven", "给出点赞", m.getLikesGiven(), c.getRequiresLikesGiven(), "次");
            addProgress(items, "postsCreated", "公开帖子", m.getPostsCreated(), c.getRequiresPostsCreated(), "篇");
        }
        return items;
    }

    private void addProgress(List<TrustInfoResp.MetricProgress> items, String key, String label,
                             long current, long target, String unit) {
        long safeTarget = Math.max(target, 1);
        int percent = (int) Math.min(100, Math.round(current * 100.0 / safeTarget));
        items.add(new TrustInfoResp.MetricProgress()
                .setKey(key)
                .setLabel(label)
                .setCurrent(Math.max(current, 0))
                .setTarget(Math.max(target, 0))
                .setUnit(unit)
                .setPercent(percent)
                .setMet(current >= target));
    }

    private List<TrustInfoResp.LevelSpec> buildLevelSpecs() {
        List<TrustInfoResp.LevelSpec> list = new ArrayList<>(5);
        for (int i = 0; i <= TL_LEADER; i++) {
            TrustInfoResp.LevelSpec spec = new TrustInfoResp.LevelSpec()
                    .setLevel(i)
                    .setLabel(LEVEL_LABELS[i])
                    .setDescription(LEVEL_DESC[i])
                    .setPrivileges(LEVEL_PRIVILEGES.get(i))
                    .setRequirements(buildRequirements(i));
            list.add(spec);
        }
        return list;
    }

    private Map<String, Long> buildRequirements(int level) {
        if (level == TL_BASIC) {
            TrustLevelProperties.Tl1 c = props.getTl1();
            return Map.of(
                    "daysSinceRegister", (long) c.getRequiresDays(),
                    "postsEnteredRecent", (long) c.getRequiresPostsEntered(),
                    "postsReadRecent", (long) c.getRequiresPostsRead(),
                    "readMinutes", (long) c.getRequiresReadMinutes());
        }
        if (level == TL_MEMBER) {
            TrustLevelProperties.Tl2 c = props.getTl2();
            return Map.of(
                    "daysVisited", (long) c.getRequiresDaysVisited(),
                    "postsEnteredRecent", (long) c.getRequiresPostsEntered(),
                    "postsReadRecent", (long) c.getRequiresPostsRead(),
                    "readMinutes", (long) c.getRequiresReadMinutes(),
                    "likesReceived", (long) c.getRequiresLikesReceived());
        }
        if (level == TL_REGULAR) {
            TrustLevelProperties.Tl3 c = props.getTl3();
            return Map.of(
                    "daysVisitedRecent", (long) c.getRequiresDaysVisited(),
                    "postsReadRecent", (long) c.getRequiresPostsRead(),
                    "likesReceived", (long) c.getRequiresLikesReceived(),
                    "likesGiven", (long) c.getRequiresLikesGiven(),
                    "postsCreated", (long) c.getRequiresPostsCreated());
        }
        return Map.of();
    }
}
