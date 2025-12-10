package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.entity.SysCategory;

import java.util.List;

/**
 * 分类服务接口
 */
public interface CategoryService extends IService<SysCategory> {

    /**
     * 获取所有分类（按排序返回）
     * 
     * @return 分类列表
     */
    List<SysCategory> getAllCategories();

    /**
     * 根据ID获取分类
     * 
     * @param categoryId 分类ID
     * @return 分类实体，不存在返回null
     */
    SysCategory getCategoryById(String categoryId);

    /**
     * 根据code获取分类
     * 
     * @param code 分类代码
     * @return 分类实体
     */
    SysCategory getCategoryByCode(String code);

    /**
     * 创建分类
     * 
     * @param category 分类实体
     * @return 创建后的分类
     */
    SysCategory createCategory(SysCategory category);

    /**
     * 更新分类
     * 
     * @param category 分类实体
     * @return 是否成功
     */
    boolean updateCategory(SysCategory category);

    /**
     * 删除分类（需要检查是否有帖子使用）
     * 
     * @param categoryId 分类ID
     * @return 是否成功
     */
    boolean deleteCategory(String categoryId);

    /**
     * 检查分类是否存在
     * 
     * @param categoryId 分类ID
     * @return 是否存在
     */
    boolean existsById(String categoryId);

    /**
     * 检查分类code是否已存在
     * 
     * @param code 分类代码
     * @return 是否存在
     */
    boolean existsByCode(String code);

    /**
     * 获取分类下的帖子数量
     * 
     * @param categoryId 分类ID
     * @return 帖子数量
     */
    long getPostCountByCategory(String categoryId);

}
