package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.api.Result;
import com.campus.trend.campus_pulse.dto.response.OneboxPreviewResp;
import com.campus.trend.campus_pulse.service.OneboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Onebox 链接预览控制器 —— 给前端帖子正文中的外部链接提供 OG 卡片元数据。
 */
@RestController
@RequestMapping("/onebox")
@RequiredArgsConstructor
public class OneboxController {

    private final OneboxService oneboxService;

    @GetMapping("/preview")
    public Result<OneboxPreviewResp> preview(@RequestParam String url) {
        return Result.success(oneboxService.fetchAndParse(url));
    }
}
