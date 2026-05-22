package com.campus.trend.campus_pulse.service;

public interface MailService {
    /**
     * Song：发送简单文本邮件
     * 
     */
    void sendSimpleMail(String to, String subject, String content);

    void sendHtmlMail(String to, String subject, String htmlContent);

    /**
     * Song：发送验证码邮件（注册用）
     * 
     */
    void sendVerificationCode(String to, String code);

    /**
     * Song：发送登录验证码邮件
     * 
     */
    void sendLoginCode(String to, String code);
}
