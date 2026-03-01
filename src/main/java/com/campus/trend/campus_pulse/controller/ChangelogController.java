package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.entity.Changelog;
import com.campus.trend.campus_pulse.service.ChangelogService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/changelog")
public class ChangelogController {

    private final ChangelogService changelogService;

    public ChangelogController(ChangelogService changelogService) {
        this.changelogService = changelogService;
    }

    /**
     * Song：公开接口：获取所有已发布的发展历程
     */
    @GetMapping("/list")
    public Result<?> getPublishedList() {
        return Result.success(changelogService.getPublishedList());
    }

    /**
     * Song：管理员：创建日志
     */
    @PostMapping
    public Result<?> create(@RequestBody Changelog changelog) {
        changelogService.save(changelog);
        return Result.success("创建成功");
    }

    /**
     * Song：管理员：更新日志
     */
    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @RequestBody Changelog changelog) {
        changelog.setId(id);
        changelogService.updateById(changelog);
        return Result.success("更新成功");
    }

    /**
     * Song：管理员：删除日志
     */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        changelogService.removeById(id);
        return Result.success("删除成功");
    }
}
