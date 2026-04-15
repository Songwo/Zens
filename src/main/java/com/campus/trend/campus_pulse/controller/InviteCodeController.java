package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.dto.response.LevelInfoResp;
import com.campus.trend.campus_pulse.entity.InviteCode;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.service.InviteCodeService;
import com.campus.trend.campus_pulse.service.LevelService;
import com.campus.trend.campus_pulse.service.UserService;
import com.campus.trend.campus_pulse.utils.PermissionUtils;
import com.campus.trend.campus_pulse.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
    @Value("${campus.invite.user-min-level:5}")
    private int userMinLevel;

    /** 前端站点地址，用于生成邀请链接 */
    @Value("${campus.site.url:http://localhost:5173}")
    private String inviteBaseUrl;

    @Value("${campus.invite.required:false}")
    private boolean inviteRequired;

    /** 是否需要邀请码注册（公开接口） */
    @GetMapping("/required")
    public Result<?> required() {
        return Result.success(inviteRequired);
    }

    /** 校验邀请码（注册前验证，公开接口） */
    @GetMapping("/validate")
    public Result<?> validate(@RequestParam String code) {
        boolean valid = inviteCodeService.validate(code);
        return valid ? Result.success("邀请码有效") : Result.failed("邀请码无效或已过期");
    }

    /** 普通用户生成自己的邀请码（需达到最低等级） */
    @PostMapping("/generate-self")
    public Result<?> generateSelf() {
        String userId = SecurityUtils.getCurrentUserId();
        LevelInfoResp info = levelService.getUserLevelInfo(userId);
        int level = info != null && info.getLevel() != null ? info.getLevel() : 1;
        if (level < userMinLevel) {
            return Result.failed("需要达到 Lv" + userMinLevel + " 才能生成邀请码，当前等级 Lv" + level);
        }
        // 每个用户限制未使用的邀请码数量不超过5个
        long pendingCount = inviteCodeService.lambdaQuery()
                .eq(InviteCode::getCreatorId, userId)
                .eq(InviteCode::getStatus, 0)
                .count();
        if (pendingCount >= 5) {
            return Result.failed("您还有 " + pendingCount + " 个未使用的邀请码，请先分享后再生成");
        }
        List<String> codes = inviteCodeService.generate(userId, 1, 1, 30, "用户自助生成");
        String code = codes.get(0);
        // 构建邀请链接
        String link = inviteBaseUrl + "/auth?type=register&invite=" + code;
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("code", code);
        result.put("link", link);
        result.put("expireDays", 30);
        return Result.success(result);
    }

    /** 查询我的邀请记录 */
    @GetMapping("/my")
    public Result<?> myInvites() {
        String userId = SecurityUtils.getCurrentUserId();
        LevelInfoResp info = levelService.getUserLevelInfo(userId);
        int level = info != null && info.getLevel() != null ? info.getLevel() : 1;
        java.util.List<InviteCode> codes = inviteCodeService.lambdaQuery()
                .eq(InviteCode::getCreatorId, userId)
                .orderByDesc(InviteCode::getCreateTime)
                .list();
        // 丰富被邀请人信息
        java.util.List<java.util.Map<String, Object>> records = codes.stream().map(c -> {
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", c.getId());
            m.put("code", c.getCode());
            m.put("link", inviteBaseUrl + "/auth?type=register&invite=" + c.getCode());
            m.put("status", c.getStatus());
            m.put("maxUses", c.getMaxUses());
            m.put("usedCount", c.getUsedCount());
            m.put("expireTime", c.getExpireTime());
            m.put("createTime", c.getCreateTime());
            m.put("remark", c.getRemark());
            // 被邀请人信息
            if (org.springframework.util.StringUtils.hasText(c.getUsedByUserId())) {
                try {
                    User u = userService.getById(c.getUsedByUserId());
                    if (u != null) {
                        java.util.Map<String, Object> invitee = new java.util.HashMap<>();
                        invitee.put("id", u.getId());
                        invitee.put("nickname", org.springframework.util.StringUtils.hasText(u.getNickname()) ? u.getNickname() : u.getUsername());
                        invitee.put("avatar", u.getAvatar());
                        invitee.put("level", u.getLevel());
                        invitee.put("createTime", u.getCreateTime());
                        m.put("invitee", invitee);
                    }
                } catch (Exception ignored) {}
            }
            return m;
        }).collect(java.util.stream.Collectors.toList());
        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("records", records);
        resp.put("total", records.size());
        resp.put("userLevel", level);
        resp.put("minLevel", userMinLevel);
        resp.put("canGenerate", level >= userMinLevel);
        return Result.success(resp);
    }

    /** 生成邀请码（管理员） */
    @PostMapping("/generate")
    public Result<?> generate(@RequestBody Map<String, Object> body) {
        if (!PermissionUtils.isAdmin()) return Result.failed("无权操作");
        String creatorId = SecurityUtils.getCurrentUserId();
        int count = body.containsKey("count") ? Integer.parseInt(String.valueOf(body.get("count"))) : 1;
        int maxUses = body.containsKey("maxUses") ? Integer.parseInt(String.valueOf(body.get("maxUses"))) : 1;
        Integer expireDays = body.containsKey("expireDays") ? Integer.parseInt(String.valueOf(body.get("expireDays"))) : null;
        String remark = body.containsKey("remark") ? String.valueOf(body.get("remark")) : null;
        List<String> codes = inviteCodeService.generate(creatorId, count, maxUses, expireDays, remark);
        return Result.success(codes);
    }

    /** 禁用邀请码（管理员） */
    @PostMapping("/disable")
    public Result<?> disable(@RequestBody java.util.Map<String, String> body) {
        String code = body.get("code");
        if (!org.springframework.util.StringUtils.hasText(code)) return Result.failed("缺少邀请码参数");
        if (!PermissionUtils.isAdmin()) return Result.failed("无权操作");
        inviteCodeService.disable(code);
        return Result.success();
    }

    /** 查询所有邀请码（管理员） */
    @GetMapping("/list")
    public Result<?> list() {
        if (!PermissionUtils.isAdmin()) return Result.failed("无权操作");
        return Result.success(inviteCodeService.listAll());
    }
}
