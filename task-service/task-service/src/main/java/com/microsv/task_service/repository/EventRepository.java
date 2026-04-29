package com.microsv.task_service.repository;

import com.microsv.task_service.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    
    Optional<Event> findByTaskId(Long taskId);
    
    boolean existsByTaskId(Long taskId);
    
    void deleteByTaskId(Long taskId);
}
