package com.microsv.task_service.messaging;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    
    public static final String TASK_NOTIFICATION_QUEUE = "task-notification-queue";
    public static final String TASK_NOTIFICATION_EXCHANGE = "task-notification-exchange";
    public static final String TASK_NOTIFICATION_ROUTING_KEY = "task.notification";
    
    public static final String EVENT_EMAIL_QUEUE = "event-email-queue";
    public static final String EVENT_EMAIL_EXCHANGE = "event-email-exchange";
    public static final String EVENT_EMAIL_ROUTING_KEY = "event.email";
    
    public static final String EVENT_CREATION_QUEUE = "event-creation-queue";
    public static final String EVENT_CREATION_EXCHANGE = "event-creation-exchange";
    public static final String EVENT_CREATION_ROUTING_KEY = "event.creation";
    
    public static final String EVENT_REMINDER_QUEUE = "event-reminder-queue";
    public static final String EVENT_REMINDER_EXCHANGE = "event-reminder-exchange";
    public static final String EVENT_REMINDER_ROUTING_KEY = "event.reminder";
    
    public static final String EVENT_UPDATE_QUEUE = "event-update-queue";
    public static final String EVENT_UPDATE_EXCHANGE = "event-update-exchange";
    public static final String EVENT_UPDATE_ROUTING_KEY = "event.update";
    
    public static final String EVENT_DELETE_QUEUE = "event-delete-queue";
    public static final String EVENT_DELETE_EXCHANGE = "event-delete-exchange";
    public static final String EVENT_DELETE_ROUTING_KEY = "event.delete";
    
    @Bean
    public Queue taskNotificationQueue() {
        return new Queue(TASK_NOTIFICATION_QUEUE, true);
    }
    
    @Bean
    public Queue eventEmailQueue() {
        return new Queue(EVENT_EMAIL_QUEUE, true);
    }
    
    @Bean
    public Queue eventCreationQueue() {
        return new Queue(EVENT_CREATION_QUEUE, true);
    }
    
    @Bean
    public Queue eventReminderQueue() {
        return new Queue(EVENT_REMINDER_QUEUE, true);
    }
    
    @Bean
    public Queue eventUpdateQueue() {
        return new Queue(EVENT_UPDATE_QUEUE, true);
    }
    
    @Bean
    public Queue eventDeleteQueue() {
        return new Queue(EVENT_DELETE_QUEUE, true);
    }
    
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
    
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }
}
