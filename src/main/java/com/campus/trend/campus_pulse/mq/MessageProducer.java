package com.campus.trend.campus_pulse.mq;

import com.campus.trend.campus_pulse.config.RabbitMQConfig;
import com.campus.trend.campus_pulse.dto.mq.EmailMessage;
import com.campus.trend.campus_pulse.dto.mq.NotificationMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MessageProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void sendNotification(String userId, String type, String title, String content, String relatedId, String relatedUserId) {
        NotificationMessage message = NotificationMessage.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .content(content)
                .relatedId(relatedId)
                .relatedUserId(relatedUserId)
                .build();
        
        log.debug("向 MQ 发送通知消息: {}", message);
        rabbitTemplate.convertAndSend(RabbitMQConfig.NOTIFICATION_EXCHANGE, RabbitMQConfig.NOTIFICATION_ROUTING_KEY, message);
    }

    public void sendEmail(String userId, String title, String content) {
        EmailMessage message = EmailMessage.builder()
                .userId(userId)
                .title(title)
                .content(content)
                .build();
        
        log.debug("向 MQ 发送邮件消息: {}", message);
        rabbitTemplate.convertAndSend(RabbitMQConfig.NOTIFICATION_EXCHANGE, RabbitMQConfig.EMAIL_ROUTING_KEY, message);
    }
}
