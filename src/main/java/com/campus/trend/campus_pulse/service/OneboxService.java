package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.dto.response.OneboxPreviewResp;

/**
 * Onebox 链接预览服务 —— 抓取目标页 OG meta，识别 provider（YouTube/Bilibili/Twitter/GitHub/Generic）。
 */
public interface OneboxService {

    /** 抓取并解析 URL 的预览信息，结果缓存 1 小时 */
    OneboxPreviewResp fetchAndParse(String url);
}
