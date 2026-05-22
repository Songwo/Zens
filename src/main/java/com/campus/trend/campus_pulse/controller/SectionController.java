package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.response.SectionResp;
import com.campus.trend.campus_pulse.entity.Section;
import com.campus.trend.campus_pulse.service.SectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/section")
@RequiredArgsConstructor
@Tag(name = "板块管理", description = "板块相关接口")
public class SectionController {

    private final SectionService sectionService;

    @GetMapping("/list")
    @Operation(summary = "获取所有板块")
    public Result<List<SectionResp>> getAllSections() {
        return Result.success(sectionService.getAllSections());
    }

    @GetMapping("/active")
    @Operation(summary = "获取启用的板块")
    public Result<List<SectionResp>> getActiveSections() {
        return Result.success(sectionService.getActiveSections());
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取板块详情")
    public Result<SectionResp> getSectionById(@PathVariable Long id) {
        SectionResp section = sectionService.getSectionRespById(id);
        if (section == null) {
            return Result.failed("板块不存在");
        }
        return Result.success(section);
    }

    @PostMapping
    @Operation(summary = "创建板块")
    public Result<Section> createSection(@RequestBody Section section) {
        return Result.success(sectionService.createSection(section));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新板块")
    public Result<Section> updateSection(@PathVariable Long id, @RequestBody Section section) {
        section.setId(id);
        return Result.success(sectionService.updateSection(section));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除板块")
    public Result<Void> deleteSection(@PathVariable Long id) {
        sectionService.deleteSection(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "切换板块状态")
    public Result<Void> toggleStatus(@PathVariable Long id, @RequestParam Integer status) {
        sectionService.toggleSectionStatus(id, status);
        return Result.success();
    }
}
