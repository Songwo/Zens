package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.config.properties.SupporterVoucherProperties;
import com.campus.trend.campus_pulse.dto.request.SupporterVoucherImportReq;
import com.campus.trend.campus_pulse.dto.response.SupporterVoucherGrantResp;
import com.campus.trend.campus_pulse.dto.response.SupporterVoucherImportResp;
import com.campus.trend.campus_pulse.dto.response.SupporterVoucherInventoryResp;
import com.campus.trend.campus_pulse.entity.PaymentOrder;
import com.campus.trend.campus_pulse.entity.SupporterVoucherCode;
import com.campus.trend.campus_pulse.entity.SupporterVoucherGrant;
import com.campus.trend.campus_pulse.mapper.SupporterVoucherCodeMapper;
import com.campus.trend.campus_pulse.mapper.SupporterVoucherGrantMapper;
import com.campus.trend.campus_pulse.service.SupporterVoucherCrypto;
import com.campus.trend.campus_pulse.service.SupporterVoucherService;
import com.campus.trend.campus_pulse.utils.PermissionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupporterVoucherServiceImpl implements SupporterVoucherService {
    private static final String PENDING = "PENDING";
    private static final String ISSUED = "ISSUED";
    private static final String AVAILABLE = "AVAILABLE";
    private static final String ASSIGNED = "ASSIGNED";
    private static final int STANDARD_QUOTA = 30;
    private static final int PLUS_QUOTA = 50;

    private final SupporterVoucherCodeMapper codeMapper;
    private final SupporterVoucherGrantMapper grantMapper;
    private final SupporterVoucherCrypto crypto;
    private final SupporterVoucherProperties properties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createGrantAndTryIssue(PaymentOrder order, LocalDateTime grantedAt) {
        Integer quota = quotaForPlan(order.getPlanCode());
        if (quota == null) return;
        if (!"PAID".equals(order.getStatus())) {
            throw new BusinessException(ResultCode.FAILED, "仅已支付订单可以创建公益站额度权益");
        }
        SupporterVoucherGrant grant = grantMapper.lockBySourceOrderNo(order.getOrderNo());
        if (grant == null) {
            grant = SupporterVoucherGrant.builder()
                    .userId(order.getUserId())
                    .sourceOrderNo(order.getOrderNo())
                    .planCode(order.getPlanCode())
                    .quota(quota)
                    .status(PENDING)
                    .redemptionUrlSnapshot(properties.getRedemptionUrl())
                    .grantedAt(grantedAt == null ? LocalDateTime.now() : grantedAt)
                    .build();
            try {
                grantMapper.insert(grant);
            } catch (DuplicateKeyException duplicate) {
                grant = grantMapper.lockBySourceOrderNo(order.getOrderNo());
                if (grant == null) throw duplicate;
            }
        }
        if (PENDING.equals(grant.getStatus())) tryIssue(grant);
    }

    @Override
    public List<SupporterVoucherGrantResp> listMine(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ResultCode.TOKEN_MISSING, "请先登录后查看公益站兑换码");
        }
        List<SupporterVoucherGrant> grants = grantMapper.selectList(
                new LambdaQueryWrapper<SupporterVoucherGrant>()
                        .eq(SupporterVoucherGrant::getUserId, userId)
                        .orderByDesc(SupporterVoucherGrant::getGrantedAt)
                        .orderByDesc(SupporterVoucherGrant::getId));
        if (grants.isEmpty()) return List.of();
        Set<Long> codeIds = new HashSet<>();
        grants.stream().map(SupporterVoucherGrant::getVoucherCodeId)
                .filter(java.util.Objects::nonNull).forEach(codeIds::add);
        var codesById = codeIds.isEmpty() ? java.util.Map.<Long, SupporterVoucherCode>of()
                : codeMapper.selectBatchIds(codeIds).stream()
                .collect(java.util.stream.Collectors.toMap(SupporterVoucherCode::getId, item -> item));
        return grants.stream().map(grant -> {
            String plainCode = null;
            if (ISSUED.equals(grant.getStatus()) && grant.getVoucherCodeId() != null) {
                SupporterVoucherCode code = codesById.get(grant.getVoucherCodeId());
                if (code == null || !ASSIGNED.equals(code.getStatus())) {
                    throw new IllegalStateException("已发放兑换码账本不完整: grantId=" + grant.getId());
                }
                plainCode = crypto.decrypt(code.getCodeCiphertext());
            }
            return new SupporterVoucherGrantResp(grant.getId(), grant.getSourceOrderNo(), grant.getPlanCode(),
                    grant.getQuota(), grant.getStatus(), plainCode, grant.getRedemptionUrlSnapshot(),
                    grant.getGrantedAt(), grant.getIssuedAt());
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupporterVoucherImportResp importCodes(String adminId, SupporterVoucherImportReq request) {
        requireAdmin(adminId);
        int quota = requireQuota(request.quota());
        int imported = 0;
        int duplicates = 0;
        Set<String> requestHashes = new HashSet<>();
        LocalDateTime now = LocalDateTime.now();
        for (String rawCode : request.codes()) {
            String code = normalizeCode(rawCode);
            String hash = crypto.hash(code);
            if (!requestHashes.add(hash) || codeMapper.selectCount(new LambdaQueryWrapper<SupporterVoucherCode>()
                    .eq(SupporterVoucherCode::getCodeHash, hash)) > 0) {
                duplicates++;
                continue;
            }
            try {
                codeMapper.insert(SupporterVoucherCode.builder()
                        .quota(quota)
                        .codeCiphertext(crypto.encrypt(code))
                        .codeHash(hash)
                        .status(AVAILABLE)
                        .importedBy(adminId)
                        .importedAt(now)
                        .build());
                imported++;
            } catch (DuplicateKeyException duplicate) {
                duplicates++;
            }
        }
        int pendingIssued = fillPending(quota);
        log.info("[supporter-voucher] inventory imported adminId={}, quota={}, imported={}, duplicates={}, pendingIssued={}",
                adminId, quota, imported, duplicates, pendingIssued);
        return new SupporterVoucherImportResp(quota, request.codes().size(), imported, duplicates, pendingIssued);
    }

    @Override
    public List<SupporterVoucherInventoryResp> inventory() {
        requireAdmin(null);
        return List.of(inventoryFor(STANDARD_QUOTA), inventoryFor(PLUS_QUOTA));
    }

    private int fillPending(int quota) {
        int issued = 0;
        while (true) {
            SupporterVoucherGrant grant = grantMapper.lockOldestPending(quota);
            if (grant == null || !tryIssue(grant)) return issued;
            issued++;
        }
    }

    private boolean tryIssue(SupporterVoucherGrant grant) {
        SupporterVoucherCode code = codeMapper.lockOldestAvailable(grant.getQuota());
        if (code == null) return false;
        LocalDateTime now = LocalDateTime.now();
        if (codeMapper.assignIfAvailable(code.getId(), grant.getId(), now) != 1) {
            throw new IllegalStateException("兑换码库存发生并发冲突");
        }
        if (grantMapper.issueIfPending(grant.getId(), code.getId(), now) != 1) {
            throw new IllegalStateException("兑换码发放记录发生并发冲突");
        }
        grant.setStatus(ISSUED);
        grant.setVoucherCodeId(code.getId());
        grant.setIssuedAt(now);
        return true;
    }

    private SupporterVoucherInventoryResp inventoryFor(int quota) {
        long available = codeMapper.selectCount(new LambdaQueryWrapper<SupporterVoucherCode>()
                .eq(SupporterVoucherCode::getQuota, quota).eq(SupporterVoucherCode::getStatus, AVAILABLE));
        long assigned = codeMapper.selectCount(new LambdaQueryWrapper<SupporterVoucherCode>()
                .eq(SupporterVoucherCode::getQuota, quota).eq(SupporterVoucherCode::getStatus, ASSIGNED));
        long pending = grantMapper.selectCount(new LambdaQueryWrapper<SupporterVoucherGrant>()
                .eq(SupporterVoucherGrant::getQuota, quota).eq(SupporterVoucherGrant::getStatus, PENDING));
        return new SupporterVoucherInventoryResp(quota, available, assigned, pending);
    }

    private void requireAdmin(String adminId) {
        if (!PermissionUtils.isAdmin()) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "仅管理员可管理公益站兑换码库存");
        }
        if (adminId != null && !StringUtils.hasText(adminId)) {
            throw new BusinessException(ResultCode.TOKEN_MISSING, "管理员登录信息无效");
        }
    }

    private int requireQuota(Integer quota) {
        if (quota == null || (quota != STANDARD_QUOTA && quota != PLUS_QUOTA)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "额度仅支持 30 或 50");
        }
        return quota;
    }

    private String normalizeCode(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim();
        if (code.isEmpty() || code.length() > 256 || code.chars().anyMatch(Character::isISOControl)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "兑换码不能为空、不能包含控制字符且最长 256 字符");
        }
        return code;
    }

    private Integer quotaForPlan(String planCode) {
        if ("supporter_30".equals(planCode)) return STANDARD_QUOTA;
        if ("supporter_plus_30".equals(planCode)) return PLUS_QUOTA;
        return null;
    }
}
