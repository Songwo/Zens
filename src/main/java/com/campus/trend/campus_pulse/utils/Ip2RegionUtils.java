package com.campus.trend.campus_pulse.utils;

import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.xdb.Searcher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;

import java.io.InputStream;

@Slf4j
public class Ip2RegionUtils {

    private static final String UNKNOWN_REGION = "未知";
    private static final String LOCAL_REGION = "本地 IP";
    private static final String PRIVATE_REGION = "内网";
    private static Searcher searcher;

    static {
        try {
            // 从 classpath 加载 xdb 文件并缓存到内存中（xdb 只有 11MB，完全可以直接丢进内存）
            ClassPathResource resource = new ClassPathResource("ip2region.xdb");
            if (!resource.exists()) {
                log.warn("未配置 ip2region.xdb，IP 归属地功能将返回未知");
            } else {
                try (InputStream inputStream = resource.getInputStream()) {
                    byte[] cBuff = FileCopyUtils.copyToByteArray(inputStream);
                    searcher = Searcher.newWithBuffer(cBuff);
                }
                log.info("ip2region 离线词典加载成功");
            }
        } catch (Exception e) {
            log.warn("ip2region 离线词典加载失败，IP 归属地功能将返回未知: {}", e.getMessage());
        }
    }

    /**
     * 根据 IP 获取物理地址
     *
     * @param ip IP地址
     * @return 物理地址，例如 "中国|0|江苏省|南京市|0"，如果解析失败则返回 "未知"
     */
    public static String getRegion(String ip) {
        if (ip == null || ip.isBlank()) {
            return UNKNOWN_REGION;
        }
        String normalized = normalizeIp(ip.trim());
        String specialRegion = resolveSpecialRegion(normalized);
        if (specialRegion != null) return specialRegion;
        if (!isIpv4Address(normalized)) return UNKNOWN_REGION;
        if (searcher == null) return UNKNOWN_REGION;
        try {
            return searcher.search(normalized);
        } catch (Exception e) {
            log.warn("IP 解析物理地址失败: {}", normalized, e);
            return UNKNOWN_REGION;
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
        if (UNKNOWN_REGION.equals(region) || LOCAL_REGION.equals(region) || PRIVATE_REGION.equals(region)) {
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

    private static String resolveSpecialRegion(String ip) {
        String normalized = ip.toLowerCase();
        if ("::1".equals(normalized)
                || "0:0:0:0:0:0:0:1".equals(normalized)) {
            return LOCAL_REGION;
        }
        int[] ipv4 = parseIpv4(normalized);
        if (ipv4 != null && ipv4[0] == 127) return LOCAL_REGION;
        if (isPrivateIpv4(ipv4)
                || normalized.equals("::")
                || normalized.startsWith("fc")
                || normalized.startsWith("fd")
                || normalized.startsWith("fe80:")) {
            return PRIVATE_REGION;
        }
        return null;
    }

    private static String normalizeIp(String ip) {
        return ip.regionMatches(true, 0, "::ffff:", 0, 7) ? ip.substring(7) : ip;
    }

    private static boolean isIpv4Address(String ip) {
        return parseIpv4(ip) != null;
    }

    private static int[] parseIpv4(String ip) {
        String[] parts = ip.split("\\.", -1);
        if (parts.length != 4) return null;
        int[] octets = new int[4];
        try {
            for (int i = 0; i < parts.length; i++) {
                octets[i] = Integer.parseInt(parts[i]);
                if (octets[i] < 0 || octets[i] > 255) return null;
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
        return octets;
    }

    private static boolean isPrivateIpv4(int[] octets) {
        if (octets == null) return false;
        return octets[0] == 10
                || (octets[0] == 100 && octets[1] >= 64 && octets[1] <= 127)
                || (octets[0] == 169 && octets[1] == 254)
                || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)
                || (octets[0] == 192 && octets[1] == 168);
    }
}
