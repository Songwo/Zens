package com.campus.trend.campus_pulse.utils;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Song：支持业务前缀 + 时间戳 + 随机数
 */
public class IdUtils {

    public static String genId(String prefix) {
        long timestamp = System.currentTimeMillis();
        // Song：6位随机数
        int random = ThreadLocalRandom.current().nextInt(100000, 999999);
        return prefix + "_" + timestamp + random;
    }

    public static String shortId() {
        long timestamp = System.currentTimeMillis();
        int random = ThreadLocalRandom.current().nextInt(100000, 999999);
        return timestamp + String.valueOf(random);
    }
}
