package com.campus.trend.campus_pulse.service;

public interface MailService {
    /**
     * Song：发送简单文本邮件
     * 
     * Song：说明
     * Song：说明
     * Song：说明
     */
    void sendSimpleMail(String to, String subject, String content);

    /**
     * Song：说明
     * 
     * Song：说明
     * Song：说明
     * Song：说明
     */
    void sendHtmlMail(String to, String subject, String htmlContent);

    /**
     * Song：发送验证码邮件（注册用）
     * 
     * Song：说明
     * Song：说明
     */
    void sendVerificationCode(String to, String code);

    /**
     * Song：发送登录验证码邮件
     * 
     * Song：说明
     * Song：说明
     */
    void sendLoginCode(String to, String code);
}
