package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.response.InviteGenerateResp;
import com.campus.trend.campus_pulse.dto.response.InviteRecordResp;
import com.campus.trend.campus_pulse.dto.response.LevelInfoResp;
import com.campus.trend.campus_pulse.dto.response.MyInviteResp;
import com.campus.trend.campus_pulse.entity.InviteCode;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.service.InviteCodeService;
import com.campus.trend.campus_pulse.service.LevelService;
import com.campus.trend.campus_pulse.service.UserService;
import com.campus.trend.campus_pulse.utils.PermissionUtils;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/invite")
@RequiredArgsConstructor
public class InviteCodeController {

    private final InviteCodeService inviteCodeService;
    private final LevelService levelService;
    private final UserService userService;

    /** 普通用户生成邀请码所需最低等级 */
    @Value("${campus.invite.user-min-level:1}")
    private int userMinLevel;

    @Value("${campus.invite.max-pending-self-codes:2}")
    private int maxPendingSelfCodes;

    /** 前端站点地址，用于生成邀请链接 */
    @Value("${campus.site.url:http://localhost:5173}")
    private String inviteBaseUrl;

    @Value("${campus.invite.required:false}")
    private boolean inviteRequired;

    @GetMapping("/required")
    public Result<Boolean> required() {
        return Result.success(inviteRequired);
    }

    @GetMapping("/validate")
    public Result<?> validate(@RequestParam String code) {
        return inviteCodeService.validate(code) ? Result.success("邀请码有效") : Result.failed("邀请码无效或已过期");
    }

    @PostMapping("/generate-self")
    public Result<?> generateSelf() {
        String userId = SecurityUtils.getCurrentUserId();
        int level = currentLevel(userId);
        if (level < userMinLevel) {
            return Result.failed("需要达到 Lv" + userMinLevel + " 才能生成邀请码，当前等级 Lv" + level);
        }
        long pendingCount = inviteCodeService.lambdaQuery()
                .eq(InviteCode::getCreatorId, userId)
                .eq(InviteCode::getStatus, 0)
                .count();
        if (pendingCount >= maxPendingSelfCodes) {
            return Result.failed("您还有 " + pendingCount + " 个未使用的邀请码，请先分享后再生成");
        }
        List<String> codes = inviteCodeService.generate(userId, 1, 1, 30, "用户自助生成");
        String code = codes.get(0);
        return Result.success(new InviteGenerateResp(code, buildInviteLink(code), 30));
    }

    @GetMapping("/my")
    public Result<MyInviteResp> myInvites() {
        String userId = SecurityUtils.getCurrentUserId();
        int level = currentLevel(userId);
        List<InviteCode> codes = inviteCodeService.lambdaQuery()
                .eq(InviteCode::getCreatorId, userId)
                .orderByDesc(InviteCode::getCreateTime)
                .list();
        List<InviteRecordResp> records = codes.stream().map(this::toInviteRecord).toList();
        return Result.success(new MyInviteResp(records, records.size(), level, userMinLevel,
                level >= userMinLevel, maxPendingSelfCodes));
    }

    @PostMapping("/generate")
    public Result<?> generate(@RequestBody Map<String, Object> body) {
        if (!PermissionUtils.isAdmin()) return Result.failed("无权操作");
        String creatorId = SecurityUtils.getCurrentUserId();
        int count = parseIntOr(body.get("count"), 1);
        int maxUses = parseIntOr(body.get("maxUses"), 1);
        Integer expireDays = body.containsKey("expireDays") ? parseIntOr(body.get("expireDays"), 0) : null;
        String remark = body.containsKey("remark") ? String.valueOf(body.get("remark")) : null;
        return Result.success(inviteCodeService.generate(creatorId, count, maxUses, expireDays, remark));
    }

    @PostMapping("/disable")
    public Result<?> disable(@RequestBody Map<String, String> body) {
        String code = body.get("code");
        if (!StringUtils.hasText(code)) return Result.failed("缺少邀请码参数");
        if (!PermissionUtils.isAdmin()) return Result.failed("无权操作");
        inviteCodeService.disable(code);
        return Result.success();
    }

    @GetMapping("/list")
    public Result<?> list() {
        if (!PermissionUtils.isAdmin()) return Result.failed("无权操作");
        return Result.success(inviteCodeService.listAll());
    }

    private int currentLevel(String userId) {
        LevelInfoResp info = levelService.getUserLevelInfo(userId);
        return info != null && info.getLevel() != null ? info.getLevel() : 1;
    }

    private String buildInviteLink(String code) {
        return inviteBaseUrl + "/auth?type=register&invite=" + code;
    }

    private InviteRecordResp toInviteRecord(InviteCode c) {
        InviteRecordResp.Invitee invitee = null;
        if (StringUtils.hasText(c.getUsedByUserId())) {
            try {
                User u = userService.getById(c.getUsedByUserId());
                if (u != null) {
                    invitee = new InviteRecordResp.Invitee(
                            u.getId(),
                            StringUtils.hasText(u.getNickname()) ? u.getNickname() : u.getUsername(),
                            u.getAvatar(),
                            u.getLevel(),
                            u.getCreateTime());
                }
            } catch (Exception ignored) {
                // 被邀请人查询失败不影响主流程
            }
        }
        return new InviteRecordResp(
                c.getId(),
                c.getCode(),
                buildInviteLink(c.getCode()),
                c.getStatus(),
                c.getMaxUses(),
                c.getUsedCount(),
                c.getExpireTime(),
                c.getCreateTime(),
                c.getRemark(),
                invitee);
    }

    private static int parseIntOr(Object value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
