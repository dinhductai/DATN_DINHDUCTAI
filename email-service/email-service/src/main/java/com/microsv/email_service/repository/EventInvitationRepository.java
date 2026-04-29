package com.microsv.email_service.repository;

import com.microsv.email_service.entity.EventInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventInvitationRepository extends JpaRepository<EventInvitation, Long> {
    List<EventInvitation> findByEventId(Long eventId);
}
