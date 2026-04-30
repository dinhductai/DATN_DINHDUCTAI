package com.microsv.task_service.repository;

import com.microsv.task_service.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    Optional<Event> findByTaskId(Long taskId);
    Optional<Event> findByEventId(Long eventId);
    boolean existsByTaskId(Long taskId);
    List<Event> findByTaskIdIn(List<Long> taskIds);
}
