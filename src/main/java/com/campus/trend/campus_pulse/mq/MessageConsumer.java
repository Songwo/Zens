package com.campus.trend.campus_pulse.mq;

import com.campus.trend.campus_pulse.config.RabbitMQConfig;
import com.campus.trend.campus_pulse.dto.mq.EmailMessage;
import com.campus.trend.campus_pulse.dto.mq.NotificationMessage;
import com.campus.trend.campus_pulse.service.AsyncTaskService;
import com.campus.trend.campus_pulse.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MessageConsumer {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private AsyncTaskService asyncTaskService;

    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_QUEUE)
    public void handleNotificationMessage(NotificationMessage message) {
        log.debug("MQ 消费者收到通知消息: {}", message);
        try {
            // 调用原始的处理逻辑。注意：这里我们会在 NotificationService 中新增一个 processNotification 方法，
            // 用于真正在消费者里执行 DB 插入和 WebSocket 推送。
            notificationService.processNotification(
                    message.getUserId(),
                    message.getType(),
                    message.getTitle(),
                    message.getContent(),
                    message.getRelatedId(),
                    message.getRelatedUserId()
            );
        } catch (Exception e) {
            log.error("MQ 处理通知消息失败", e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.EMAIL_QUEUE)
    public void handleEmailMessage(EmailMessage message) {
        log.debug("MQ 消费者收到邮件消息: {}", message);
        try {
            // 这里可以直接复用已有的 email 发送逻辑，或者调用 MailService
            asyncTaskService.syncNotificationEmailAsync(
                    message.getUserId(),
                    message.getTitle(),
                    message.getContent()
            );
        } catch (Exception e) {
            log.error("MQ 处理邮件消息失败", e);
        }
    }
}
