package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.entity.Category;
import com.campus.trend.campus_pulse.entity.Post;
import com.campus.trend.campus_pulse.mapper.CategoryMapper;
import com.campus.trend.campus_pulse.mapper.PostMapper;
import com.campus.trend.campus_pulse.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 分类服务实现类
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category>
        implements CategoryService {

    private final PostMapper postMapper;

    @Override
    public List<Category> getAllCategories() {
        return lambdaQuery()
                .orderByAsc(Category::getSort)
                .list();
    }

    @Override
    public Category getCategoryById(String categoryId) {
        return getById(categoryId);
    }

    @Override
    public Category getCategoryByCode(String code) {
        return lambdaQuery()
                .eq(Category::getCode, code)
                .one();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Category createCategory(Category category) {
        // 检查code是否已存在
        if (existsByCode(category.getCode())) {
            throw new RuntimeException("分类代码已存在: " + category.getCode());
        }

        save(category);
        log.info("创建分类成功: {} ({})", category.getName(), category.getCode());
        return category;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateCategory(Category category) {
        // 检查是否存在
        Category existing = getById(category.getId());
        if (existing == null) {
            throw new RuntimeException("分类不存在: " + category.getId());
        }

        // 如果修改了code，检查新code是否已被其他分类使用
        if (!existing.getCode().equals(category.getCode()) && existsByCode(category.getCode())) {
            throw new RuntimeException("分类代码已存在: " + category.getCode());
        }

        boolean result = updateById(category);
        log.info("更新分类成功: {}", category.getName());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteCategory(String categoryId) {
        // 检查是否有帖子使用该分类
        long postCount = getPostCountByCategory(categoryId);
        if (postCount > 0) {
            throw new RuntimeException("该分类下有 " + postCount + " 篇帖子，无法删除");
        }

        boolean result = removeById(categoryId);
        log.info("删除分类成功: {}", categoryId);
        return result;
    }

    @Override
    public boolean existsById(String categoryId) {
        return getById(categoryId) != null;
    }

    @Override
    public boolean existsByCode(String code) {
        Long count = lambdaQuery()
                .eq(Category::getCode, code)
                .count();
        return count != null && count > 0;
    }

    @Override
    public long getPostCountByCategory(String categoryId) {
        Long count = postMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Post>()
                        .eq(Post::getCategoryId, categoryId)
                        .eq(Post::getStatus, 1));
        return count != null ? count : 0;
    }

}
