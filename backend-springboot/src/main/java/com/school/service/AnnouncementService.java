package com.school.service;

import com.school.dto.request.AnnouncementRequest;
import com.school.dto.response.AnnouncementResponse;
import com.school.entity.Announcement;
import com.school.entity.Notification;
import com.school.entity.User;
import com.school.enums.NotificationType;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.AnnouncementRepository;
import com.school.repository.NotificationRepository;
import com.school.repository.UserRepository;
import com.school.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Annonces administratives : création, diffusion en notifications, consultation, suppression.
 */
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<AnnouncementResponse> listForCurrentUser() {
        String role = currentRoleName();
        List<Announcement> announcements = role != null
                ? announcementRepository.findByTargetRoleOrTargetRoleIsNullOrderByPinnedDescCreatedAtDesc(role)
                : announcementRepository.findAllByOrderByPinnedDescCreatedAtDesc();
        return announcements.stream().map(AnnouncementResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AnnouncementResponse> listAll() {
        return announcementRepository.findAllByOrderByPinnedDescCreatedAtDesc().stream()
                .map(AnnouncementResponse::from).toList();
    }

    /**
     * Création d'une annonce et diffusion d'une notification à tous les utilisateurs
     * actifs du rôle ciblé (ou tous les utilisateurs si ciblage global).
     */
    @Transactional
    public AnnouncementResponse create(AnnouncementRequest request, HttpServletRequest httpRequest) {
        Announcement announcement = Announcement.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .targetRole(request.getTargetRole())
                .pinned(request.isPinned())
                .createdBy(SecurityUtils.currentUser())
                .build();
        Announcement saved = announcementRepository.save(announcement);

        List<User> targets = userRepository.findAll().stream()
                .filter(User::isEnabled)
                .filter(u -> request.getTargetRole() == null
                        || u.getRoles().stream().anyMatch(r -> r.getName().equals(request.getTargetRole())))
                .toList();

        for (User target : targets) {
            String content = saved.getContent() != null ? saved.getContent() : "";
            notificationRepository.save(Notification.builder()
                    .user(target)
                    .title("Nouvelle annonce : " + saved.getTitle())
                    .message(content.length() > 120 ? content.substring(0, 120) + "..." : content)
                    .type(NotificationType.INFO)
                    .build());
        }

        auditService.log("ANNOUNCEMENT", "Announcement", saved.getId(),
                "Annonce créée : " + saved.getTitle(), httpRequest);
        return AnnouncementResponse.from(saved);
    }

    @Transactional
    public void delete(Long id, HttpServletRequest httpRequest) {
        Announcement announcement = announcementRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Annonce", id));
        announcementRepository.delete(announcement);
        auditService.log("ANNOUNCEMENT_DELETE", "Announcement", id,
                "Annonce supprimée : " + announcement.getTitle(), httpRequest);
    }

    private String currentRoleName() {
        User user = SecurityUtils.currentUser();
        if (user == null || user.getRoles().isEmpty()) {
            return null;
        }
        return user.getRoles().iterator().next().getName();
    }
}