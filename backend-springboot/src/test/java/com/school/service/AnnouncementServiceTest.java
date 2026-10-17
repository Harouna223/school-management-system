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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Annonces administratives — creation, diffusion en notifications, visibilite par role.
 *
 * <p><b>Le risque principal est la diffusion :</b> une annonce mal ciblee est soit invisible pour
 * tout le monde, soit visible pour tout le monde. Trois comportements sont donc verrouilles : les
 * comptes desactives ne recoivent rien, un role cible filtre exactement, et l'absence de role cible
 * diffuse a tous les actifs.</p>
 *
 * <p>Deux defauts ont ete corriges et sont desormais verrouilles par des tests :</p>
 * <ol>
 *   <li><b>Depassement de colonne sur le titre.</b> {@code Notification.title} est
 *       {@code VARCHAR(150)} ; le service ecrit {@code "Nouvelle annonce : " + titre} (19 caracteres)
 *       alors que {@code AnnouncementRequest.title} autorise <b>200</b>. Un titre de 132 a 200
 *       caracteres produisait 151 a 219 caracteres : MySQL levait « Data too long » et annulait
 *       toute la transaction. Le titre de la notification est desormais tronque a 150.</li>
 *   <li><b>{@code targetRole} n'etait pas valide.</b> La comparaison etait sensible a la casse et a
 *       la valeur exacte : {@code "enseignant"} ou {@code "PROF"} creaient une annonce que
 *       <i>personne</i> ne voyait, sans erreur. Le role est maintenant resolu contre la table
 *       {@code roles} (insensible a la casse) et stocke sous forme canonique ; un role inconnu
 *       est refuse en 400.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class AnnouncementServiceTest {

    private static final String PREFIXE = "Nouvelle annonce : ";

    @Mock
    private AnnouncementRepository announcementRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private HttpServletRequest httpRequest;

    private AnnouncementService service;
    private MockedStatic<SecurityUtils> security;

    @BeforeEach
    void setUp() {
        service = new AnnouncementService(announcementRepository, notificationRepository,
                userRepository, roleRepository, auditService);
        security = mockStatic(SecurityUtils.class, CALLS_REAL_METHODS);
    }

    @AfterEach
    void tearDown() {
        security.close();
    }

    // ==================================================================
    // Fixtures
    // ==================================================================

    private static Role role(String name) {
        Role r = new Role();
        r.setName(name);
        return r;
    }

    private static User utilisateur(Long id, boolean enabled, String... roles) {
        User u = new User();
        u.setId(id);
        u.setUsername("user" + id);
        u.setFirstName("Jean");
        u.setLastName("Kamdem");
        u.setEnabled(enabled);
        Set<Role> set = new LinkedHashSet<>();
        for (String r : roles) {
            set.add(role(r));
        }
        u.setRoles(set);
        return u;
    }

    private static AnnouncementRequest requete(String titre, String contenu, String roleCible) {
        return AnnouncementRequest.builder()
                .title(titre)
                .content(contenu)
                .targetRole(roleCible)
                .pinned(false)
                .build();
    }

    /** Stubbe la sauvegarde de l'annonce en lui attribuant un identifiant. */
    private void annonceSauveeAvecId(Long id) {
        when(announcementRepository.save(any(Announcement.class))).thenAnswer(inv -> {
            Announcement a = inv.getArgument(0);
            a.setId(id);
            return a;
        });
    }

    private List<Notification> notificationsEnvoyees(int attendues) {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(attendues)).save(captor.capture());
        return captor.getAllValues();
    }

    // ==================================================================
    // 1. Creation et reponse
    // ==================================================================

    @Test
    void create_enregistreLAnnonceAvecLesChampsEtLAuteur() {
        User auteur = utilisateur(1L, true, "DIRECTEUR");
        security.when(SecurityUtils::currentUser).thenReturn(auteur);
        when(roleRepository.findByNameIgnoreCase("PARENT"))
                .thenReturn(Optional.of(role("PARENT")));
        annonceSauveeAvecId(50L);
        when(userRepository.findAll()).thenReturn(List.of());

        service.create(requete("Rentree", "Cours le 5 octobre", "PARENT"), httpRequest);

        ArgumentCaptor<Announcement> captor = ArgumentCaptor.forClass(Announcement.class);
        verify(announcementRepository).save(captor.capture());
        Announcement enregistree = captor.getValue();
        assertThat(enregistree.getTitle()).isEqualTo("Rentree");
        assertThat(enregistree.getContent()).isEqualTo("Cours le 5 octobre");
        assertThat(enregistree.getTargetRole()).isEqualTo("PARENT");
        assertThat(enregistree.isPinned()).isFalse();
        assertThat(enregistree.getCreatedBy()).isSameAs(auteur);
    }

    @Test
    void create_retourneLaReponseMappee() {
        security.when(SecurityUtils::currentUser).thenReturn(utilisateur(1L, true, "DIRECTEUR"));
        annonceSauveeAvecId(50L);
        when(userRepository.findAll()).thenReturn(List.of());

        AnnouncementResponse response = service.create(
                requete("Rentree", "Cours le 5 octobre", null), httpRequest);

        assertThat(response.getId()).isEqualTo(50L);
        assertThat(response.getTitle()).isEqualTo("Rentree");
        assertThat(response.getCreatedByName()).isEqualTo("Jean Kamdem");
    }

    @Test
    void create_journaliseLAuditAvecLIdentifiantEtLeTitre() {
        security.when(SecurityUtils::currentUser).thenReturn(utilisateur(1L, true, "DIRECTEUR"));
        annonceSauveeAvecId(50L);
        when(userRepository.findAll()).thenReturn(List.of());

        service.create(requete("Rentree", "Cours le 5 octobre", null), httpRequest);

        verify(auditService).log("ANNOUNCEMENT", "Announcement", 50L,
                "Annonce créée : Rentree", httpRequest);
    }

    // ==================================================================
    // 2. Diffusion
    // ==================================================================

    @Test
    void create_neNotifiePasLesComptesDesactives() {
        security.when(SecurityUtils::currentUser).thenReturn(utilisateur(1L, true, "DIRECTEUR"));
        annonceSauveeAvecId(50L);
        when(userRepository.findAll()).thenReturn(List.of(
                utilisateur(2L, true, "PARENT"),
                utilisateur(3L, false, "PARENT")));

        service.create(requete("Rentree", "Cours le 5 octobre", null), httpRequest);

        List<Notification> envoyees = notificationsEnvoyees(1);
        assertThat(envoyees.get(0).getUser().getId()).isEqualTo(2L);
    }

    @Test
    void create_sansRoleCible_diffuseATousLesActifs() {
        security.when(SecurityUtils::currentUser).thenReturn(utilisateur(1L, true, "DIRECTEUR"));
        annonceSauveeAvecId(50L);
        when(userRepository.findAll()).thenReturn(List.of(
                utilisateur(2L, true, "PARENT"),
                utilisateur(3L, true, "ENSEIGNANT"),
                utilisateur(4L, true, "ELEVE")));

        service.create(requete("Rentree", "Cours le 5 octobre", null), httpRequest);

        assertThat(notificationsEnvoyees(3))
                .extracting(n -> n.getUser().getId())
                .containsExactly(2L, 3L, 4L);
    }

    @Test
    void create_avecRoleCible_neNotifieQueLesPorteursDeCeRole() {
        security.when(SecurityUtils::currentUser).thenReturn(utilisateur(1L, true, "DIRECTEUR"));
        when(roleRepository.findByNameIgnoreCase("ENSEIGNANT"))
                .thenReturn(Optional.of(role("ENSEIGNANT")));
        annonceSauveeAvecId(50L);
        when(userRepository.findAll()).thenReturn(List.of(
                utilisateur(2L, true, "PARENT"),
                utilisateur(3L, true, "ENSEIGNANT"),
                utilisateur(4L, true, "ELEVE")));

        service.create(requete("Rentree", "Cours le 5 octobre", "ENSEIGNANT"), httpRequest);

        assertThat(notificationsEnvoyees(1))
                .extracting(n -> n.getUser().getId())
                .containsExactly(3L);
    }

    @Test
    void create_normaliseLeRoleCibleEnMajuscules() {
        // "enseignant" doit etre resolu puis stocke sous la forme canonique "ENSEIGNANT" :
        // la lecture filtre sur le nom exact, donc un role stocke en minuscules serait invisible.
        security.when(SecurityUtils::currentUser).thenReturn(utilisateur(1L, true, "DIRECTEUR"));
        when(roleRepository.findByNameIgnoreCase("enseignant"))
                .thenReturn(Optional.of(role("ENSEIGNANT")));
        annonceSauveeAvecId(50L);
        when(userRepository.findAll()).thenReturn(List.of(utilisateur(3L, true, "ENSEIGNANT")));

        service.create(requete("Rentree", "Cours le 5 octobre", "enseignant"), httpRequest);

        ArgumentCaptor<Announcement> captor = ArgumentCaptor.forClass(Announcement.class);
        verify(announcementRepository).save(captor.capture());
        assertThat(captor.getValue().getTargetRole()).isEqualTo("ENSEIGNANT");
        assertThat(notificationsEnvoyees(1))
                .extracting(n -> n.getUser().getId())
                .containsExactly(3L);
    }

    @Test
    void create_refuseUnRoleCibleInconnu() {
        security.when(SecurityUtils::currentUser).thenReturn(utilisateur(1L, true, "DIRECTEUR"));
        when(roleRepository.findByNameIgnoreCase("PROF")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(requete("Rentree", "Cours", "PROF"), httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PROF");

        verify(announcementRepository, never()).save(any(Announcement.class));
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void create_sansRoleCible_neResoutAucunRole() {
        security.when(SecurityUtils::currentUser).thenReturn(utilisateur(1L, true, "DIRECTEUR"));
        annonceSauveeAvecId(50L);
        when(userRepository.findAll()).thenReturn(List.of());

        service.create(requete("Rentree", "Cours", null), httpRequest);

        verify(roleRepository, never()).findByNameIgnoreCase(any());
    }

    // ==================================================================
    // 3. Contenu de la notification
    // ==================================================================

    @Test
    void message_tronqueAuDelaDe120Caracteres() {
        security.when(SecurityUtils::currentUser).thenReturn(utilisateur(1L, true, "DIRECTEUR"));
        annonceSauveeAvecId(50L);
        when(userRepository.findAll()).thenReturn(List.of(utilisateur(2L, true, "PARENT")));
        String contenu = "x".repeat(121);

        service.create(requete("Rentree", contenu, null), httpRequest);

        String message = notificationsEnvoyees(1).get(0).getMessage();
        assertThat(message).isEqualTo("x".repeat(120) + "...").hasSize(123);
    }

    @Test
    void message_conserveLeContenuDe120CaracteresSansPointsDeSuspension() {
        security.when(SecurityUtils::currentUser).thenReturn(utilisateur(1L, true, "DIRECTEUR"));
        annonceSauveeAvecId(50L);
        when(userRepository.findAll()).thenReturn(List.of(utilisateur(2L, true, "PARENT")));
        String contenu = "x".repeat(120);

        service.create(requete("Rentree", contenu, null), httpRequest);

        assertThat(notificationsEnvoyees(1).get(0).getMessage())
                .isEqualTo(contenu)
                .doesNotEndWith("...");
    }

    @Test
    void message_videQuandLeContenuEstNul() {
        security.when(SecurityUtils::currentUser).thenReturn(utilisateur(1L, true, "DIRECTEUR"));
        annonceSauveeAvecId(50L);
        when(userRepository.findAll()).thenReturn(List.of(utilisateur(2L, true, "PARENT")));

        service.create(requete("Rentree", null, null), httpRequest);

        assertThat(notificationsEnvoyees(1).get(0).getMessage()).isEmpty();
    }

    @Test
    void notification_porteLeTypeInfoEtLeTitrePrefixe() {
        security.when(SecurityUtils::currentUser).thenReturn(utilisateur(1L, true, "DIRECTEUR"));
        annonceSauveeAvecId(50L);
        when(userRepository.findAll()).thenReturn(List.of(utilisateur(2L, true, "PARENT")));

        service.create(requete("Rentree", "Cours le 5 octobre", null), httpRequest);

        Notification n = notificationsEnvoyees(1).get(0);
        assertThat(n.getType()).isEqualTo(NotificationType.INFO);
        assertThat(n.getTitle()).isEqualTo(PREFIXE + "Rentree");
        assertThat(n.isRead()).isFalse();
    }

    @Test
    void titreLong_estTronqueALaTailleDeLaColonne() {
        // notifications.title est VARCHAR(150). Le titre maximal autorise (200) plus le prefixe
        // de 19 caracteres donnerait 219 : sans troncature, MySQL annule toute la creation.
        security.when(SecurityUtils::currentUser).thenReturn(utilisateur(1L, true, "DIRECTEUR"));
        annonceSauveeAvecId(50L);
        when(userRepository.findAll()).thenReturn(List.of(utilisateur(2L, true, "PARENT")));

        service.create(requete("T".repeat(200), "Cours", null), httpRequest);

        assertThat(notificationsEnvoyees(1).get(0).getTitle())
                .as("jamais plus que notifications.title (VARCHAR(150))")
                .hasSize(150);
    }

    @Test
    void titreDe131Caracteres_tientExactementDansLaColonne() {
        // Frontiere : 19 + 131 = 150, donc aucune troncature ne doit s'appliquer.
        security.when(SecurityUtils::currentUser).thenReturn(utilisateur(1L, true, "DIRECTEUR"));
        annonceSauveeAvecId(50L);
        when(userRepository.findAll()).thenReturn(List.of(utilisateur(2L, true, "PARENT")));

        service.create(requete("T".repeat(131), "Cours", null), httpRequest);

        assertThat(notificationsEnvoyees(1).get(0).getTitle())
                .isEqualTo(PREFIXE + "T".repeat(131))
                .hasSize(150);
    }

    // ==================================================================
    // 4. Suppression
    // ==================================================================

    @Test
    void delete_leveUneExceptionSiLAnnonceExistePas() {
        when(announcementRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(404L, httpRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("404");
    }

    @Test
    void delete_journaliseLAuditApresLaSuppression() {
        Announcement existante = Announcement.builder().id(50L).title("Rentree").build();
        when(announcementRepository.findById(50L)).thenReturn(Optional.of(existante));

        service.delete(50L, httpRequest);

        InOrder ordre = inOrder(announcementRepository, auditService);
        ordre.verify(announcementRepository).delete(existante);
        ordre.verify(auditService).log("ANNOUNCEMENT_DELETE", "Announcement", 50L,
                "Annonce supprimée : Rentree", httpRequest);
    }

    // ==================================================================
    // 5. Visibilite
    // ==================================================================

    @Test
    void listForCurrentUser_avecRole_utiliseLaRequeteCiblee() {
        security.when(SecurityUtils::currentUser).thenReturn(utilisateur(3L, true, "ENSEIGNANT"));
        when(announcementRepository
                .findByTargetRoleOrTargetRoleIsNullOrderByPinnedDescCreatedAtDesc("ENSEIGNANT"))
                .thenReturn(List.of(Announcement.builder().id(1L).title("A").content("a").build()));

        List<AnnouncementResponse> responses = service.listForCurrentUser();

        assertThat(responses).hasSize(1);
        verify(announcementRepository, never()).findAllByOrderByPinnedDescCreatedAtDesc();
    }

    @Test
    void listForCurrentUser_sansUtilisateur_listeTout() {
        security.when(SecurityUtils::currentUser).thenReturn(null);
        when(announcementRepository.findAllByOrderByPinnedDescCreatedAtDesc())
                .thenReturn(List.of());

        assertThat(service.listForCurrentUser()).isEmpty();

        verify(announcementRepository, never())
                .findByTargetRoleOrTargetRoleIsNullOrderByPinnedDescCreatedAtDesc(any());
    }

    @Test
    void listForCurrentUser_utilisateurSansRole_listeTout() {
        // Repli : un compte sans role voit TOUTES les annonces, y compris celles ciblees
        // sur un autre role. A signaler, non corrige.
        security.when(SecurityUtils::currentUser).thenReturn(utilisateur(9L, true));
        when(announcementRepository.findAllByOrderByPinnedDescCreatedAtDesc())
                .thenReturn(List.of(Announcement.builder().id(1L).title("A").content("a")
                        .targetRole("DIRECTEUR").build()));

        assertThat(service.listForCurrentUser()).hasSize(1);
    }

    @Test
    void listAll_mappeLesAnnonces() {
        Announcement avecAuteur = Announcement.builder().id(1L).title("A").content("a")
                .createdBy(utilisateur(1L, true, "DIRECTEUR")).build();
        Announcement sansAuteur = Announcement.builder().id(2L).title("B").content("b").build();
        when(announcementRepository.findAllByOrderByPinnedDescCreatedAtDesc())
                .thenReturn(List.of(avecAuteur, sansAuteur));

        List<AnnouncementResponse> responses = service.listAll();

        assertThat(responses).extracting(AnnouncementResponse::getCreatedByName)
                .containsExactly("Jean Kamdem", "Système");
    }
}
