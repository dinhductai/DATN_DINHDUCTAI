package com.microsv.email_service.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "emails")
@Data
public class Email {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String toEmail;

    private String subject;

    @Column(columnDefinition = "TEXT")
    private String body;

    private String status;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    @Column(columnDefinition = "timestamptz")
    private OffsetDateTime sentAt;
}
