package com.microsv.task_service.messaging;

import com.microsv.task_service.dto.message.TaskNotificationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskNotificationProducer {
    
    private final RabbitTemplate rabbitTemplate;
    
    public void sendTaskNotification(TaskNotificationMessage message) {
        try {
            log.info("Sending task notification to RabbitMQ for user: {}", message.getUserId());
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.TASK_NOTIFICATION_QUEUE,
                message
            );
            log.info("Task notification sent successfully for user: {}", message.getUserId());
        } catch (Exception e) {
            log.error("Failed to send task notification for user {}: {}", message.getUserId(), e.getMessage());
        }
    }
}
