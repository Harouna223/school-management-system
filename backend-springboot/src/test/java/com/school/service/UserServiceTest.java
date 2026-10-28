package com.school.service;

import com.school.dto.response.PageResponse;
import com.school.dto.response.UserResponse;
import com.school.entity.Role;
import com.school.entity.User;
import com.school.enums.RoleName;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.RefreshTokenRepository;
import com.school.repository.RoleRepository;
import com.school.repository.UserRepository;
import com.school.utils.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Tests de l'administration des utilisateurs et rôles.
 *
 * <p>Ce service porte les garde-fous d'escalade de privilèges de la
 * plateforme : liste blanche des rôles attribuables, SUPER_ADMIN réservé aux
 * SUPER_ADMIN, interdiction d'agir sur son propre compte, et protection du
 * dernier SUPER_ADMIN actif. Une régression ici n'est pas visible en
 * production tant qu'aucun administrateur ne se rétrograde : c'est donc
 * exactement le genre de garde qu'il faut verrouiller par des tests.</p>
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditService auditService;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, roleRepository, refreshTokenRepository,
                passwordEncoder, auditService);
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static Role role(String name) {
        return Role.builder().name(name).build();
    }

    private static User user(Long id, String... roleNames) {
        return user(id, true, roleNames);
    }

    private static User user(Long id, boolean enabled, String... roleNames) {
        Set<Role> roles = Arrays.stream(roleNames)
                .map(UserServiceTest::role)
                .collect(Collectors.toCollection(HashSet::new));
        return User.builder().id(id).username("user" + id).enabled(enabled).roles(roles).build();
    }

    /**
     * Simule l'utilisateur authentifié. {@code SecurityUtils.hasRole} n'est
     * volontairement pas stubbé : par défaut il renvoie {@code false}, ce qui
     * correspond à un appelant qui n'est PAS SUPER_ADMIN.
     */
    private MockedStatic<SecurityUtils> asUser(Long currentUserId) {
        MockedStatic<SecurityUtils> utils = mockStatic(SecurityUtils.class);
        utils.when(SecurityUtils::currentUserId).thenReturn(currentUserId);
        return utils;
    }

    // ------------------------------------------------------------------
    // updateRoles : liste blanche et validations d'entrée
    // ------------------------------------------------------------------

    @Test
    void updateRoles_refuseUnRoleHorsListeBlanche() {
        when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, "PARENT")));

        try (MockedStatic<SecurityUtils> utils = asUser(1L)) {
            assertThatThrownBy(() -> service.updateRoles(5L, Set.of("ADMIN"), null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Rôle non attribuable");
        }
        // Aucun rôle résolu, aucune écriture : le rejet est immédiat.
        verifyNoInteractions(roleRepository);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateRoles_refuseUnEnsembleDeRolesVide() {
        when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, "PARENT")));

        try (MockedStatic<SecurityUtils> utils = asUser(1L)) {
            assertThatThrownBy(() -> service.updateRoles(5L, Set.of(), null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Au moins un rôle doit être attribué");
            assertThatThrownBy(() -> service.updateRoles(5L, null, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Au moins un rôle doit être attribué");
        }
        verify(userRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // updateRoles : escalade de privilèges
    // ------------------------------------------------------------------

    @Test
    void updateRoles_refuseAttributionSuperAdminParUnNonSuperAdmin() {
        when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, "DIRECTEUR")));
        when(roleRepository.findByName("SUPER_ADMIN")).thenReturn(Optional.of(role("SUPER_ADMIN")));

        try (MockedStatic<SecurityUtils> utils = asUser(1L)) {
            assertThatThrownBy(() -> service.updateRoles(5L, Set.of("SUPER_ADMIN"), null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Seul un SUPER_ADMIN peut attribuer");
        }
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateRoles_autoriseAttributionSuperAdminParUnSuperAdmin() {
        User target = user(5L, "DIRECTEUR");
        when(userRepository.findById(5L)).thenReturn(Optional.of(target));
        when(roleRepository.findByName("SUPER_ADMIN")).thenReturn(Optional.of(role("SUPER_ADMIN")));
        when(userRepository.save(target)).thenReturn(target);

        try (MockedStatic<SecurityUtils> utils = asUser(1L)) {
            utils.when(() -> SecurityUtils.hasRole("SUPER_ADMIN")).thenReturn(true);

            UserResponse res = service.updateRoles(5L, Set.of("SUPER_ADMIN"), null);

            assertThat(res.getRoles()).containsExactly("SUPER_ADMIN");
        }
        verify(auditService).log(eq("ROLES_UPDATE"), eq("User"), eq(5L),
                contains("SUPER_ADMIN"), isNull());
    }

    @Test
    void updateRoles_refuseDeModifierSesPropresRoles() {
        // Même un SUPER_ADMIN ne peut pas toucher à ses propres rôles :
        // c'est ce qui empêche une auto-promotion.
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "SUPER_ADMIN")));

        try (MockedStatic<SecurityUtils> utils = asUser(1L)) {
            assertThatThrownBy(() -> service.updateRoles(1L, Set.of("PARENT"), null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("modifier vos propres rôles");
        }
        verifyNoInteractions(roleRepository);
        verify(userRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // updateRoles : protection du dernier SUPER_ADMIN actif
    // ------------------------------------------------------------------

    @Test
    void updateRoles_refuseDeRetirerLeDernierSuperAdminActif() {
        User seulAdmin = user(5L, "SUPER_ADMIN");
        when(userRepository.findById(5L)).thenReturn(Optional.of(seulAdmin));
        when(roleRepository.findByName("DIRECTEUR")).thenReturn(Optional.of(role("DIRECTEUR")));
        when(userRepository.findAll()).thenReturn(List.of(seulAdmin));

        try (MockedStatic<SecurityUtils> utils = asUser(1L)) {
            assertThatThrownBy(() -> service.updateRoles(5L, Set.of("DIRECTEUR"), null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("sans SUPER_ADMIN actif");
        }
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateRoles_autoriseLeRetraitSiUnAutreAdminActifExiste() {
        User target = user(5L, "SUPER_ADMIN");
        User autre = user(6L, "SUPER_ADMIN");
        when(userRepository.findById(5L)).thenReturn(Optional.of(target));
        when(roleRepository.findByName("DIRECTEUR")).thenReturn(Optional.of(role("DIRECTEUR")));
        when(userRepository.findAll()).thenReturn(List.of(target, autre));
        when(userRepository.save(target)).thenReturn(target);

        try (MockedStatic<SecurityUtils> utils = asUser(1L)) {
            UserResponse res = service.updateRoles(5L, Set.of("DIRECTEUR"), null);

            assertThat(res.getRoles()).containsExactly("DIRECTEUR");
        }
    }

    @Test
    void updateRoles_neComptePasUnSuperAdminDesactiveCommeFiletDeSecurite() {
        // Régression silencieuse : si le filtre isEnabled() saute, un compte
        // SUPER_ADMIN désactivé est compté comme secours et le dernier
        // administrateur réellement actif peut être rétrogradé.
        User target = user(5L, "SUPER_ADMIN");
        User autreDesactive = user(6L, false, "SUPER_ADMIN");
        when(userRepository.findById(5L)).thenReturn(Optional.of(target));
        when(roleRepository.findByName("DIRECTEUR")).thenReturn(Optional.of(role("DIRECTEUR")));
        when(userRepository.findAll()).thenReturn(List.of(target, autreDesactive));

        try (MockedStatic<SecurityUtils> utils = asUser(1L)) {
            assertThatThrownBy(() -> service.updateRoles(5L, Set.of("DIRECTEUR"), null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("sans SUPER_ADMIN actif");
        }
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateRoles_neSeComptePasLuiMemeCommeFiletDeSecurite() {
        // Si le filtre « autre utilisateur » saute, l'utilisateur devient son
        // propre secours et la garde ne bloque plus jamais rien.
        User seulAdmin = user(5L, "SUPER_ADMIN");
        when(userRepository.findById(5L)).thenReturn(Optional.of(seulAdmin));
        when(roleRepository.findByName("DIRECTEUR")).thenReturn(Optional.of(role("DIRECTEUR")));
        when(userRepository.findAll()).thenReturn(List.of(seulAdmin));

        try (MockedStatic<SecurityUtils> utils = asUser(1L)) {
            assertThatThrownBy(() -> service.updateRoles(5L, Set.of("DIRECTEUR"), null))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ------------------------------------------------------------------
    // toggleEnabled
    // ------------------------------------------------------------------

    @Test
    void toggleEnabled_refuseDeDesactiverSonPropreCompte() {
        // Rôle non administrateur : la garde « soi-même » est indépendante du rôle.
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "DIRECTEUR")));

        try (MockedStatic<SecurityUtils> utils = asUser(1L)) {
            assertThatThrownBy(() -> service.toggleEnabled(1L, false, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("désactiver votre propre compte");
        }
        verify(userRepository, never()).save(any());
    }

    @Test
    void toggleEnabled_refuseDeDesactiverLeDernierSuperAdminActif() {
        User seulAdmin = user(5L, "SUPER_ADMIN");
        when(userRepository.findById(5L)).thenReturn(Optional.of(seulAdmin));
        when(userRepository.findAll()).thenReturn(List.of(seulAdmin));

        try (MockedStatic<SecurityUtils> utils = asUser(1L)) {
            assertThatThrownBy(() -> service.toggleEnabled(5L, false, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("sans SUPER_ADMIN actif");
        }
        verify(userRepository, never()).save(any());
    }

    @Test
    void toggleEnabled_reactivationEffaceLeVerrouillageEtLeCompteur() {
        User target = user(3L, "PARENT");
        target.setLockedUntil(LocalDateTime.now().plusHours(1));
        target.setFailedAttempts(4);
        when(userRepository.findById(3L)).thenReturn(Optional.of(target));
        when(userRepository.save(target)).thenReturn(target);

        // Réactiver son propre compte est autorisé : pas de garde « soi-même » ici.
        UserResponse res = service.toggleEnabled(3L, true, null);

        assertThat(res.isEnabled()).isTrue();
        assertThat(target.getLockedUntil()).isNull();
        assertThat(target.getFailedAttempts()).isZero();
    }

    // ------------------------------------------------------------------
    // unlock
    // ------------------------------------------------------------------

    @Test
    void unlock_effaceLeVerrouillageEtLeCompteur() {
        User target = user(5L, "PARENT");
        target.setFailedAttempts(5);
        target.setLockedUntil(LocalDateTime.now().plusHours(2));
        when(userRepository.findById(5L)).thenReturn(Optional.of(target));
        when(userRepository.save(target)).thenReturn(target);

        UserResponse res = service.unlock(5L, null);

        assertThat(res.getLockedUntil()).isNull();
        assertThat(target.getLockedUntil()).isNull();
        assertThat(target.getFailedAttempts()).isZero();
    }

    // ------------------------------------------------------------------
    // resetPassword
    // ------------------------------------------------------------------

    @Test
    void resetPassword_refuseUnMotDePasseTropCourtSansToucherALaBase() {
        assertThatThrownBy(() -> service.resetPassword(5L, "court", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("au moins 8 caractères");

        // La politique est évaluée avant toute lecture : aucun accès base/encodeur.
        verifyNoInteractions(userRepository, passwordEncoder, auditService);
    }

    @Test
    void resetPassword_refuseUnMotDePasseSansMajusculeOuChiffre() {
        assertThatThrownBy(() -> service.resetPassword(5L, "motdepasse", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("minuscule, une majuscule et un chiffre");

        verifyNoInteractions(userRepository, passwordEncoder, auditService);
    }

    @Test
    void resetPassword_refuseLaReinitialisationDeSonPropreCompte() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "DIRECTEUR")));

        try (MockedStatic<SecurityUtils> utils = asUser(1L)) {
            assertThatThrownBy(() -> service.resetPassword(1L, "NouveauPass1", null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("réinitialiser votre propre mot de passe");
        }
        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void resetPassword_hacheLeMotDePasseEtEffaceLeVerrouillage() {
        User target = user(5L, "PARENT");
        target.setFailedAttempts(3);
        target.setLockedUntil(LocalDateTime.now().plusMinutes(30));
        when(userRepository.findById(5L)).thenReturn(Optional.of(target));
        when(passwordEncoder.encode("NouveauPass1")).thenReturn("$2a$hash");

        try (MockedStatic<SecurityUtils> utils = asUser(1L)) {
            service.resetPassword(5L, "NouveauPass1", null);
        }

        assertThat(target.getPassword()).isEqualTo("$2a$hash");
        assertThat(target.getFailedAttempts()).isZero();
        assertThat(target.getLockedUntil()).isNull();
        verify(userRepository).save(target);
        // Un mot de passe réinitialisé doit couper les sessions en cours.
        verify(refreshTokenRepository).deleteByUserId(5L);
        verify(auditService).log(eq("PASSWORD_RESET"), eq("User"), eq(5L),
                contains("user5"), isNull());
    }

    @Test
    void toggleEnabled_desactivationSupprimeLesJetonsDeRafraichissement() {
        User target = user(5L, "PARENT");
        when(userRepository.findById(5L)).thenReturn(Optional.of(target));
        when(userRepository.save(target)).thenReturn(target);

        try (MockedStatic<SecurityUtils> utils = asUser(1L)) {
            service.toggleEnabled(5L, false, null);
        }

        assertThat(target.isEnabled()).isFalse();
        // Sinon un compte désactivé continuait de renouveler sa session 7 jours.
        verify(refreshTokenRepository).deleteByUserId(5L);
    }

    @Test
    void toggleEnabled_reactivationNeRessusciteAucunJeton() {
        User target = user(5L, false, "PARENT");
        when(userRepository.findById(5L)).thenReturn(Optional.of(target));
        when(userRepository.save(target)).thenReturn(target);

        try (MockedStatic<SecurityUtils> utils = asUser(1L)) {
            service.toggleEnabled(5L, true, null);
        }

        assertThat(target.isEnabled()).isTrue();
        // Les jetons ont été SUPPRIMÉS lors de la désactivation : la
        // réactivation ne peut pas les rétablir.
        verify(refreshTokenRepository, never()).deleteByUserId(any());
    }

    // ------------------------------------------------------------------
    // Lectures
    // ------------------------------------------------------------------

    @Test
    void findById_leveUneResourceNotFoundException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void findAll_trieParNomDUtilisateur() {
        when(userRepository.findAll(Sort.by("username"))).thenReturn(List.of(user(1L, "PARENT")));

        assertThat(service.findAll()).hasSize(1);
        verify(userRepository).findAll(Sort.by("username"));
    }

    @Test
    void listRoles_trieParNomDeRole() {
        when(roleRepository.findAll(Sort.by("name")))
                .thenReturn(List.of(role("PARENT"), role("SUPER_ADMIN")));

        assertThat(service.listRoles()).hasSize(2);
    }

    @Test
    void search_sansTerme_utiliseFindAllAvecTriParDateDecroissante() {
        Pageable attendu = PageRequest.of(0, 10, Sort.by("createdAt").descending());
        when(userRepository.findAll(attendu))
                .thenReturn(new PageImpl<>(List.of(user(1L, "PARENT")), attendu, 1));

        PageResponse<UserResponse> res = service.search("   ", 0, 10);

        assertThat(res.getTotalElements()).isEqualTo(1);
        verify(userRepository).findAll(attendu);
        verify(userRepository, never())
                .findByUsernameContainingOrFirstNameContainingOrLastNameContaining(
                        anyString(), anyString(), anyString(), any(Pageable.class));
    }

    @Test
    void search_avecTerme_transmetLeTermeEbarbe() {
        when(userRepository.findByUsernameContainingOrFirstNameContainingOrLastNameContaining(
                eq("kamdem"), eq("kamdem"), eq("kamdem"), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.search("  kamdem  ", 0, 10);

        verify(userRepository).findByUsernameContainingOrFirstNameContainingOrLastNameContaining(
                "kamdem", "kamdem", "kamdem",
                PageRequest.of(0, 10, Sort.by("createdAt").descending()));
    }

    // ------------------------------------------------------------------
    // Gardes structurelles
    // ------------------------------------------------------------------

    @Test
    void laListeBlancheDesRolesEstLeMiroirExactDeRoleName() throws Exception {
        Field field = UserService.class.getDeclaredField("ASSIGNABLE_ROLES");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> assignable = (Set<String>) field.get(null);

        Set<String> attendu = Arrays.stream(RoleName.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertThat(assignable)
                .as("ASSIGNABLE_ROLES doit rester le miroir de RoleName, "
                        + "sinon un rôle légitime devient inattribuable via l'API")
                .isEqualTo(attendu);
    }

    @Test
    void userResponse_neTransporteJamaisLeMotDePasse() {
        assertThat(Arrays.stream(UserResponse.class.getDeclaredFields()).map(Field::getName))
                .as("Un champ 'password' dans UserResponse exposerait le hash "
                        + "sur tous les endpoints d'administration")
                .doesNotContain("password");
    }
}
