package com.campus.trend.campus_pulse.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * 读写分离配置（可选启用）
 * 通过 spring.datasource.read-write-separation.enabled=true 启用
 *
 * 配置示例：
 * spring:
 *   datasource:
 *     read-write-separation:
 *       enabled: true
 *     master:
 *       jdbc-url: jdbc:mysql://master:3306/campus_pulse
 *     slave:
 *       jdbc-url: jdbc:mysql://slave:3306/campus_pulse
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.datasource.read-write-separation", name = "enabled", havingValue = "true")
public class ReadWriteSeparationConfig {

    /**
     * 主库数据源（写操作）
     */
    @Bean(name = "masterDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.master.hikari")
    public DataSource masterDataSource() {
        return DataSourceBuilder.create()
            .type(HikariDataSource.class)
            .build();
    }

    /**
     * 从库数据源（读操作）
     */
    @Bean(name = "slaveDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.slave.hikari")
    public DataSource slaveDataSource() {
        return DataSourceBuilder.create()
            .type(HikariDataSource.class)
            .build();
    }

    /**
     * 动态路由数据源
     */
    @Bean
    @Primary
    public DataSource routingDataSource(
            @Qualifier("masterDataSource") DataSource masterDataSource,
            @Qualifier("slaveDataSource") DataSource slaveDataSource) {

        DynamicRoutingDataSource routingDataSource = new DynamicRoutingDataSource();

        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(DataSourceType.MASTER, masterDataSource);
        targetDataSources.put(DataSourceType.SLAVE, slaveDataSource);

        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(masterDataSource);

        return routingDataSource;
    }

    /**
     * 数据源类型枚举
     */
    public enum DataSourceType {
        MASTER, // 主库（写）
        SLAVE   // 从库（读）
    }

    /**
     * 动态路由数据源实现
     */
    public static class DynamicRoutingDataSource extends AbstractRoutingDataSource {

        private static final ThreadLocal<DataSourceType> contextHolder = new ThreadLocal<>();

        public static void setDataSourceType(DataSourceType type) {
            contextHolder.set(type);
        }

        public static DataSourceType getDataSourceType() {
            return contextHolder.get();
        }

        public static void clearDataSourceType() {
            contextHolder.remove();
        }

        @Override
        protected Object determineCurrentLookupKey() {
            DataSourceType type = getDataSourceType();
            return type != null ? type : DataSourceType.MASTER;
        }
    }
}
