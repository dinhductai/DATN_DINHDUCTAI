package com.microsv.task_service.repository;

import com.microsv.task_service.entity.Event;
import jakarta.persistence.Tuple;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    Optional<Event> findByTaskId(Long taskId);
    Optional<Event> findByEventId(Long eventId);
    boolean existsByTaskId(Long taskId);
    List<Event> findByTaskIdIn(List<Long> taskIds);

    // Count total events in current year
    @Query(value = """
        SELECT COUNT(e.event_id)
        FROM events e
        JOIN tasks t ON e.task_id = t.task_id
        WHERE EXTRACT(YEAR FROM t.start_time) = EXTRACT(YEAR FROM CURRENT_DATE)
        """, nativeQuery = true)
    Long countEventsInCurrentYear();

    // Count personal events (no invited emails = personal event)
    @Query(value = """
        SELECT COUNT(e.event_id)
        FROM events e
        JOIN tasks t ON e.task_id = t.task_id
        WHERE EXTRACT(YEAR FROM t.start_time) = EXTRACT(YEAR FROM CURRENT_DATE)
        AND (e.invited_emails IS NULL OR e.invited_emails = '' OR e.invited_emails = '[]')
        """, nativeQuery = true)
    Long countPersonalEventsInCurrentYear();

    // Count group events (has invited emails = group event)
    @Query(value = """
        SELECT COUNT(e.event_id)
        FROM events e
        JOIN tasks t ON e.task_id = t.task_id
        WHERE EXTRACT(YEAR FROM t.start_time) = EXTRACT(YEAR FROM CURRENT_DATE)
        AND e.invited_emails IS NOT NULL
        AND e.invited_emails != ''
        AND e.invited_emails != '[]'
        """, nativeQuery = true)
    Long countGroupEventsInCurrentYear();

    // Count events by priority in current year
    @Query(value = """
        SELECT t.priority as priorityLevel, COUNT(e.event_id) as eventCount
        FROM events e
        JOIN tasks t ON e.task_id = t.task_id
        WHERE EXTRACT(YEAR FROM t.start_time) = EXTRACT(YEAR FROM CURRENT_DATE)
        GROUP BY t.priority
        """, nativeQuery = true)
    List<Tuple> countEventsByPriorityInCurrentYear();

    // Get upcoming events for user (future events sorted by start_time ASC)
    @Query(value = """
        SELECT e.event_id as eventId, e.task_id as taskId, e.event_description as eventDescription,
               e.link_event as linkEvent, e.location as location, e.is_online as isOnline,
               e.reminder_minutes_before as reminderMinutesBefore, e.invited_emails as invitedEmails,
               t.task_id as taskId2, t.title as title, t.description as description,
               t.deadline as deadline, t.status as status, t.priority as priority,
               t.start_time as startTime, t.completed_at as completedAt, t.user_id as userId
        FROM events e
        JOIN tasks t ON e.task_id = t.task_id
        WHERE t.user_id = :userId
        AND t.start_time > CURRENT_TIMESTAMP
        ORDER BY t.start_time ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<Tuple> findUpcomingEventsByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    // Get all events for user
    @Query(value = """
        SELECT e.event_id as eventId, e.task_id as taskId, e.event_description as eventDescription,
               e.link_event as linkEvent, e.location as location, e.is_online as isOnline,
               e.reminder_minutes_before as reminderMinutesBefore, e.invited_emails as invitedEmails,
               t.task_id as taskId2, t.title as title, t.description as description,
               t.deadline as deadline, t.status as status, t.priority as priority,
               t.start_time as startTime, t.completed_at as completedAt, t.user_id as userId
        FROM events e
        JOIN tasks t ON e.task_id = t.task_id
        WHERE t.user_id = :userId
        ORDER BY t.start_time DESC
        """, nativeQuery = true)
    List<Tuple> findAllEventsByUserId(@Param("userId") Long userId);
}
