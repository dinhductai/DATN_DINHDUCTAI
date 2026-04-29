package com.microsv.email_service.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    
    public static final String EVENT_CREATION_QUEUE = "event-creation-queue";
    public static final String EVENT_REMINDER_QUEUE = "event-reminder-queue";
    public static final String EVENT_UPDATE_QUEUE = "event-update-queue";
    
    public static final String EVENT_DELETE_QUEUE = "event-delete-queue";
    
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
