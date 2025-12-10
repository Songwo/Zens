package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.Result;
import com.campus.trend.campus_pulse.dto.request.CategoryRequest;
import com.campus.trend.campus_pulse.dto.response.CategoryResponse;
import com.campus.trend.campus_pulse.entity.SysCategory;
import com.campus.trend.campus_pulse.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 分类控制器
 */
@RestController
@RequestMapping("/sys-category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * 获取所有分类列表（公开接口）
     */
    @GetMapping("/list")
    public Result<?> getAllCategories() {
        List<SysCategory> categories = categoryService.getAllCategories();
        List<CategoryResponse> responses = categories.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
        return Result.success(responses);
    }

    /**
     * 根据ID获取分类详情
     */
    @GetMapping("/{id}")
    public Result<?> getCategoryById(@PathVariable String id) {
        SysCategory category = categoryService.getCategoryById(id);
        if (category == null) {
            return Result.success(null);
        }
        return Result.success(convertToResponse(category));
    }

    /**
     * 根据code获取分类
     */
    @GetMapping("/code/{code}")
    public Result<?> getCategoryByCode(@PathVariable String code) {
        SysCategory category = categoryService.getCategoryByCode(code);
        if (category == null) {
            return Result.success(null);
        }
        return Result.success(convertToResponse(category));
    }

    /**
     * 创建分类（管理员）
     */
    @PostMapping
    public Result<?> createCategory(@Valid @RequestBody CategoryRequest request) {
        SysCategory category = new SysCategory()
                .setName(request.getName())
                .setCode(request.getCode())
                .setIcon(request.getIcon())
                .setSort(request.getSort() != null ? request.getSort() : 0);

        SysCategory created = categoryService.createCategory(category);
        return Result.success(convertToResponse(created));
    }

    /**
     * 更新分类（管理员）
     */
    @PutMapping
    public Result<?> updateCategory(@Valid @RequestBody CategoryRequest request) {
        if (request.getId() == null || request.getId().isEmpty()) {
            throw new RuntimeException("分类ID不能为空");
        }

        SysCategory category = new SysCategory()
                .setId(request.getId())
                .setName(request.getName())
                .setCode(request.getCode())
                .setIcon(request.getIcon())
                .setSort(request.getSort());

        categoryService.updateCategory(category);
        return Result.success();
    }

    /**
     * 删除分类（管理员）
     */
    @DeleteMapping("/{id}")
    public Result<?> deleteCategory(@PathVariable String id) {
        categoryService.deleteCategory(id);
        return Result.success();
    }

    /**
     * 获取分类下的帖子数量
     */
    @GetMapping("/{id}/post-count")
    public Result<?> getPostCount(@PathVariable String id) {
        long count = categoryService.getPostCountByCategory(id);
        return Result.success(count);
    }

    /**
     * 转换实体为响应DTO
     */
    private CategoryResponse convertToResponse(SysCategory category) {
        CategoryResponse response = new CategoryResponse();
        response.setId(category.getId());
        response.setName(category.getName());
        response.setCode(category.getCode());
        response.setIcon(category.getIcon());
        response.setSort(category.getSort());
        response.setPostCount(categoryService.getPostCountByCategory(category.getId()));
        return response;
    }

}
