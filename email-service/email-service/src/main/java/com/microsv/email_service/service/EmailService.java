package com.microsv.email_service.service;

import com.microsv.email_service.dto.message.EventReminderMessage;
import com.microsv.email_service.entity.Email;
import com.microsv.email_service.repository.EmailRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    
    private final JavaMailSender mailSender;
    private final EmailRepository emailRepository;
    
    @Value("${spring.mail.username}")
    private String fromEmail;
    
    public void sendEmail(String to, String subject, String htmlContent) {
        Email email = new Email();
        email.setToEmail(to);
        email.setSubject(subject);
        email.setBody(htmlContent);
        email.setUserId(null);
        email.setStatus("PENDING");
        
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            
            email.setStatus("SENT");
            email.setSentAt(OffsetDateTime.now());
            log.info("Email sent successfully to: {}", to);
            
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            email.setStatus("FAILED");
            email.setErrorMessage(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error sending email to {}: {}", to, e.getMessage());
            email.setStatus("FAILED");
            email.setErrorMessage(e.getMessage());
        }
        
        emailRepository.save(email);
    }
    
    public void sendEventReminderEmail(String to, EventReminderMessage message) {
        String subject = "Event Reminder: " + message.getEventDescription();
        String htmlContent = buildEventReminderHtml(message);
        sendEmail(to, subject, htmlContent);
    }
    
    private String buildEventReminderHtml(EventReminderMessage message) {
        String eventType = Boolean.TRUE.equals(message.getIsOnline()) ? "Online" : "In-Person";
        String locationInfo = Boolean.TRUE.equals(message.getIsOnline()) 
                ? "<p><strong>Join Link:</strong> <a href='" + message.getLinkEvent() + "'>" + message.getLinkEvent() + "</a></p>"
                : "<p><strong>Location:</strong> " + message.getLocation() + "</p>";
        String deadlineStr = message.getDeadline() != null ? message.getDeadline().toString() : "Not specified";
        
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; background-color: #f4f4f4; margin: 0; padding: 20px; }
                    .container { background-color: #ffffff; border-radius: 8px; padding: 30px; max-width: 600px; margin: 0 auto; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                    .header { background-color: #FF9800; color: white; padding: 20px; text-align: center; border-radius: 8px 8px 0 0; }
                    .content { padding: 20px; }
                    .event-type { display: inline-block; background-color: #fff3e0; color: #E65100; padding: 5px 15px; border-radius: 20px; font-size: 14px; }
                    .reminder-badge { display: inline-block; background-color: #ffeb3b; color: #333; padding: 5px 15px; border-radius: 20px; font-size: 14px; margin-left: 10px; }
                    .detail-row { margin: 15px 0; padding: 10px; background-color: #f9f9f9; border-radius: 5px; }
                    .detail-label { color: #666; font-size: 12px; text-transform: uppercase; }
                    .detail-value { color: #333; font-size: 16px; font-weight: bold; margin-top: 5px; }
                    .footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Event Reminder!</h1>
                        <span class="event-type">%s Event</span>
                        <span class="reminder-badge">Starting Soon</span>
                    </div>
                    <div class="content">
                        <h2 style="color: #333;">%s</h2>
                        %s
                        <div class="detail-row">
                            <div class="detail-label">Event Time</div>
                            <div class="detail-value">%s</div>
                        </div>
                    </div>
                    <div class="footer">
                        <p>Smart Schedule - Don't miss your event!</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(eventType, message.getEventDescription(), locationInfo, deadlineStr);
    }
    
    public List<Email> getEmailsByUserId(Long userId) {
        return emailRepository.findByUserId(userId);
    }
}
