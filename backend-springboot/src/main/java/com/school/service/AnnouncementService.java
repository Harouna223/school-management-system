package com.school.service;

import com.school.dto.request.AnnouncementRequest;
import com.school.dto.response.AnnouncementResponse;
import com.school.entity.Announcement;
import com.school.entity.Notification;
import com.school.entity.Role;
import com.school.entity.User;
import com.school.enums.NotificationType;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.AnnouncementRepository;
import com.school.repository.NotificationRepository;
import com.school.repository.RoleRepository;
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

    /**
     * Longueur de {@code notifications.title} (VARCHAR(150)). Le titre d'une notification est
     * « Nouvelle annonce : » + le titre de l'annonce, ce dernier pouvant atteindre 200 caracteres :
     * sans troncature, MySQL rejette l'insertion et annule toute la transaction de creation.
     */
    private static final int TITRE_NOTIFICATION_MAX = 150;

    private final AnnouncementRepository announcementRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
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
     *
     * <p>Le rôle cible est resolu contre la table {@code roles} (insensible a la casse) et stocke
     * sous sa forme canonique. Un rôle inconnu est refuse : auparavant l'annonce était créée et
     * diffusée a personne, sans aucune erreur.</p>
     */
    @Transactional
    public AnnouncementResponse create(AnnouncementRequest request, HttpServletRequest httpRequest) {
        String roleCible = resoudreRoleCible(request.getTargetRole());
        Announcement announcement = Announcement.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .targetRole(roleCible)
                .pinned(request.isPinned())
                .createdBy(SecurityUtils.currentUser())
                .build();
        Announcement saved = announcementRepository.save(announcement);

        List<User> targets = userRepository.findAll().stream()
                .filter(User::isEnabled)
                .filter(u -> roleCible == null
                        || u.getRoles().stream().anyMatch(r -> r.getName().equals(roleCible)))
                .toList();

        String titreNotification = tronquer("Nouvelle annonce : " + saved.getTitle(),
                TITRE_NOTIFICATION_MAX);
        for (User target : targets) {
            String content = saved.getContent() != null ? saved.getContent() : "";
            notificationRepository.save(Notification.builder()
                    .user(target)
                    .title(titreNotification)
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

    /**
     * Resout le rôle cible saisi par l'utilisateur et le renvoie sous sa forme canonique.
     * {@code null} signifie « tous les utilisateurs » ; toute autre valeur doit correspondre
     * à un rôle existant, sinon l'annonce serait invisible pour tout le monde.
     */
    private String resoudreRoleCible(String demande) {
        if (demande == null) {
            return null;
        }
        return roleRepository.findByNameIgnoreCase(demande.trim())
                .map(Role::getName)
                .orElseThrow(() -> new BusinessException("Rôle cible inconnu : " + demande));
    }

    private static String tronquer(String valeur, int max) {
        return valeur != null && valeur.length() > max ? valeur.substring(0, max) : valeur;
    }

    private String currentRoleName() {
        User user = SecurityUtils.currentUser();
        if (user == null || user.getRoles().isEmpty()) {
            return null;
        }
        return user.getRoles().iterator().next().getName();
    }
}