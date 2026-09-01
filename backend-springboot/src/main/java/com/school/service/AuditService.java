package com.school.service;

import com.school.entity.AuditLog;
import com.school.repository.AuditLogRepository;
import com.school.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Enregistrement centralisé des actions utilisateurs (audit logs).
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void log(String action, String entity, Long entityId, String details,
                    HttpServletRequest request) {
        AuditLog logEntry = AuditLog.builder()
                .user(SecurityUtils.currentUser())
                .username(SecurityUtils.currentUsername())
                .action(action)
                .entity(entity)
                .entityId(entityId)
                .details(details)
                .ipAddress(request != null ? request.getRemoteAddr() : null)
                .build();
        auditLogRepository.save(logEntry);
    }
}