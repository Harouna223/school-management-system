package com.school.service;

import com.school.dto.response.NotificationResponse;
import com.school.dto.response.PageResponse;
import com.school.entity.Notification;
import com.school.entity.User;
import com.school.enums.NotificationType;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Notifications : création, lecture, marquage lu.
 *
 * <p>Deux invariants valent d'être verrouillés, et aucun n'est visible à la lecture rapide :</p>
 * <ol>
 *   <li><b>Cloisonnement par utilisateur (IDOR).</b> {@code markAsRead} ne fait pas {@code findById(id)}
 *       mais {@code findByIdAndUserId(id, userId)}. Sans ce scoping, n'importe quel compte authentifié
 *       pourrait marquer comme lue — et donc faire disparaître du compteur — la notification d'un autre.
 *       Le test vérifie à la fois le résultat (404 sur la notification d'autrui) <b>et</b> que la
 *       méthode non scopée n'est jamais appelée.</li>
 *   <li><b>{@code markAsRead} n'appelle pas {@code save()}.</b> Elle mute l'entité et compte sur le
 *       <i>dirty checking</i> de JPA : sans transaction active, la modification est <b>silencieusement
 *       perdue</b>. L'annotation {@code @Transactional} est donc une dépendance fonctionnelle, pas une
 *       décoration — épinglée par réflexion.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService service;

    private static Notification notification(Long id, boolean read) {
        Notification n = Notification.builder()
                .title("Relance de paiement")
                .message("Facture FAC-2026-0001 : solde restant 25000")
                .type(NotificationType.PAYMENT)
                .link("/payments")
                .build();
        n.setId(id);
        n.setRead(read);
        n.setCreatedAt(LocalDateTime.of(2026, 1, 15, 9, 30));
        return n;
    }

    // ------------------------------------------------------------------
    // notify : contenu enregistre
    // ------------------------------------------------------------------

    @Test
    void notify_enregistreUneNotificationNonLue() {
        User destinataire = new User();
        destinataire.setId(7L);

        service.notify(destinataire, "Titre", "Message", NotificationType.PAYMENT, "/payments");

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());

        Notification n = saved.getValue();
        assertThat(n.getUser()).isSameAs(destinataire);
        assertThat(n.getTitle()).isEqualTo("Titre");
        assertThat(n.getMessage()).isEqualTo("Message");
        assertThat(n.getType()).isEqualTo(NotificationType.PAYMENT);
        assertThat(n.getLink()).isEqualTo("/payments");
        assertThat(n.isRead())
                .as("une notification creee doit etre non lue, sinon elle n'apparait jamais dans le compteur")
                .isFalse();
    }

    @Test
    void notify_sansLienEnregistreUnLienNul() {
        service.notify(new User(), "Titre", "Message", NotificationType.WARNING);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getLink()).isNull();
    }

    @Test
    void notify_appelleToujoursLeDepotUneSeuleFois() {
        service.notify(new User(), "Titre", "Message", NotificationType.PAYMENT);

        verify(notificationRepository).save(any(Notification.class));
    }

    // ------------------------------------------------------------------
    // markAsRead : cloisonnement par utilisateur
    // ------------------------------------------------------------------

    @Test
    void markAsRead_chercheLaNotificationScopeeALUtilisateur() {
        when(notificationRepository.findByIdAndUserId(3L, 7L))
                .thenReturn(Optional.of(notification(3L, false)));

        service.markAsRead(7L, 3L);

        verify(notificationRepository).findByIdAndUserId(3L, 7L);
        verify(notificationRepository, never()).findById(anyLong());
    }

    @Test
    void markAsRead_refuseLaNotificationDUnAutreUtilisateur() {
        // La notification 3 existe, mais elle appartient a l'utilisateur 8 : le depot scope ne la rend pas.
        Notification dAutrui = notification(3L, false);
        when(notificationRepository.findByIdAndUserId(3L, 7L)).thenReturn(Optional.empty());
        // Piege : si le service elargissait la recherche (findById), il TROUVERAIT cette notification.
        // lenient() car, dans le code correct, ce stub ne doit justement jamais etre consomme.
        lenient().when(notificationRepository.findById(3L)).thenReturn(Optional.of(dAutrui));

        assertThatThrownBy(() -> service.markAsRead(7L, 3L))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(dAutrui.isRead())
                .as("la notification d'un autre utilisateur ne doit jamais etre modifiee")
                .isFalse();
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void markAsRead_marqueLaNotificationLue() {
        Notification n = notification(3L, false);
        when(notificationRepository.findByIdAndUserId(3L, 7L)).thenReturn(Optional.of(n));

        service.markAsRead(7L, 3L);

        assertThat(n.isRead()).isTrue();
    }

    @Test
    void markAsRead_neMarquePasUneNotificationDejaLue() {
        Notification n = notification(3L, true);
        when(notificationRepository.findByIdAndUserId(3L, 7L)).thenReturn(Optional.of(n));

        service.markAsRead(7L, 3L);

        assertThat(n.isRead()).isTrue();
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void markAsRead_estTransactionnelleCarElleNeSauvegardePas() throws Exception {
        Method method = NotificationService.class.getMethod("markAsRead", Long.class, Long.class);
        Transactional tx = method.getAnnotation(Transactional.class);

        assertThat(tx)
                .as("sans save(), seul le dirty checking applique le changement : il faut une transaction")
                .isNotNull();
    }

    // ------------------------------------------------------------------
    // Delegations
    // ------------------------------------------------------------------

    @Test
    void markAllAsRead_transmetLUtilisateurAuDepot() {
        service.markAllAsRead(7L);

        verify(notificationRepository).markAllAsRead(7L);
    }

    @Test
    void unreadCount_renvoieLeCompteDuDepot() {
        when(notificationRepository.countByUserIdAndReadFalse(7L)).thenReturn(4L);

        assertThat(service.unreadCount(7L)).isEqualTo(4L);
    }

    // ------------------------------------------------------------------
    // getMyNotifications : pagination et mapping
    // ------------------------------------------------------------------

    @Test
    void getMyNotifications_transmetLaPageEtLaTailleDemandees() {
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(7L), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.getMyNotifications(7L, 2, 5);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findByUserIdOrderByCreatedAtDesc(eq(7L), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void getMyNotifications_mappeLesMetadonneesDePagination() {
        Page<Notification> page = new PageImpl<>(
                List.of(notification(1L, false), notification(2L, true)),
                PageRequest.of(0, 10), 25);
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(7L), any(Pageable.class)))
                .thenReturn(page);

        PageResponse<NotificationResponse> result = service.getMyNotifications(7L, 0, 10);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getPage()).isZero();
        assertThat(result.getSize()).isEqualTo(10);
        assertThat(result.getTotalElements()).isEqualTo(25);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.isFirst()).isTrue();
        assertThat(result.isLast()).isFalse();
    }

    @Test
    void getMyNotifications_mappeLesChampsDeChaqueNotification() {
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(7L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(notification(1L, false))));

        NotificationResponse r = service.getMyNotifications(7L, 0, 10).getContent().get(0);

        assertThat(r.getId()).isEqualTo(1L);
        assertThat(r.getTitle()).isEqualTo("Relance de paiement");
        assertThat(r.getMessage()).isEqualTo("Facture FAC-2026-0001 : solde restant 25000");
        assertThat(r.getType()).isEqualTo(NotificationType.PAYMENT);
        assertThat(r.isRead()).isFalse();
        assertThat(r.getLink()).isEqualTo("/payments");
        assertThat(r.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 15, 9, 30));
    }

    @Test
    void getMyNotifications_neRenvoieAucunePageSansNotification() {
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(7L), any(Pageable.class)))
                .thenReturn(Page.empty());

        PageResponse<NotificationResponse> result = service.getMyNotifications(7L, 0, 10);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }
}
