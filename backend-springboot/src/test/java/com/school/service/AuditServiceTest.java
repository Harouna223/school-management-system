package com.school.service;

import com.school.entity.AuditLog;
import com.school.entity.User;
import com.school.repository.AuditLogRepository;
import com.school.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Journal d'audit — 93 sites d'appel dans le backend, donc un point de defaillance unique.
 *
 * <p><b>Le garde qui compte est {@code request != null}.</b> Il n'est pas theorique :
 * {@code AuthService.changePassword} appelle {@code log(..., null)} apres avoir enregistre le nouveau
 * mot de passe, dans la meme transaction. Sans ce garde, changer son mot de passe leverait une NPE
 * <i>apres</i> l'ecriture, donc renverrait une erreur 500 et annulerait le changement.</p>
 *
 * <p>Le statut d'authentification est simule avec {@code mockStatic(..., CALLS_REAL_METHODS)} et seul
 * {@code currentUser()} est stubbe : {@code currentUsername()} s'execute alors pour de vrai, ce qui
 * permet de verifier le repli {@code "anonymous"} plutot que de le stubber.</p>
 *
 * <p>⚠️ Deux constats, non corriges ici car ils changent le comportement :</p>
 * <ul>
 *   <li>{@code log()} n'est pas {@code @Transactional(REQUIRES_NEW)} : l'audit appartient a la
 *       transaction de l'appelant, donc un rollback metier efface la trace.</li>
 *   <li>{@code details} est ecrit sans troncature alors que la colonne fait 500 caracteres : un
 *       detail trop long ferait echouer l'operation metier elle-meme.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private HttpServletRequest request;

    private AuditService service;
    private MockedStatic<SecurityUtils> security;

    @BeforeEach
    void setUp() {
        service = new AuditService(auditLogRepository);
        security = mockStatic(SecurityUtils.class, CALLS_REAL_METHODS);
    }

    @AfterEach
    void tearDown() {
        security.close();
    }

    private static User utilisateur(Long id, String username) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        return u;
    }

    private AuditLog capture() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void enregistreLAction() {
        security.when(SecurityUtils::currentUser).thenReturn(utilisateur(7L, "admin"));

        service.log("UPDATE", "Student", 42L, "Modification eleve", request);

        assertThat(capture().getAction()).isEqualTo("UPDATE");
    }

    @Test
    void enregistreLEntite() {
        security.when(SecurityUtils::currentUser).thenReturn(utilisateur(7L, "admin"));

        service.log("UPDATE", "Student", 42L, "Modification eleve", request);

        assertThat(capture().getEntity()).isEqualTo("Student");
    }

    @Test
    void enregistreLIdentifiantDeLEntite() {
        security.when(SecurityUtils::currentUser).thenReturn(utilisateur(7L, "admin"));

        service.log("UPDATE", "Student", 42L, "Modification eleve", request);

        assertThat(capture().getEntityId()).isEqualTo(42L);
    }

    @Test
    void enregistreLesDetails() {
        security.when(SecurityUtils::currentUser).thenReturn(utilisateur(7L, "admin"));

        service.log("UPDATE", "Student", 42L, "Modification eleve", request);

        assertThat(capture().getDetails()).isEqualTo("Modification eleve");
    }

    @Test
    void utilisateurAuthentifie_renseigneLeUser() {
        User admin = utilisateur(7L, "admin");
        security.when(SecurityUtils::currentUser).thenReturn(admin);

        service.log("CREATE", "Class", 1L, "Creation classe", request);

        assertThat(capture().getUser()).isSameAs(admin);
    }

    @Test
    void utilisateurAuthentifie_renseigneLeUsername() {
        security.when(SecurityUtils::currentUser).thenReturn(utilisateur(7L, "admin"));

        service.log("CREATE", "Class", 1L, "Creation classe", request);

        assertThat(capture().getUsername()).isEqualTo("admin");
    }

    @Test
    void utilisateurNonAuthentifie_userNulEtUsernameAnonymous() {
        // currentUsername() s'execute reellement : c'est son repli qu'on verifie.
        security.when(SecurityUtils::currentUser).thenReturn(null);

        service.log("LOGIN_FAILED", "User", null, "Mot de passe incorrect", request);

        AuditLog entree = capture();
        assertThat(entree.getUser()).isNull();
        assertThat(entree.getUsername()).isEqualTo("anonymous");
    }

    @Test
    void requestNulle_nEchouePasEtLaisseLAdresseIpNulle() {
        // Chemin reel : AuthService.changePassword appelle log(..., null).
        security.when(SecurityUtils::currentUser).thenReturn(utilisateur(7L, "admin"));

        service.log("PASSWORD_CHANGE", "User", 7L, "Changement de mot de passe", null);

        assertThat(capture().getIpAddress()).isNull();
    }

    @Test
    void requestFournie_renseigneLAdresseIp() {
        security.when(SecurityUtils::currentUser).thenReturn(utilisateur(7L, "admin"));
        org.mockito.Mockito.when(request.getRemoteAddr()).thenReturn("192.168.1.10");

        service.log("DELETE", "Schedule", 5L, "Suppression creneau", request);

        assertThat(capture().getIpAddress()).isEqualTo("192.168.1.10");
    }

    @Test
    void identifiantEntiteNul_estAccepte() {
        // Actions sans entite (connexion, import global...) : la colonne est nullable.
        security.when(SecurityUtils::currentUser).thenReturn(null);

        service.log("LOGIN", "User", null, "Connexion reussie", request);

        assertThat(capture().getEntityId()).isNull();
    }

    @Test
    void detailsNuls_sontAcceptes() {
        security.when(SecurityUtils::currentUser).thenReturn(null);

        service.log("LOGOUT", "User", 3L, null, request);

        assertThat(capture().getDetails()).isNull();
    }

    @Test
    void nEcritQuUneSeuleLigneParAppel() {
        security.when(SecurityUtils::currentUser).thenReturn(utilisateur(7L, "admin"));

        service.log("CREATE", "Room", 9L, "Creation salle", request);

        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }
}
