package com.microsv.email_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

@Data
@Entity
@Table(name = "invited_emails", indexes = {
    @Index(name = "idx_invited_emails_event_id", columnList = "event_id"),
    @Index(name = "idx_invited_emails_email", columnList = "email")
})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvitedEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "invitation_status", length = 50)
    @Builder.Default
    private String invitationStatus = "PENDING";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", columnDefinition = "timestamptz")
    private OffsetDateTime updatedAt;
}
