package com.microsv.email_service.repository;

import com.microsv.email_service.entity.InvitedEmail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvitedEmailRepository extends JpaRepository<InvitedEmail, Long> {
    
    List<InvitedEmail> findByEventId(Long eventId);
    
    List<InvitedEmail> findByEventIdIn(List<Long> eventIds);
    
    void deleteByEventId(Long eventId);
    
    boolean existsByEventIdAndEmail(Long eventId, String email);
}
