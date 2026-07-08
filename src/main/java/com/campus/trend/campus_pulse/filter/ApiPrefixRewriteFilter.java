package com.campus.trend.campus_pulse.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 兼容前端固定以 /api 作为 baseURL，同时保留现有 Controller 的无前缀映射。
 * /api/internal/** 仍保留原样，避免破坏内部服务 HMAC 路径签名。
 */
@Component
public class ApiPrefixRewriteFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api";
    private static final String INTERNAL_PREFIX = "/api/internal";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (!StringUtils.hasText(uri)) {
            return true;
        }
        String contextPath = request.getContextPath();
        String apiPrefix = contextPath + API_PREFIX;
        String internalPrefix = contextPath + INTERNAL_PREFIX;
        return !uri.startsWith(apiPrefix) || uri.startsWith(internalPrefix);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String contextPath = request.getContextPath();
        String requestUri = request.getRequestURI();
        String apiPrefix = contextPath + API_PREFIX;
        String strippedRequestUri = requestUri.substring(apiPrefix.length());
        if (!StringUtils.hasText(strippedRequestUri)) {
            strippedRequestUri = "/";
        }

        String servletPath = request.getServletPath();
        String strippedServletPath = stripApiPrefix(servletPath);
        String pathInfo = request.getPathInfo();
        String strippedPathInfo = stripApiPrefix(pathInfo);
        String rewrittenRequestUri = contextPath + strippedRequestUri;

        HttpServletRequest wrappedRequest = new HttpServletRequestWrapper(request) {
            @Override
            public String getRequestURI() {
                return rewrittenRequestUri;
            }

            @Override
            public String getServletPath() {
                return strippedServletPath;
            }

            @Override
            public String getPathInfo() {
                return strippedPathInfo;
            }

            @Override
            public StringBuffer getRequestURL() {
                StringBuilder builder = new StringBuilder();
                builder.append(getScheme()).append("://").append(getServerName());
                if (shouldAppendPort(getScheme(), getServerPort())) {
                    builder.append(':').append(getServerPort());
                }
                builder.append(rewrittenRequestUri);
                return new StringBuffer(builder.toString());
            }
        };

        filterChain.doFilter(wrappedRequest, response);
    }

    private static String stripApiPrefix(String path) {
        if (!StringUtils.hasText(path)) {
            return path;
        }
        if (API_PREFIX.equals(path)) {
            return "/";
        }
        if (path.startsWith(API_PREFIX + "/")) {
            return path.substring(API_PREFIX.length());
        }
        return path;
    }

    private static boolean shouldAppendPort(String scheme, int port) {
        return ("http".equalsIgnoreCase(scheme) && port != 80)
                || ("https".equalsIgnoreCase(scheme) && port != 443);
    }
}
