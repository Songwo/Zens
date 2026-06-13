package com.campus.trend.campus_pulse.utils;

import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.xdb.Searcher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;

import java.io.InputStream;

@Slf4j
public class Ip2RegionUtils {

    private static Searcher searcher;

    static {
        try {
            // 从 classpath 加载 xdb 文件并缓存到内存中（xdb 只有 11MB，完全可以直接丢进内存）
            ClassPathResource resource = new ClassPathResource("ip2region.xdb");
            InputStream inputStream = resource.getInputStream();
            byte[] cBuff = FileCopyUtils.copyToByteArray(inputStream);
            searcher = Searcher.newWithBuffer(cBuff);
            log.info("ip2region 离线词典加载成功");
        } catch (Exception e) {
            log.error("ip2region 离线词典加载失败", e);
        }
    }

    /**
     * 根据 IP 获取物理地址
     *
     * @param ip IP地址
     * @return 物理地址，例如 "中国|0|江苏省|南京市|0"，如果解析失败则返回 "未知"
     */
    public static String getRegion(String ip) {
        if (searcher == null || ip == null || ip.isEmpty()) {
            return "未知";
        }
        try {
            // 特殊 IP 处理
            if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
                return "本地 IP";
            }
            return searcher.search(ip);
        } catch (Exception e) {
            log.warn("IP 解析物理地址失败: {}", ip, e);
            return "未知";
        }
    }

    /**
     * 获取精简的省市信息
     *
     * @param ip IP地址
     * @return 例如 "江苏省 南京市"
     */
    public static String getShortRegion(String ip) {
        String region = getRegion(ip);
        if ("未知".equals(region) || "本地 IP".equals(region)) {
            return region;
        }
        // ip2region 的格式：国家|区域|省份|城市|ISP
        String[] parts = region.split("\\|");
        if (parts.length != 5) {
            return region;
        }

        String country = parts[0];
        String province = parts[2];
        String city = parts[3];

        if ("0".equals(province)) {
            province = "";
        }
        if ("0".equals(city)) {
            city = "";
        }

        if ("中国".equals(country)) {
            if (province.isEmpty() && city.isEmpty()) {
                return "中国";
            }
            return (province + " " + city).trim();
        } else {
            return (country + " " + province + " " + city).trim();
        }
    }
}
