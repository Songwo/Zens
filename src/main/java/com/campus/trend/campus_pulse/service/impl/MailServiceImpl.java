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
            helper.setText(htmlContent, true); // Song：说明
            javaMailSender.send(message);
            log.info("HTML邮件已发送至: {}", to);
        } catch (Exception e) {
            log.error("发送HTML邮件失败: " + to, e);
        }
    }

    @Override
    @Async
    public void sendVerificationCode(String to, String code) {
        log.info("开始发送验证码邮件到: {}", to);
        String subject = "【Zens社区】注册验证码";
        String htmlContent = buildZensVerificationHtml(code, "注册验证", "欢迎加入 Zens 新一代校园社区！");
        sendHtmlMail(to, subject, htmlContent);
        log.info("验证码邮件发送完成: {}", to);
    }

    @Override
    @Async
    public void sendLoginCode(String to, String code) {
        log.info("开始发送登录验证码邮件到: {}", to);
        String subject = "【Zens社区】登录验证码";
        String htmlContent = buildZensVerificationHtml(code, "快速登录", "欢迎回来！检测到您正在尝试登录 Zens 社区。");
        sendHtmlMail(to, subject, htmlContent);
        log.info("登录验证码邮件发送完成: {}", to);
    }

    /**
     * Song：说明
     */
    private String buildZensVerificationHtml(String code, String action, String greeting) {
        // Song：说明
        String logoUrl = "https://api.dicebear.com/7.x/shapes/png?seed=Zens&backgroundColor=000000&radius=10";

        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body style="margin: 0; padding: 0; background-color: #f7f9fc; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;">
                    <table width="100%%" cellpadding="0" cellspacing="0" style="background-color: #f7f9fc; padding: 50px 0;">
                        <tr>
                            <td align="center">
                                <table width="600" cellpadding="0" cellspacing="0" style="background-color: #ffffff; border-radius: 16px; box-shadow: 0 4px 20px rgba(0,0,0,0.05); overflow: hidden;">
                                    <!-- Header with Zens Branding -->
                                    <tr>
                                        <td style="padding: 40px 40px 30px 40px; text-align: center; border-bottom: 1px solid #f0f2f5;">
                                            <img src="%s" alt="Zens Logo" style="width: 56px; height: 56px; border-radius: 12px; margin-bottom: 16px;">
                                            <h1 style="margin: 0; color: #111827; font-size: 26px; font-weight: 700; letter-spacing: -0.5px;">Zens社区</h1>
                                            <p style="margin: 8px 0 0 0; color: #6b7280; font-size: 15px;">探索校园新脉搏</p>
                                        </td>
                                    </tr>
                                    <!-- Content -->
                                    <tr>
                                        <td style="padding: 40px;">
                                            <h2 style="margin: 0 0 16px 0; color: #111827; font-size: 20px; font-weight: 600;">%s</h2>
                                            <p style="margin: 0 0 32px 0; color: #4b5563; font-size: 16px; line-height: 1.6;">%s</p>

                                            <p style="margin: 0 0 12px 0; color: #6b7280; font-size: 14px;">您的 <b>%s</b> 验证码为：</p>

                                            <!-- Verification Code Box (Premium Dark Mode Style) -->
                                            <div style="background: linear-gradient(135deg, #111827 0%%, #374151 100%%); border-radius: 12px; padding: 32px 24px; text-align: center; margin: 0 0 40px 0; box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1);">
                                                <div style="font-size: 40px; font-weight: 700; color: #ffffff; letter-spacing: 12px; font-family: 'SF Mono', 'Courier New', monospace; text-shadow: 0 2px 4px rgba(0,0,0,0.2);">%s</div>
                                            </div>

                                            <!-- Security Notice -->
                                            <div style="background-color: #f3f4f6; border-left: 4px solid #3b82f6; padding: 20px; border-radius: 0 8px 8px 0; margin: 0 0 32px 0;">
                                                <p style="margin: 0; color: #374151; font-size: 14px; line-height: 1.7;">
                                                    <strong style="color: #1d4ed8;">🛡️ 安全提示</strong><br>
                                                    • 验证码有效期为 <b>5 分钟</b><br>
                                                    • 请勿将验证码截图或透露给他人<br>
                                                    • 若非您本人操作，请忽略或联系社区管理员
                                                </p>
                                            </div>

                                            <p style="margin: 0; color: #9ca3af; font-size: 13px; line-height: 1.6; text-align: center;">
                                                本邮件由系统自动发送，请勿直接回复。<br>
                                            </p>
                                        </td>
                                    </tr>
                                    <!-- Footer -->
                                    <tr>
                                        <td style="background-color: #f8fafc; padding: 24px; text-align: center; border-top: 1px solid #f0f2f5;">
                                            <p style="margin: 0; color: #94a3b8; font-size: 13px;">© 2026 Zens社区. All rights reserved.</p>
                                            <p style="margin: 6px 0 0 0; color: #cbd5e1; font-size: 12px;">Made with ❤️ for UI/UX Excellence.</p>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
                """
                .formatted(logoUrl, action, greeting, action, code);
    }
}
