package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.entity.InviteCode;
import com.campus.trend.campus_pulse.mapper.InviteCodeMapper;
import com.campus.trend.campus_pulse.service.InviteCodeService;
import com.campus.trend.campus_pulse.service.LevelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class InviteCodeServiceImpl extends ServiceImpl<InviteCodeMapper, InviteCode> implements InviteCodeService {

    private final LevelService levelService;

    @Override
    public List<String> generate(String creatorId, int count, int maxUses, Integer expireDays, String remark) {
        if (count <= 0 || count > 100) throw new BusinessException(ResultCode.VALIDATE_FAILED, "生成数量需在1-100之间");
        List<InviteCode> codes = new ArrayList<>();
        List<String> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String code = generateCode();
            InviteCode ic = new InviteCode();
            ic.setCode(code);
            ic.setCreatorId(creatorId);
            ic.setStatus(0);
            ic.setMaxUses(maxUses <= 0 ? 0 : maxUses);
            ic.setUsedCount(0);
            ic.setExpireTime(expireDays != null && expireDays > 0
                    ? LocalDateTime.now().plusDays(expireDays) : null);
            ic.setRemark(remark);
            codes.add(ic);
            result.add(code);
        }
        saveBatch(codes);
        return result;
    }

    @Override
    public boolean validate(String code) {
        if (code == null || code.isBlank()) return false;
        InviteCode ic = getByCode(code);
        return isUsable(ic);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void consume(String code, String userId) {
        if (code == null || code.isBlank()) return;
        InviteCode ic = getByCode(code);
        if (!isUsable(ic)) throw new BusinessException(ResultCode.VALIDATE_FAILED, "邀请码无效或已过期");
        ic.setUsedCount(ic.getUsedCount() + 1);
        ic.setUsedByUserId(userId);
        // 达到最大使用次数则标记已使用
        if (ic.getMaxUses() > 0 && ic.getUsedCount() >= ic.getMaxUses()) {
            ic.setStatus(1);
        }
        ic.setUpdateTime(LocalDateTime.now());
        updateById(ic);
        log.info("邀请码已使用: code={}, userId={}", code, userId);
        // 给邀请人奖励经验
        if (org.springframework.util.StringUtils.hasText(ic.getCreatorId())) {
            try {
                levelService.addExperience(ic.getCreatorId(), 30, "成功邀请新用户");
            } catch (Exception e) {
                log.warn("邀请奖励经验发放失败: creatorId={}, err={}", ic.getCreatorId(), e.getMessage());
            }
        }
    }

    @Override
    public void disable(String code) {
        InviteCode ic = getByCode(code);
        if (ic == null) throw new BusinessException(ResultCode.VALIDATE_FAILED, "邀请码不存在");
        ic.setStatus(2);
        ic.setUpdateTime(LocalDateTime.now());
        updateById(ic);
    }

    @Override
    public List<InviteCode> listAll() {
        return list(new LambdaQueryWrapper<InviteCode>().orderByDesc(InviteCode::getCreateTime));
    }

    private InviteCode getByCode(String code) {
        return getOne(new LambdaQueryWrapper<InviteCode>().eq(InviteCode::getCode, code));
    }

    private boolean isUsable(InviteCode ic) {
        if (ic == null) return false;
        if (ic.getStatus() != 0) return false;
        if (ic.getExpireTime() != null && ic.getExpireTime().isBefore(LocalDateTime.now())) return false;
        if (ic.getMaxUses() > 0 && ic.getUsedCount() >= ic.getMaxUses()) return false;
        return true;
    }

    private String generateCode() {
        // 格式: XXXX-XXXX-XXXX (大写字母+数字，去掉易混淆字符)
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        String uuid = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        for (int i = 0; i < 12; i++) {
            if (i > 0 && i % 4 == 0) sb.append('-');
            int idx = (int)(uuid.charAt(i) % chars.length());
            if (idx < 0) idx += chars.length();
            sb.append(chars.charAt(idx));
        }
        return sb.toString();
    }
}
