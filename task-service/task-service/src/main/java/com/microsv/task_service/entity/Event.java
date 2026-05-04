package com.microsv.task_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Entity
@Table(name = "events", indexes = {
    @Index(name = "idx_events_task_id", columnList = "task_id")
})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "task_id", nullable = false, unique = true)
    private Long taskId;

    @Column(name = "event_description", columnDefinition = "TEXT")
    private String eventDescription;

    @Column(name = "link_event", length = 500)
    private String linkEvent;

    @Column(name = "location", length = 500)
    private String location;

    @Column(name = "is_online")
    @Builder.Default
    private Boolean isOnline = false;

    @Column(name = "reminder_minutes_before")
    @Builder.Default
    private Integer reminderMinutesBefore = 30;

    @Column(name = "invited_emails", columnDefinition = "TEXT")
    private String invitedEmails; // JSON array stored as text
}
