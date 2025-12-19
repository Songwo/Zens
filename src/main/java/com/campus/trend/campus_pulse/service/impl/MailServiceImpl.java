package com.campus.trend.campus_pulse.service.impl;

import com.campus.trend.campus_pulse.service.MailService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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
    @Async
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
        }
    }

    @Override
    @Async
    public void sendHtmlMail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true); // true表示HTML格式
            javaMailSender.send(message);
            log.info("HTML邮件已发送至: {}", to);
        } catch (Exception e) {
            log.error("发送HTML邮件失败: " + to, e);
        }
    }

    @Override
    public void sendVerificationCode(String to, String code) {
        String subject = "【校园脉搏】注册验证码";
        String htmlContent = buildVerificationCodeHtml(code, "注册", "欢迎加入校园脉搏社区！");
        sendHtmlMail(to, subject, htmlContent);
    }

    @Override
    public void sendLoginCode(String to, String code) {
        String subject = "【校园脉搏】登录验证码";
        String htmlContent = buildVerificationCodeHtml(code, "登录", "检测到您正在尝试登录");
        sendHtmlMail(to, subject, htmlContent);
    }

    /**
     * 构建简约风格的验证码HTML模板
     */
    private String buildVerificationCodeHtml(String code, String action, String greeting) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body style="margin: 0; padding: 0; background-color: #f5f5f5; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;">
                    <table width="100%%" cellpadding="0" cellspacing="0" style="background-color: #f5f5f5; padding: 40px 0;">
                        <tr>
                            <td align="center">
                                <table width="600" cellpadding="0" cellspacing="0" style="background-color: #ffffff; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1);">
                                    <!-- Header -->
                                    <tr>
                                        <td style="padding: 40px 40px 30px 40px; border-bottom: 1px solid #e5e7eb;">
                                            <h1 style="margin: 0; color: #1f2937; font-size: 24px; font-weight: 600;">校园脉搏</h1>
                                            <p style="margin: 8px 0 0 0; color: #6b7280; font-size: 14px;">Campus Pulse</p>
                                        </td>
                                    </tr>
                                    <!-- Content -->
                                    <tr>
                                        <td style="padding: 40px;">
                                            <p style="margin: 0 0 24px 0; color: #374151; font-size: 16px; line-height: 1.5;">%s</p>
                                            <p style="margin: 0 0 16px 0; color: #6b7280; font-size: 14px;">您的%s验证码为：</p>

                                            <!-- Verification Code Box -->
                                            <div style="background-color: #f9fafb; border: 2px solid #e5e7eb; border-radius: 8px; padding: 24px; text-align: center; margin: 0 0 32px 0;">
                                                <div style="font-size: 32px; font-weight: 600; color: #111827; letter-spacing: 8px; font-family: 'Courier New', monospace;">%s</div>
                                            </div>

                                            <div style="background-color: #fef3c7; border-left: 3px solid #f59e0b; padding: 16px; border-radius: 4px; margin: 0 0 32px 0;">
                                                <p style="margin: 0; color: #92400e; font-size: 13px; line-height: 1.6;">
                                                    <strong>安全提示</strong><br>
                                                    • 验证码有效期为 5 分钟<br>
                                                    • 请勿将验证码透露给他人<br>
                                                    • 如非本人操作，请忽略此邮件
                                                </p>
                                            </div>

                                            <p style="margin: 0; color: #9ca3af; font-size: 12px; line-height: 1.5;">
                                                此邮件由系统自动发送，请勿直接回复。<br>
                                                如有疑问，请联系管理员。
                                            </p>
                                        </td>
                                    </tr>
                                    <!-- Footer -->
                                    <tr>
                                        <td style="background-color: #f9fafb; padding: 20px; text-align: center; border-top: 1px solid #e5e7eb; border-radius: 0 0 8px 8px;">
                                            <p style="margin: 0; color: #9ca3af; font-size: 12px;">© 2025 校园脉搏. All rights reserved.</p>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
                """
                .formatted(greeting, action, code);
    }
}
