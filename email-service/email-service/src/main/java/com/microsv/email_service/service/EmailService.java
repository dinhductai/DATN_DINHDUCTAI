package com.microsv.email_service.service;

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

import java.time.LocalDateTime;
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
            email.setSentAt(LocalDateTime.now());
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
    
    public List<Email> getEmailsByUserId(Long userId) {
        return emailRepository.findByUserId(userId);
    }
}
