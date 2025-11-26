package com.campus.trend.campus_pulse.controller;

import com.campus.trend.campus_pulse.entity.SysUser;
import com.campus.trend.campus_pulse.service.SysUserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sys-user")
public class SysUserController {

    public SysUserController(SysUserService sysUserService){
        this.userService = sysUserService;
    }

    private final SysUserService userService;

    @GetMapping("/test")
    public String Test(){
        return "test";
    }

    @GetMapping("/user/{username}")
    public List<SysUser> getUser(@PathVariable String username){
        return userService.searchByUsername(username);
    }

    @GetMapping("/grade/{grade}")
    public List<SysUser> getUserByGrade(@PathVariable int grade){
        return userService.searchByGrade(grade);
    }
}
