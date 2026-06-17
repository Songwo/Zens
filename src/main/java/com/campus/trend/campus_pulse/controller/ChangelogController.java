package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.entity.Changelog;
import com.campus.trend.campus_pulse.service.ChangelogService;
import com.campus.trend.campus_pulse.utils.PermissionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
public class ChangelogController {

    private final ChangelogService changelogService;

    public ChangelogController(ChangelogService changelogService) {
        this.changelogService = changelogService;
    }

    /**
     * Song：公开接口：获取所有已发布的发展历程
     */
    @GetMapping({"/changelog/list", "/api/changelog/list"})
    public Result<?> getPublishedList() {
        return Result.success(changelogService.getPublishedList());
    }

    /**
     * Song：管理员：获取全部路线图配置，包含草稿和隐藏项
     */
    @GetMapping({"/admin/changelog/list", "/api/admin/changelog/list"})
    public Result<?> getAdminList() {
        Result<?> denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        return Result.success(changelogService.getAdminList());
    }

    /**
     * Song：管理员：创建日志
     */
    @PostMapping({"/changelog", "/api/changelog"})
    public Result<?> create(@RequestBody Changelog changelog) {
        Result<?> denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        Result<?> invalid = validateAndNormalize(changelog);
        if (invalid != null) {
            return invalid;
        }
        changelogService.save(changelog);
        return Result.success("创建成功");
    }

    /**
     * Song：管理员：更新日志
     */
    @PutMapping({"/changelog/{id}", "/api/changelog/{id}"})
    public Result<?> update(@PathVariable Long id, @RequestBody Changelog changelog) {
        Result<?> denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        Result<?> invalid = validateAndNormalize(changelog);
        if (invalid != null) {
            return invalid;
        }
        changelog.setId(id);
        changelogService.updateById(changelog);
        return Result.success("更新成功");
    }

    /**
     * Song：管理员：删除日志
     */
    @DeleteMapping({"/changelog/{id}", "/api/changelog/{id}"})
    public Result<?> delete(@PathVariable Long id) {
        Result<?> denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        changelogService.removeById(id);
        return Result.success("删除成功");
    }

    private Result<?> validateAndNormalize(Changelog changelog) {
        if (changelog == null) {
            return Result.error(ResultCode.REQUEST_BODY_MISSING, "请求体不能为空");
        }
        if (!StringUtils.hasText(changelog.getVersion())
                || !StringUtils.hasText(changelog.getTitle())
                || !StringUtils.hasText(changelog.getContent())) {
            return Result.error(ResultCode.PARAM_ERROR, "版本号、标题和内容不能为空");
        }

        if (changelog.getStatus() == null) {
            changelog.setStatus(1);
        }
        if (changelog.getSortOrder() == null) {
            changelog.setSortOrder(0);
        }
        if (!StringUtils.hasText(changelog.getRoadmapStatus())) {
            changelog.setRoadmapStatus("released");
        }
        if (!isValidRoadmapStatus(changelog.getRoadmapStatus())) {
            return Result.error(ResultCode.PARAM_ERROR, "上线状态只能是 released、building 或 planned");
        }
        if (!StringUtils.hasText(changelog.getStageLabel())) {
            changelog.setStageLabel(resolveStageLabel(changelog.getRoadmapStatus()));
        }
        if (changelog.getUpgradeEnabled() == null) {
            changelog.setUpgradeEnabled(0);
        }
        return null;
    }

    private boolean isValidRoadmapStatus(String status) {
        return "released".equals(status) || "building".equals(status) || "planned".equals(status);
    }

    private String resolveStageLabel(String status) {
        if ("building".equals(status)) {
            return "建设中";
        }
        if ("planned".equals(status)) {
            return "下一阶段";
        }
        return "已上线";
    }

    private <T> Result<T> requireAdmin() {
        if (!PermissionUtils.isAdmin()) {
            return Result.error(ResultCode.NO_PERMISSION, "仅管理员可管理社区路线图");
        }
        return null;
    }
}
