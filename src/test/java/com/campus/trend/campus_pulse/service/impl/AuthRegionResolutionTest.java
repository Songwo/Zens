package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.utils.Ip2RegionUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AuthRegionResolutionTest {

    @Test
    void resolveActiveRegion_shouldReturnGeographicRegionForPublicIp() {
        String ip = "114.114.114.114";

        String region = AuthServiceImpl.resolveActiveRegion(ip);

        assertNotNull(region);
        assertNotEquals(ip, region);
        assertNotEquals("未知", region);
        assertFalse(region.contains("|"));
        assertEquals("江苏省 南京市", region);
    }

    @Test
    void resolveActiveRegion_shouldDescribeLocalAndPrivateAddresses() {
        assertEquals("本地开发环境", AuthServiceImpl.resolveActiveRegion("127.0.0.1"));
        assertEquals("本地开发环境", AuthServiceImpl.resolveActiveRegion("127.0.0.8"));
        assertEquals("本地开发环境", AuthServiceImpl.resolveActiveRegion("::1"));
        assertEquals("本地开发环境", AuthServiceImpl.resolveActiveRegion("::ffff:127.0.0.8"));
        assertEquals("内网", AuthServiceImpl.resolveActiveRegion("10.0.0.8"));
        assertEquals("内网", AuthServiceImpl.resolveActiveRegion("172.20.1.8"));
        assertEquals("内网", AuthServiceImpl.resolveActiveRegion("192.168.1.8"));
        assertEquals("内网", AuthServiceImpl.resolveActiveRegion("fd00::8"));
    }

    @Test
    void resolveActiveRegion_shouldNotExposeUnknownOrInvalidIpAsRegion() {
        assertNull(AuthServiceImpl.resolveActiveRegion(null));
        assertNull(AuthServiceImpl.resolveActiveRegion("unknown"));
        assertNull(AuthServiceImpl.resolveActiveRegion("not-an-ip"));
        assertNull(AuthServiceImpl.resolveActiveRegion("2001:db8::8"));
    }

    @Test
    void getShortRegion_shouldKeepSpecialAddressLabelsStable() {
        assertEquals("本地 IP", Ip2RegionUtils.getShortRegion("0:0:0:0:0:0:0:1"));
        assertEquals("内网", Ip2RegionUtils.getShortRegion("169.254.10.20"));
        assertEquals("内网", Ip2RegionUtils.getShortRegion("100.64.10.20"));
    }

    @Test
    void getShortRegion_shouldSupportModernAndLegacyDataLayouts() {
        assertEquals("浙江省 杭州市", Ip2RegionUtils.formatRegion("中国|浙江省|杭州市|电信|CN"));
        assertEquals("United States California", Ip2RegionUtils.formatRegion("United States|California|0|Google LLC|US"));
        assertEquals("江苏省 南京市", Ip2RegionUtils.formatRegion("中国|0|江苏省|南京市|电信"));
        assertEquals("江苏省 南京市", Ip2RegionUtils.formatRegion("中国|华东|江苏省|南京市|电信"));
    }

    @Test
    void resolveActiveRegion_shouldSupportPublicIpv6() {
        String region = AuthServiceImpl.resolveActiveRegion("2001:4860:4860::8888");
        assertNotNull(region);
        assertNotEquals("未知", region);
        assertFalse(region.contains("|"));
    }
}
