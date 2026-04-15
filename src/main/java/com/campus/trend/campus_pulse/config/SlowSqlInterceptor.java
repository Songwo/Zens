package com.campus.trend.campus_pulse.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Component
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class})
})
@Slf4j
public class SlowSqlInterceptor implements Interceptor {

    @Value("${campus.observability.slow-sql-ms:300}")
    private long slowSqlMs;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        MappedStatement mappedStatement = (MappedStatement) args[0];
        Object parameterObject = args.length > 1 ? args[1] : null;
        BoundSql boundSql;
        if (args.length >= 6 && args[5] instanceof BoundSql sql) {
            boundSql = sql;
        } else {
            boundSql = mappedStatement.getBoundSql(parameterObject);
        }

        long start = System.currentTimeMillis();
        try {
            return invocation.proceed();
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed >= Math.max(50L, slowSqlMs)) {
                String sql = boundSql != null ? sanitizeSql(boundSql.getSql()) : "";
                log.warn("慢SQL告警: msId={}, cost={}ms, sql={}", mappedStatement.getId(), elapsed, sql);
            }
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // no-op
    }

    private String sanitizeSql(String raw) {
        if (raw == null) {
            return "";
        }
        String sql = raw.replaceAll("\\s+", " ").trim();
        return sql.length() > 400 ? sql.substring(0, 400) + "..." : sql;
    }
}
