package com.campus.trend.campus_pulse.service.mapperservice.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.entity.SysUser;
import com.campus.trend.campus_pulse.mapper.SysUserMapper;
import com.campus.trend.campus_pulse.service.mapperservice.SysUserService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    /**
     * Mybatis-Plus 的Lambda表达式详解
     * 1.LambdaQueryWrapper Mybatis-plus内置的Lambda的查询
     * 2.Wrappers.lambdaQuery() 等价于 new LambdaQueryWrapper()，Wrappers代表工厂
     * 3.eq(1-字段名字，通常防止字段打错，2-参数)，指的是 Where 字段名 = ？ ,把第二个参数赋值给 ？
     * 4.like，同上可以进行模糊查询
     * 5.inSql,可以进行子查询
     * 6.And/Or，可以嵌套条件进行查询
     * 7.ge(),等价于 >=
    */

    @Override
    public SysUser searchByUsername(String username) {
        return this.getOne(
                Wrappers.lambdaQuery(SysUser.class)
                        .eq(SysUser::getUsername, username),
                false // 不抛异常，返回 null
        );
    }

    @Override
    public List<SysUser> searchByGrade(int grade) {
        LambdaQueryWrapper<SysUser> wrapper = Wrappers.lambdaQuery();
        wrapper.ge(SysUser::getGrade, grade);
        return this.list(wrapper);
    }

    @Override
    public List<SysUser> searchAll() {
        LambdaQueryWrapper<SysUser> wrapper = Wrappers.lambdaQuery();
        return this.list(wrapper);
    }


}

