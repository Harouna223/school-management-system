package com.school.repository;

import com.school.entity.MessageLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageLogRepository extends JpaRepository<MessageLog, Long> {

    Page<MessageLog> findByChannelOrderByCreatedAtDesc(MessageLog.Channel channel, Pageable pageable);

    Page<MessageLog> findByOrderByCreatedAtDesc(Pageable pageable);
}
