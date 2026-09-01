package com.school.service;

import com.school.dto.response.NotificationResponse;
import com.school.dto.response.PageResponse;
import com.school.entity.Notification;
import com.school.entity.User;
import com.school.enums.NotificationType;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestion des notifications : création, consultation, marquage lu.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public void notify(User user, String title, String message, NotificationType type) {
        notify(user, title, message, type, null);
    }

    @Transactional
    public void notify(User user, String title, String message, NotificationType type, String link) {
        Notification notification = Notification.builder()
                .user(user)
                .title(title)
                .message(message)
                .type(type)
                .link(link)
                .build();
        notificationRepository.save(notification);
    }

    public PageResponse<NotificationResponse> getMyNotifications(Long userId, int page, int size) {
        Page<Notification> result = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        return PageResponse.from(result, NotificationResponse::from);
    }

    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository
                .findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Notification", notificationId));
        notification.setRead(true);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsRead(userId);
    }
}