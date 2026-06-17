package com.campus.trend.campus_pulse.config;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.support.spring6.http.converter.FastJsonHttpMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * FastJson2 HTTP消息转换器配置
 * 替代默认的Jackson，性能提升2-3倍
 */
@Configuration
public class FastJsonConfig implements WebMvcConfigurer {

    @Bean
    public FastJsonHttpMessageConverter fastJsonHttpMessageConverter() {
        FastJsonHttpMessageConverter converter = new FastJsonHttpMessageConverter();

        // FastJson配置
        com.alibaba.fastjson2.support.config.FastJsonConfig config =
            new com.alibaba.fastjson2.support.config.FastJsonConfig();

        // 序列化特性
        config.setWriterFeatures(
            JSONWriter.Feature.WriteMapNullValue,        // 输出null值
            JSONWriter.Feature.WriteNullListAsEmpty,     // null列表输出为[]
            JSONWriter.Feature.WriteNullStringAsEmpty,   // null字符串输出为""
            JSONWriter.Feature.WriteNullBooleanAsFalse,  // null布尔输出为false
            JSONWriter.Feature.WriteLongAsString,        // Long类型转String（前端精度问题）
            JSONWriter.Feature.PrettyFormat              // 开发环境格式化（生产可关闭）
        );

        // 反序列化特性
        config.setReaderFeatures(
            JSONReader.Feature.SupportAutoType,          // 支持自动类型
            JSONReader.Feature.FieldBased               // 基于字段反序列化
        );

        // 日期格式
        config.setDateFormat("yyyy-MM-dd HH:mm:ss");

        converter.setFastJsonConfig(config);
        converter.setDefaultCharset(StandardCharsets.UTF_8);
        converter.setSupportedMediaTypes(Collections.singletonList(MediaType.APPLICATION_JSON));

        return converter;
    }

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        // 将FastJson转换器添加到首位（优先使用）
        converters.add(0, fastJsonHttpMessageConverter());
    }
}
