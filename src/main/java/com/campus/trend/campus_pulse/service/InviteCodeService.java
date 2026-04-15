package com.campus.trend.campus_pulse.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.trend.campus_pulse.entity.InviteCode;

import java.util.List;

public interface InviteCodeService extends IService<InviteCode> {

    /** 生成一批邀请码 */
    List<String> generate(String creatorId, int count, int maxUses, Integer expireDays, String remark);

    /** 校验邀请码是否有效（不消耗） */
    boolean validate(String code);

    /** 消耗邀请码（注册时调用） */
    void consume(String code, String userId);

    /** 禁用邀请码 */
    void disable(String code);

    /** 查询所有邀请码（管理员） */
    List<InviteCode> listAll();
}
