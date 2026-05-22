package com.campus.trend.campus_pulse.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Song：配置分页插件等
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * Song：添加分页插件支持
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // Song：添加分页插件
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);

        paginationInterceptor.setOverflow(false);

        // Song：设置最大单页限制数量，默认500条，-1不受限制
        paginationInterceptor.setMaxLimit(500L);

        interceptor.addInnerInterceptor(paginationInterceptor);

        return interceptor;
    }
}
