package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.common.Result;
import com.campus.trend.campus_pulse.service.mapperservice.SysPostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sys-post")
public class PostController {

    private SysPostService sysPostService;
    @Autowired
    public void setSysPostService(SysPostService sysPostService) {
        this.sysPostService = sysPostService;
    }

    @GetMapping("/{id}")
    public Result<?> searchByPostId(@PathVariable String id) {
        return Result.success(sysPostService.searchByPostId(id));
    }


}
