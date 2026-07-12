package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.trend.campus_pulse.entity.SupporterEntitlement;
import com.campus.trend.campus_pulse.mapper.SupporterEntitlementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 支持者身份的统一实时查询入口，不写入或覆盖用户自定义徽章。 */
@Service
@RequiredArgsConstructor
public class SupporterEntitlementQueryService {
    private final SupporterEntitlementMapper entitlementMapper;

    public SupporterIdentity findActive(String userId) {
        if (userId == null || userId.isBlank()) return SupporterIdentity.inactive();
        LocalDateTime now = LocalDateTime.now();
        SupporterEntitlement entitlement = entitlementMapper.selectOne(
                new LambdaQueryWrapper<SupporterEntitlement>()
                        .eq(SupporterEntitlement::getUserId, userId)
                        .eq(SupporterEntitlement::getStatus, "ACTIVE")
                        .le(SupporterEntitlement::getStartsAt, now)
                        .gt(SupporterEntitlement::getExpiresAt, now)
                        .orderByDesc(SupporterEntitlement::getExpiresAt)
                        .last("LIMIT 1"));
        if (entitlement == null) return SupporterIdentity.inactive();
        String tier = "supporter_plus_30".equals(entitlement.getPlanCode()) ? "PLUS" : "SUPPORTER";
        return new SupporterIdentity(true, tier, entitlement.getExpiresAt());
    }

    public record SupporterIdentity(boolean active, String tier, LocalDateTime expiresAt) {
        public static SupporterIdentity inactive() { return new SupporterIdentity(false, null, null); }
    }
}
