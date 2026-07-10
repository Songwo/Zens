package com.campus.trend.campus_pulse.utils;

import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.xdb.LongByteArray;
import org.lionsoul.ip2region.xdb.Searcher;
import org.lionsoul.ip2region.xdb.Version;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;

import java.io.InputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class Ip2RegionUtils {

    private static final String UNKNOWN_REGION = "未知";
    private static final String LOCAL_REGION = "本地 IP";
    private static final String PRIVATE_REGION = "内网";
    private static Searcher ipv4Searcher;
    private static Searcher ipv6Searcher;

    static {
        ipv4Searcher = loadSearcher("ip2region.xdb", Version.IPv4);
        ipv6Searcher = loadSearcher("ip2region_v6.xdb", Version.IPv6);
    }

    private static Searcher loadSearcher(String resourceName, Version version) {
        try {
            ClassPathResource resource = new ClassPathResource(resourceName);
            if (!resource.exists()) {
                log.warn("未配置 {}，{} 归属地将返回未知", resourceName, version.name);
                return null;
            }
            try (InputStream inputStream = resource.getInputStream()) {
                byte[] bytes = FileCopyUtils.copyToByteArray(inputStream);
                LongByteArray buffer = new LongByteArray();
                buffer.append(bytes);
                Searcher loaded = Searcher.newWithBuffer(version, buffer);
                log.info("ip2region {} 离线词典加载成功，大小={} bytes", version.name, bytes.length);
                return loaded;
            }
        } catch (Exception e) {
            log.warn("ip2region {} 离线词典加载失败: {}", version.name, e.getMessage());
            return null;
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
        Searcher searcher;
        if (isIpv4Address(normalized)) {
            searcher = ipv4Searcher;
        } else if (isIpv6Address(normalized)) {
            searcher = ipv6Searcher;
        } else {
            return UNKNOWN_REGION;
        }
        if (searcher == null) return UNKNOWN_REGION;
        try {
            // Searcher 不是线程安全实现，登录等低频场景串行查询可避免共享计数器竞争。
            synchronized (searcher) {
                String region = searcher.search(normalized);
                return region == null || region.isBlank() ? UNKNOWN_REGION : region;
            }
        } catch (Exception e) {
            log.warn("IP 解析物理地址失败: {}", e.getMessage());
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
        return formatRegion(region);
    }

    /**
     * 兼容 ip2region 新旧数据格式：
     * - v3: 国家|省份|城市|ISP|国家代码
     * - v2: 国家|区域|省份|城市|ISP
     */
    public static String formatRegion(String region) {
        if (region == null || region.isBlank() || UNKNOWN_REGION.equals(region)) return UNKNOWN_REGION;
        String[] parts = region.split("\\|", -1);
        if (parts.length != 5) {
            return region;
        }

        String country = cleanPart(parts[0]);
        if ("reserved".equalsIgnoreCase(country)) return UNKNOWN_REGION;
        // v3 的最后一段是两位国家代码；v2 的最后一段是 ISP。
        // 不再根据“区域/省份是否同时非空”猜测格式，避免把 v2 的区域名（如“华东”）误当成省份。
        boolean modernFormat = isCountryCode(parts[4]);
        String province = cleanPart(modernFormat ? parts[1] : parts[2]);
        String city = cleanPart(modernFormat ? parts[2] : parts[3]);

        List<String> location = new ArrayList<>();
        if (!"中国".equals(country)) addDistinct(location, country);
        addDistinct(location, province);
        addDistinct(location, city);
        if (location.isEmpty()) addDistinct(location, country);
        return location.isEmpty() ? UNKNOWN_REGION : String.join(" ", location);
    }

    private static String cleanPart(String value) {
        if (value == null) return "";
        String cleaned = value.trim();
        return cleaned.isEmpty() || "0".equals(cleaned) ? "" : cleaned;
    }

    private static boolean isCountryCode(String value) {
        String cleaned = cleanPart(value);
        return cleaned.length() == 2 && cleaned.chars().allMatch(ch ->
                (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z'));
    }

    private static void addDistinct(List<String> values, String value) {
        if (!value.isEmpty() && (values.isEmpty() || !values.get(values.size() - 1).equals(value))) {
            values.add(value);
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

    private static boolean isIpv6Address(String ip) {
        if (ip == null || !ip.contains(":")) return false;
        try {
            InetAddress parsed = InetAddress.getByName(ip);
            return parsed instanceof Inet6Address;
        } catch (Exception ignored) {
            return false;
        }
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
