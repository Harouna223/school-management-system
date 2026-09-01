package com.school.controller;

import com.school.dto.request.AnnouncementRequest;
import com.school.dto.request.MessageRequest;
import com.school.dto.response.AnnouncementResponse;
import com.school.dto.response.ApiResponse;
import com.school.dto.response.MessageResponse;
import com.school.dto.response.NotificationResponse;
import com.school.dto.response.PageResponse;
import com.school.service.AnnouncementService;
import com.school.service.MessageService;
import com.school.service.NotificationService;
import com.school.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Module communication : annonces, notifications et messagerie interne.
 */
@RestController
@RequestMapping("/api/communication")
@RequiredArgsConstructor
@Tag(name = "Communication", description = "Annonces, notifications et messagerie interne")
public class CommunicationController {

    private final NotificationService notificationService;
    private final MessageService messageService;
    private final AnnouncementService announcementService;
    private final com.school.repository.MessageLogRepository messageLogRepository;

    // ---------- Annonces ----------

    @GetMapping("/announcements")
    @Operation(summary = "Annonces visibles par l'utilisateur courant")
    public ResponseEntity<ApiResponse<List<AnnouncementResponse>>> announcements() {
        return ok("Annonces", announcementService.listForCurrentUser());
    }

    @GetMapping("/announcements/all")
    @Operation(summary = "Toutes les annonces (administration)")
    public ResponseEntity<ApiResponse<List<AnnouncementResponse>>> allAnnouncements() {
        return ok("Annonces", announcementService.listAll());
    }

    @PostMapping("/announcements")
    @Operation(summary = "Publier une annonce", description = "Diffuse une notification aux utilisateurs ciblés")
    public ResponseEntity<ApiResponse<AnnouncementResponse>> createAnnouncement(
            @Valid @RequestBody AnnouncementRequest request, HttpServletRequest httpRequest) {
        return ok("Annonce publiée", announcementService.create(request, httpRequest));
    }

    @DeleteMapping("/announcements/{id}")
    @Operation(summary = "Supprimer une annonce")
    public ResponseEntity<ApiResponse<Void>> deleteAnnouncement(
            @PathVariable Long id, HttpServletRequest httpRequest) {
        announcementService.delete(id, httpRequest);
        return ok("Annonce supprimée", null);
    }

    // ---------- Notifications ----------

    @GetMapping("/notifications")
    @Operation(summary = "Mes notifications")
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> notifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ok("Notifications", notificationService.getMyNotifications(
                SecurityUtils.currentUserId(), page, size));
    }

    @GetMapping("/notifications/unread-count")
    @Operation(summary = "Nombre de notifications non lues")
    public ResponseEntity<ApiResponse<Long>> unreadCount() {
        return ok("Compteur non lues", notificationService.unreadCount(SecurityUtils.currentUserId()));
    }

    @PatchMapping("/notifications/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable Long id) {
        notificationService.markAsRead(SecurityUtils.currentUserId(), id);
        return ok("Notification marquée comme lue", null);
    }

    @PatchMapping("/notifications/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllRead() {
        notificationService.markAllAsRead(SecurityUtils.currentUserId());
        return ok("Toutes les notifications sont lues", null);
    }

    // ---------- Messagerie ----------

    @GetMapping("/messages/inbox")
    @Operation(summary = "Messages reçus")
    public ResponseEntity<ApiResponse<PageResponse<MessageResponse>>> inbox(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ok("Boîte de réception", messageService.inbox(SecurityUtils.currentUserId(), page, size));
    }

    @GetMapping("/messages/sent")
    @Operation(summary = "Messages envoyés")
    public ResponseEntity<ApiResponse<PageResponse<MessageResponse>>> sent(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ok("Messages envoyés", messageService.sent(SecurityUtils.currentUserId(), page, size));
    }

    @PostMapping("/messages")
    @Operation(summary = "Envoyer un message")
    public ResponseEntity<ApiResponse<MessageResponse>> send(@Valid @RequestBody MessageRequest request,
                                                             HttpServletRequest httpRequest) {
        return ok("Message envoyé", messageService.send(request, httpRequest));
    }

    @GetMapping("/messages/unread-count")
    @Operation(summary = "Messages non lus")
    public ResponseEntity<ApiResponse<Long>> unreadMessages() {
        return ok("Messages non lus", messageService.unreadCount(SecurityUtils.currentUserId()));
    }

    @PatchMapping("/messages/read-all")
    public ResponseEntity<ApiResponse<Void>> markMessagesRead() {
        messageService.markAllAsRead(SecurityUtils.currentUserId());
        return ok("Messages marqués comme lus", null);
    }

    // ---------- Historique des notifications WhatsApp/SMS/Email ----------

    @GetMapping("/message-logs")
    @Operation(summary = "Historique des envois de notifications (WhatsApp, SMS, Email)")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<com.school.entity.MessageLog>>> messageLogs(
            @RequestParam(required = false) String channel,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by("createdAt").descending());
        org.springframework.data.domain.Page<com.school.entity.MessageLog> result;
        if (channel != null && !channel.isBlank()) {
            try {
                result = messageLogRepository.findByChannelOrderByCreatedAtDesc(
                        com.school.entity.MessageLog.Channel.valueOf(channel.toUpperCase()), pageable);
            } catch (IllegalArgumentException e) {
                result = messageLogRepository.findByOrderByCreatedAtDesc(pageable);
            }
        } else {
            result = messageLogRepository.findByOrderByCreatedAtDesc(pageable);
        }
        return ok("Historique", result);
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).message(message).data(data)
                .timestamp(LocalDateTime.now()).build());
    }
}