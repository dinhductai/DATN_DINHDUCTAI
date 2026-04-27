package com.microsv.email_service.messaging;

import com.microsv.email_service.config.RabbitMQConfig;
import com.microsv.email_service.dto.message.TaskNotificationMessage;
import com.microsv.email_service.dto.message.TaskNotificationMessage.TaskInfo;
import com.microsv.email_service.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.StringJoiner;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailConsumer {
    
    private final EmailService emailService;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    
    @RabbitListener(queues = RabbitMQConfig.TASK_NOTIFICATION_QUEUE)
    public void handleTaskNotification(TaskNotificationMessage message) {
        log.info("Received task notification for user: {} at email: {}", message.getUserId(), message.getUserEmail());
        
        try {
            String subject = String.format("Your Tasks for Today - %s", message.getDate());
            String htmlContent = buildEmailContent(message);
            
            emailService.sendEmail(message.getUserEmail(), subject, htmlContent);
            log.info("Email sent successfully to user: {}", message.getUserId());
            
        } catch (Exception e) {
            log.error("Failed to process notification for user {}: {}", message.getUserId(), e.getMessage());
        }
    }
    
    private String buildEmailContent(TaskNotificationMessage message) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>");
        html.append("<html>");
        html.append("<head>");
        html.append("<style>");
        html.append("body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }");
        html.append(".container { max-width: 600px; margin: 0 auto; padding: 20px; }");
        html.append("h1 { color: #2c3e50; border-bottom: 2px solid #3498db; padding-bottom: 10px; }");
        html.append(".task-item { background: #f9f9f9; border-left: 4px solid #3498db; padding: 15px; margin: 10px 0; }");
        html.append(".priority-high { border-left-color: #e74c3c; }");
        html.append(".priority-medium { border-left-color: #f39c12; }");
        html.append(".priority-low { border-left-color: #27ae60; }");
        html.append(".task-title { font-weight: bold; font-size: 16px; margin-bottom: 5px; }");
        html.append(".task-details { color: #666; font-size: 14px; }");
        html.append(".footer { margin-top: 20px; padding-top: 20px; border-top: 1px solid #eee; color: #888; font-size: 12px; }");
        html.append("</style>");
        html.append("</head>");
        html.append("<body>");
        html.append("<div class='container'>");
        html.append("<h1>Your Tasks for Today</h1>");
        html.append("<p>Hello,</p>");
        html.append("<p>Here are your tasks for today, <strong>").append(message.getDate()).append("</strong>:</p>");
        html.append("<div class='tasks'>");
        
        for (TaskInfo task : message.getTasks()) {
            String priorityClass = getPriorityClass(task.getPriority());
            String deadlineStr = formatDeadline(task.getDeadline());
            
            html.append("<div class='task-item ").append(priorityClass).append("'>");
            html.append("<div class='task-title'>").append(escapeHtml(task.getTitle())).append("</div>");
            html.append("<div class='task-details'>");
            html.append("<p><strong>Priority:</strong> ").append(task.getPriority()).append("</p>");
            html.append("<p><strong>Deadline:</strong> ").append(deadlineStr).append("</p>");
            if (task.getDescription() != null && !task.getDescription().isEmpty()) {
                html.append("<p><strong>Description:</strong> ").append(escapeHtml(task.getDescription())).append("</p>");
            }
            html.append("</div>");
            html.append("</div>");
        }
        
        html.append("</div>");
        html.append("<div class='footer'>");
        html.append("<p>This is an automated email from Smart Schedule. Please don't reply to this email.</p>");
        html.append("</div>");
        html.append("</div>");
        html.append("</body>");
        html.append("</html>");
        
        return html.toString();
    }
    
    private String getPriorityClass(String priority) {
        if (priority == null) return "";
        return switch (priority.toUpperCase()) {
            case "HIGH" -> "priority-high";
            case "MEDIUM" -> "priority-medium";
            case "LOW" -> "priority-low";
            default -> "";
        };
    }
    
    private String formatDeadline(OffsetDateTime deadline) {
        if (deadline == null) return "No deadline";
        return deadline.atZoneSameInstant(ZoneId.systemDefault())
                .format(DATE_FORMATTER);
    }
    
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}
