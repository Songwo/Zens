package com.campus.trend.campus_pulse.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;

/**
 * 缓存 body 的 request 包装器，给签名校验后下游 controller 继续读取。
 * 一次读出全部 body 字节，后续每次 getInputStream / getReader 都基于该缓存重新构造。
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
    }

    public String cachedBodyAsString() {
        if (cachedBody == null || cachedBody.length == 0) return "";
        return new String(cachedBody, StandardCharsets.UTF_8);
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(cachedBody);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() { return byteArrayInputStream.available() == 0; }
            @Override
            public boolean isReady() { return true; }
            @Override
            public void setReadListener(ReadListener readListener) { /* no-op */ }
            @Override
            public int read() { return byteArrayInputStream.read(); }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
}
