package com.school.repository;

import com.school.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByOrderByCreatedAtDesc(Pageable pageable);

    Page<AuditLog> findByUsernameIgnoreCaseContaining(String username, Pageable pageable);

    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:username IS NULL OR LOWER(a.username) LIKE LOWER(CONCAT('%', :username, '%')))
              AND (:action IS NULL OR a.action = :action)
              AND (:entity IS NULL OR a.entity = :entity)
              AND (:from IS NULL OR a.createdAt >= :from)
              AND (:to IS NULL OR a.createdAt <= :to)
            ORDER BY a.createdAt DESC
            """)
    Page<AuditLog> search(@Param("username") String username,
                          @Param("action") String action,
                          @Param("entity") String entity,
                          @Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to,
                          Pageable pageable);
}
