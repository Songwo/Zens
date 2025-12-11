package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.service.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class MailServiceImpl implements MailService {

    private final JavaMailSender javaMailSender;

    @Value("${spring.mail.username}")
    private String from;

    @Override
    @Async // 异步发送，不阻塞主线程
    public void sendSimpleMail(String to, String subject, String content) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);
            javaMailSender.send(message);
            log.info("邮件已发送至: {}", to);
        } catch (Exception e) {
            log.error("发送邮件失败: " + to, e);
            // 生产环境可能需要重试机制或记录失败日志表
        }
    }

    @Override
    public void sendVerificationCode(String to, String code) {
        String subject = "【校园脉搏】注册验证码";
        String content = "您好！\n\n您的注册验证码是：" + code + "\n有效时间为5分钟，请勿泄露给他人。\n\n如非本人操作，请忽略此邮件。";
        sendSimpleMail(to, subject, content);
    }
}
