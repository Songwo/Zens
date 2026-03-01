package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.response.SectionResp;
import com.campus.trend.campus_pulse.entity.Section;
import com.campus.trend.campus_pulse.service.SectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Song：板块控制器
 */
@Slf4j
@RestController
@RequestMapping("/section")
@Tag(name = "板块管理", description = "板块相关接口")
public class SectionController {

    @Autowired
    private SectionService sectionService;

    @GetMapping("/list")
    @Operation(summary = "获取所有板块")
    public Result<List<SectionResp>> getAllSections() {
        try {
            List<SectionResp> sections = sectionService.getAllSections();
            return Result.success(sections);
        } catch (Exception e) {
            log.error("获取板块列表失败", e);
            return Result.failed("获取失败");
        }
    }

    @GetMapping("/active")
    @Operation(summary = "获取启用的板块")
    public Result<List<SectionResp>> getActiveSections() {
        try {
            List<SectionResp> sections = sectionService.getActiveSections();
            return Result.success(sections);
        } catch (Exception e) {
            log.error("获取启用板块失败", e);
            return Result.failed("获取失败");
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取板块详情")
    public Result<SectionResp> getSectionById(@PathVariable Long id) {
        try {
            SectionResp section = sectionService.getSectionRespById(id);
            if (section == null) {
                return Result.failed("板块不存在");
            }
            return Result.success(section);
        } catch (Exception e) {
            log.error("获取板块详情失败", e);
            return Result.failed("获取失败");
        }
    }

    @PostMapping
    @Operation(summary = "创建板块")
    public Result<Section> createSection(@RequestBody Section section) {
        try {
            Section created = sectionService.createSection(section);
            return Result.success(created);
        } catch (Exception e) {
            log.error("创建板块失败", e);
            return Result.failed("创建失败");
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新板块")
    public Result<Section> updateSection(@PathVariable Long id, @RequestBody Section section) {
        try {
            section.setId(id);
            Section updated = sectionService.updateSection(section);
            return Result.success(updated);
        } catch (IllegalArgumentException e) {
            return Result.failed(e.getMessage());
        } catch (Exception e) {
            log.error("更新板块失败", e);
            return Result.failed("更新失败");
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除板块")
    public Result<Void> deleteSection(@PathVariable Long id) {
        try {
            sectionService.deleteSection(id);
            return Result.success();
        } catch (Exception e) {
            log.error("删除板块失败", e);
            return Result.failed("删除失败");
        }
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "切换板块状态")
    public Result<Void> toggleStatus(@PathVariable Long id, @RequestParam Integer status) {
        try {
            sectionService.toggleSectionStatus(id, status);
            return Result.success();
        } catch (IllegalArgumentException e) {
            return Result.failed(e.getMessage());
        } catch (Exception e) {
            log.error("切换板块状态失败", e);
            return Result.failed("操作失败");
        }
    }
}
