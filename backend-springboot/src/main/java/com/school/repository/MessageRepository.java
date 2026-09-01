package com.school.repository;

import com.school.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {

    long countByRecipientIdAndReadFalse(Long recipientId);

    Page<Message> findByRecipientIdOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    Page<Message> findBySenderIdOrderByCreatedAtDesc(Long senderId, Pageable pageable);

    @Modifying
    @Query("UPDATE Message m SET m.read = true WHERE m.recipient.id = :userId AND m.read = false")
    void markAllAsRead(@Param("userId") Long userId);
}
