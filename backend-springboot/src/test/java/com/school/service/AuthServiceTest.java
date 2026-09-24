package com.school.service;

import com.school.dto.request.ChangePasswordRequest;
import com.school.dto.request.LoginRequest;
import com.school.dto.request.RefreshTokenRequest;
import com.school.dto.request.RegisterRequest;
import com.school.dto.response.AuthResponse;
import com.school.entity.RefreshToken;
import com.school.entity.Role;
import com.school.entity.User;
import com.school.exception.BusinessException;
import com.school.exception.TokenRefreshException;
import com.school.repository.RefreshTokenRepository;
import com.school.repository.RoleRepository;
import com.school.repository.UserRepository;
import com.school.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests de l'authentification.
 *
 * <p>Pourquoi ce fichier existe : `AuthService` porte le verrouillage de compte, la
 * rotation des jetons de rafraîchissement et la création de comptes — le code le plus
 * sensible de l'application, et il n'était pas testé. Même profil que `BackupService`
 * et `FileStorageService` : une branche fausse y serait invisible (la CI reste verte).</p>
 *
 * <p>Les cas les plus utiles ici ne sont pas « un login valide marche » mais les seuils :
 * un compte verrouillé est-il refusé AVANT de tenter l'authentification ? Le 5e échec
 * verrouille-t-il, et le compteur repart-il de zéro ? Un jeton de rafraîchissement déjà
 * utilisé est-il bien révoqué ?</p>
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditService auditService;
    @Mock private HttpServletRequest httpRequest;

    @InjectMocks private AuthService service;

    @BeforeEach
    void setUp() {
        // Les deux champs @Value ne sont pas injectés par le constructeur.
        ReflectionTestUtils.setField(service, "refreshExpirationMs", 604_800_000L);
        ReflectionTestUtils.setField(service, "accessExpirationMs", 900_000L);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------
    // Verrouillage de compte
    // ------------------------------------------------------------------

    @Test
    @DisplayName("un compte verrouillé est refusé sans même tenter l'authentification")
    void compteVerrouilleRefuseAvantAuthentification() {
        User user = utilisateur(1L, "prof1");
        user.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        when(userRepository.findByUsername("prof1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(connexion("prof1", "MotDePasse1"), httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("verrouillé");

        // Le point important : on n'a pas vérifié le mot de passe, donc un mot de passe
        // correct ne contourne pas le verrou.
        verifyNoInteractions(authenticationManager);
    }

    @Test
    @DisplayName("un verrou expiré ne bloque plus la connexion")
    void verrouExpireLaissePasser() {
        User user = utilisateur(1L, "prof1");
        user.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        when(userRepository.findByUsername("prof1")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any())).thenReturn(authentifie(user));
        when(jwtService.generateToken(user)).thenReturn("jeton-acces");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = service.login(connexion("prof1", "MotDePasse1"), httpRequest);

        assertThat(response.getAccessToken()).isEqualTo("jeton-acces");
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("un échec de mot de passe incrémente le compteur")
    void echecIncrementeLeCompteur() {
        User user = utilisateur(1L, "prof1");
        user.setFailedAttempts(2);
        when(userRepository.findByUsername("prof1")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("mot de passe invalide"));

        assertThatThrownBy(() -> service.login(connexion("prof1", "faux"), httpRequest))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(user.getFailedAttempts()).isEqualTo(3);
        assertThat(user.getLockedUntil()).isNull();
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("le 5e échec verrouille le compte et remet le compteur à zéro")
    void cinquiemeEchecVerrouilleLeCompte() {
        User user = utilisateur(1L, "prof1");
        user.setFailedAttempts(4);
        when(userRepository.findByUsername("prof1")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("mot de passe invalide"));

        assertThatThrownBy(() -> service.login(connexion("prof1", "faux"), httpRequest))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLockedUntil()).isAfter(LocalDateTime.now().plusMinutes(14));
        assertThat(user.getLockedUntil()).isBefore(LocalDateTime.now().plusMinutes(16));
    }

    @Test
    @DisplayName("un échec sur un nom d'utilisateur inconnu n'est pas comptabilisé")
    void echecSurUtilisateurInconnuNestPasComptabilise() {
        when(userRepository.findByUsername("inconnu")).thenReturn(Optional.empty());
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("mot de passe invalide"));

        assertThatThrownBy(() -> service.login(connexion("inconnu", "faux"), httpRequest))
                .isInstanceOf(BadCredentialsException.class);

        // Rien à verrouiller : on ne crée pas de trace, et surtout on ne révèle pas
        // l'existence du compte par un verrouillage.
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("un compte désactivé est refusé par un message dédié, sans compteur d'échecs")
    void compteDesactiveRefuse() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new DisabledException("compte désactivé"));

        assertThatThrownBy(() -> service.login(connexion("prof1", "MotDePasse1"), httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("désactivé");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("un verrou Spring Security est traduit en message dédié")
    void verrouSpringSecurityTraduit() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new LockedException("compte verrouillé"));

        assertThatThrownBy(() -> service.login(connexion("prof1", "MotDePasse1"), httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Contactez l'administrateur");
    }

    @Test
    @DisplayName("une connexion réussie remet le compteur à zéro et horodate la connexion")
    void connexionReussieReinitialiseLeCompteur() {
        User user = utilisateur(1L, "prof1");
        user.setFailedAttempts(3);
        user.setLockedUntil(LocalDateTime.now().minusMinutes(1));
        when(userRepository.findByUsername("prof1")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any())).thenReturn(authentifie(user));
        when(jwtService.generateToken(user)).thenReturn("jeton-acces");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = service.login(connexion("prof1", "MotDePasse1"), httpRequest);

        assertThat(response.getAccessToken()).isEqualTo("jeton-acces");
        assertThat(response.getRefreshToken()).matches("[0-9a-f]{64}");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(900_000L);
        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getLastLogin()).isNotNull();
        verify(userRepository).save(user);
        verify(auditService).log(eq("LOGIN"), eq("User"), eq(1L), anyString(), eq(httpRequest));
    }

    // ------------------------------------------------------------------
    // Rotation des jetons de rafraîchissement
    // ------------------------------------------------------------------

    @Test
    @DisplayName("un jeton de rafraîchissement inconnu est refusé")
    void refreshJetonInconnuRefuse() {
        when(refreshTokenRepository.findByToken("inconnu")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh(new RefreshTokenRequest("inconnu")))
                .isInstanceOf(TokenRefreshException.class)
                .hasMessageContaining("inconnu");
    }

    @Test
    @DisplayName("un jeton révoqué est supprimé puis refusé")
    void refreshJetonRevoqueRefuse() {
        RefreshToken revoque = RefreshToken.builder()
                .token("revoque").user(utilisateur(1L, "prof1"))
                .expiryDate(LocalDateTime.now().plusDays(1)).revoked(true).build();
        when(refreshTokenRepository.findByToken("revoque")).thenReturn(Optional.of(revoque));

        assertThatThrownBy(() -> service.refresh(new RefreshTokenRequest("revoque")))
                .isInstanceOf(TokenRefreshException.class)
                .hasMessageContaining("expiré ou révoqué");

        verify(refreshTokenRepository).delete(revoque);
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("un jeton expiré est supprimé puis refusé")
    void refreshJetonExpireRefuse() {
        RefreshToken expire = RefreshToken.builder()
                .token("expire").user(utilisateur(1L, "prof1"))
                .expiryDate(LocalDateTime.now().minusSeconds(1)).build();
        when(refreshTokenRepository.findByToken("expire")).thenReturn(Optional.of(expire));

        assertThatThrownBy(() -> service.refresh(new RefreshTokenRequest("expire")))
                .isInstanceOf(TokenRefreshException.class);

        verify(refreshTokenRepository).delete(expire);
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("un rafraîchissement valide révoque l'ancien jeton et en émet un nouveau")
    void refreshRotationRevoqueLancienJeton() {
        User user = utilisateur(1L, "prof1");
        RefreshToken ancien = RefreshToken.builder()
                .token("ancien").user(user).expiryDate(LocalDateTime.now().plusDays(1)).build();
        when(refreshTokenRepository.findByToken("ancien")).thenReturn(Optional.of(ancien));
        when(jwtService.generateToken(user)).thenReturn("nouvel-acces");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = service.refresh(new RefreshTokenRequest("ancien"));

        assertThat(response.getAccessToken()).isEqualTo("nouvel-acces");
        assertThat(response.getRefreshToken()).isNotEqualTo("ancien");
        assertThat(response.getRefreshToken()).matches("[0-9a-f]{64}");
        // Sans cette révocation, un jeton volé resterait valable indéfiniment.
        assertThat(ancien.isRevoked()).isTrue();
        verify(refreshTokenRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("la déconnexion révoque le jeton fourni")
    void logoutRevoqueLeJeton() {
        RefreshToken jeton = RefreshToken.builder()
                .token("jeton").user(utilisateur(1L, "prof1"))
                .expiryDate(LocalDateTime.now().plusDays(1)).build();
        when(refreshTokenRepository.findByToken("jeton")).thenReturn(Optional.of(jeton));

        service.logout("jeton");

        assertThat(jeton.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(jeton);
    }

    @Test
    @DisplayName("la déconnexion d'un jeton inconnu ne lève pas d'erreur")
    void logoutJetonInconnuSansErreur() {
        when(refreshTokenRepository.findByToken("absent")).thenReturn(Optional.empty());

        assertThatCode(() -> service.logout("absent")).doesNotThrowAnyException();

        verify(refreshTokenRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // Création de comptes
    // ------------------------------------------------------------------

    @Test
    @DisplayName("l'inscription refuse un nom d'utilisateur déjà pris")
    void registerRefuseNomDejaPris() {
        when(userRepository.existsByUsername("prof1")).thenReturn(true);

        assertThatThrownBy(() -> service.register(inscription("prof1", "prof1@ecole.cm", Set.of("ELEVE"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("déjà pris");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("l'inscription refuse un email déjà utilisé")
    void registerRefuseEmailDejaUtilise() {
        when(userRepository.existsByUsername("neuf")).thenReturn(false);
        when(userRepository.existsByEmail("pris@ecole.cm")).thenReturn(true);

        assertThatThrownBy(() -> service.register(inscription("neuf", "pris@ecole.cm", Set.of("ELEVE"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("déjà utilisé");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("l'inscription applique la politique de mot de passe")
    void registerAppliqueLaPolitiqueDeMotDePasse() {
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.register(inscription("neuf", "neuf@ecole.cm", Set.of("ELEVE"), "court1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("au moins 8");

        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("sans rôle demandé, l'inscription attribue ELEVE par défaut")
    void registerSansRoleAttribueEleveParDefaut() {
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(roleRepository.findByName("ELEVE")).thenReturn(Optional.of(role("ELEVE")));
        when(passwordEncoder.encode("MotDePasse1")).thenReturn("mot-de-passe-hache");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.register(inscription("neuf", "neuf@ecole.cm", null));

        User enregistre = utilisateurEnregistre();
        assertThat(enregistre.getRoles()).extracting(Role::getName).containsExactly("ELEVE");
        assertThat(enregistre.getPassword()).isEqualTo("mot-de-passe-hache");
        assertThat(enregistre.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("l'inscription refuse un rôle inconnu")
    void registerRefuseRoleInconnu() {
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(roleRepository.findByName("PIRATE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(inscription("neuf", "neuf@ecole.cm", Set.of("PIRATE"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Rôle inconnu");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("l'inscription attribue tous les rôles demandés")
    void registerAttribueLesRolesDemandes() {
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(roleRepository.findByName("ENSEIGNANT")).thenReturn(Optional.of(role("ENSEIGNANT")));
        when(roleRepository.findByName("ELEVE")).thenReturn(Optional.of(role("ELEVE")));
        when(passwordEncoder.encode(anyString())).thenReturn("mot-de-passe-hache");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.register(inscription("neuf", "neuf@ecole.cm", Set.of("ENSEIGNANT", "ELEVE")));

        assertThat(utilisateurEnregistre().getRoles())
                .extracting(Role::getName)
                .containsExactlyInAnyOrder("ENSEIGNANT", "ELEVE");
    }

    @Test
    @DisplayName("la création d'un compte lié réutilise le compte existant sans le modifier")
    void createLinkedAccountReutiliseLeCompteExistant() {
        User existant = utilisateur(7L, "eleve6");
        when(userRepository.existsByUsername("eleve6")).thenReturn(true);
        when(userRepository.findByUsername("eleve6")).thenReturn(Optional.of(existant));

        User retourne = service.createLinkedAccount("eleve6", "MotDePasse1", "e@ecole.cm",
                "Aicha", "Kamdem", "ELEVE");

        assertThat(retourne).isSameAs(existant);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("la création d'un compte lié encode le mot de passe et attache le rôle")
    void createLinkedAccountCreeLeCompte() {
        when(userRepository.existsByUsername("neuf")).thenReturn(false);
        when(roleRepository.findByName("ENSEIGNANT")).thenReturn(Optional.of(role("ENSEIGNANT")));
        when(passwordEncoder.encode("MotDePasse1")).thenReturn("mot-de-passe-hache");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User cree = service.createLinkedAccount("neuf", "MotDePasse1", "neuf@ecole.cm",
                "Jean", "Kamdem", "ENSEIGNANT");

        // `user.getRoles().add(role)` lèverait une NPE si la collection n'était plus
        // initialisée dans l'entité : ce test le garde aussi.
        assertThat(cree.getRoles()).extracting(Role::getName).containsExactly("ENSEIGNANT");
        assertThat(cree.getPassword()).isEqualTo("mot-de-passe-hache");
        assertThat(cree.isEnabled()).isTrue();
        assertThat(cree.getUsername()).isEqualTo("neuf");
    }

    @Test
    @DisplayName("la création d'un compte lié refuse un rôle non configuré")
    void createLinkedAccountRefuseRoleInconnu() {
        when(userRepository.existsByUsername("neuf")).thenReturn(false);
        when(roleRepository.findByName("PIRATE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createLinkedAccount("neuf", "MotDePasse1", "n@ecole.cm",
                "Jean", "Kamdem", "PIRATE"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("non configuré");

        verify(userRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // Changement de mot de passe
    // ------------------------------------------------------------------

    @Test
    @DisplayName("le changement de mot de passe refuse un mot de passe actuel erroné")
    void changePasswordRefuseMauvaisMotDePasseActuel() {
        User user = utilisateur(1L, "prof1");
        user.setPassword("hache-actuel");
        authentifieDansLeContexte(user);
        when(passwordEncoder.matches("faux", "hache-actuel")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(new ChangePasswordRequest("faux", "NouveauMot1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("incorrect");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("le changement de mot de passe applique la politique au nouveau mot de passe")
    void changePasswordAppliqueLaPolitique() {
        User user = utilisateur(1L, "prof1");
        user.setPassword("hache-actuel");
        authentifieDansLeContexte(user);
        when(passwordEncoder.matches("actuel", "hache-actuel")).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(new ChangePasswordRequest("actuel", "faible")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("au moins 8");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("le changement de mot de passe encode et enregistre le nouveau mot de passe")
    void changePasswordEncodeEtEnregistre() {
        User user = utilisateur(1L, "prof1");
        user.setPassword("hache-actuel");
        authentifieDansLeContexte(user);
        when(passwordEncoder.matches("actuel", "hache-actuel")).thenReturn(true);
        when(passwordEncoder.encode("NouveauMot1")).thenReturn("hache-nouveau");

        service.changePassword(new ChangePasswordRequest("actuel", "NouveauMot1"));

        assertThat(user.getPassword()).isEqualTo("hache-nouveau");
        verify(userRepository).save(user);
        verify(auditService).log(eq("PASSWORD_CHANGE"), eq("User"), eq(1L), anyString(), isNull());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static User utilisateur(Long id, String username) {
        User user = User.builder()
                .username(username)
                .password("hache")
                .email(username + "@ecole.cm")
                .firstName("Test")
                .lastName("Utilisateur")
                .enabled(true)
                .build();
        user.setId(id);
        return user;
    }

    private static Role role(String nom) {
        return Role.builder().name(nom).build();
    }

    private static Authentication authentifie(User user) {
        return new UsernamePasswordAuthenticationToken(user, null, List.of());
    }

    private static void authentifieDansLeContexte(User user) {
        SecurityContextHolder.getContext().setAuthentication(authentifie(user));
    }

    private static LoginRequest connexion(String username, String password) {
        return LoginRequest.builder().username(username).password(password).build();
    }

    private static RegisterRequest inscription(String username, String email, Set<String> roles) {
        return inscription(username, email, roles, "MotDePasse1");
    }

    private static RegisterRequest inscription(String username, String email, Set<String> roles,
                                               String password) {
        return RegisterRequest.builder()
                .username(username)
                .password(password)
                .email(email)
                .firstName("Jean")
                .lastName("Kamdem")
                .roles(roles)
                .build();
    }

    /** Récupère l'utilisateur réellement passé à `save`. */
    private User utilisateurEnregistre() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        return captor.getValue();
    }
}
