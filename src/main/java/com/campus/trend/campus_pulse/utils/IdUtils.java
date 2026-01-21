package com.campus.trend.campus_pulse.utils;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 全局唯一ID生成工具类
 * 支持业务前缀 + 时间戳 + 随机数
 */
public class IdUtils {

    /**
     * 全局通用ID生成器
     * @param prefix 业务前缀（如 "POST", "CATE", "USER"）
     * @return POST_1732512345123456
     */
    public static String genId(String prefix) {
        long timestamp = System.currentTimeMillis();
        // 6位随机数
        int random = ThreadLocalRandom.current().nextInt(100000, 999999);
        return prefix + "_" + timestamp + random;
    }

    /**
     * 无前缀的短 ID
     */
    public static String shortId() {
        long timestamp = System.currentTimeMillis();
        int random = ThreadLocalRandom.current().nextInt(100000, 999999);
        return timestamp + String.valueOf(random);
    }
}
