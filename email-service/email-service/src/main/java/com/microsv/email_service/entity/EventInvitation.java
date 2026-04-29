package com.microsv.email_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "event_invitations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventInvitation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "event_id", nullable = false)
    private Long eventId;
    
    @Column(name = "invited_email", nullable = false)
    private String invitedEmail;
    
    @Column(name = "status")
    private String status; // PENDING, SENT, FAILED
    
    @Column(name = "sent_at")
    private LocalDateTime sentAt;
    
    @PrePersist
    public void prePersist() {
        if (this.status == null) {
            this.status = "PENDING";
        }
    }
}
