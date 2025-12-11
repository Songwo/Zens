package com.campus.trend.campus_pulse.service;

public interface MailService {
    /**
     * 发送简单文本邮件
     * 
     * @param to      接收者邮箱
     * @param subject 主题
     * @param content 内容
     */
    void sendSimpleMail(String to, String subject, String content);

    /**
     * 发送验证码邮件
     * 
     * @param to   接收者邮箱
     * @param code 验证码
     */
    void sendVerificationCode(String to, String code);
}
