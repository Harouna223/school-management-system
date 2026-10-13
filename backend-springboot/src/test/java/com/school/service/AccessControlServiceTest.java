package com.school.service;

import com.school.entity.Parent;
import com.school.entity.Student;
import com.school.entity.User;
import com.school.exception.BusinessException;
import com.school.repository.ParentRepository;
import com.school.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Tests du contrôle d'accès centralisé (IDOR).
 * Scénarios : parent peut voir ses enfants, élève peut voir son profil,
 * parent refuse un enfant non lié, élève refuse un autre profil.
 */
@ExtendWith(MockitoExtension.class)
class AccessControlServiceTest {

    @Mock
    private StudentRepository studentRepository;
    @Mock
    private ParentRepository parentRepository;

    private AccessControlService service;

    @BeforeEach
    void setUp() {
        service = new AccessControlService(studentRepository, parentRepository);
    }

    private User user(Long id, String role) {
        com.school.entity.Role r = com.school.entity.Role.builder().name(role).build();
        return User.builder().id(id).username("user" + id).roles(new java.util.HashSet<>(Set.of(r))).build();
    }

    /** Active le mock statique SecurityUtils avec un utilisateur et un rôle donnés. */
    private MockedStatic<com.school.utils.SecurityUtils> mockSecurity(User user) {
        MockedStatic<com.school.utils.SecurityUtils> utils =
                mockStatic(com.school.utils.SecurityUtils.class);
        utils.when(com.school.utils.SecurityUtils::currentUser).thenReturn(user);
        utils.when(com.school.utils.SecurityUtils::currentUserId).thenReturn(user.getId());
        utils.when(() -> com.school.utils.SecurityUtils.hasRole(anyString())).thenCallRealMethod();
        return utils;
    }

    private Student student(Long id, Parent parent) {
        return Student.builder().id(id).firstName("Enfant").lastName("Test" + id)
                .parent(parent).build();
    }

    private Parent parent(Long id, User user) {
        return Parent.builder().id(id).user(user).build();
    }

    @Test
    void parentCanAccessOwnChild() {
        User user = user(1L, "PARENT");
        Parent parent = parent(10L, user);
        Student child = student(20L, parent);
        when(parentRepository.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(parent));
        when(studentRepository.findById(20L)).thenReturn(Optional.of(child));

        try (MockedStatic<com.school.utils.SecurityUtils> utils = mockSecurity(user)) {
            assertThatNoException().isThrownBy(() -> service.assertCanAccessStudent(20L));
        }
    }

    @Test
    void parentCannotAccessOtherChild() {
        User user = user(1L, "PARENT");
        Parent parent = parent(10L, user);
        Student otherChild = student(30L, parent(20L, user(2L, "PARENT")));
        when(parentRepository.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(parent));
        when(studentRepository.findById(30L)).thenReturn(Optional.of(otherChild));

        try (MockedStatic<com.school.utils.SecurityUtils> utils = mockSecurity(user)) {
            assertThatThrownBy(() -> service.assertCanAccessStudent(30L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Accès refusé");
        }
    }

    @Test
    void eleveCanAccessOwnProfile() {
        User user = user(1L, "ELEVE");
        Student ownProfile = student(1L, null);
        when(studentRepository.findByUserId(1L)).thenReturn(Optional.of(ownProfile));

        try (MockedStatic<com.school.utils.SecurityUtils> utils = mockSecurity(user)) {
            assertThatNoException().isThrownBy(() -> service.assertCanAccessStudent(1L));
        }
    }

    @Test
    void eleveCannotAccessOtherProfile() {
        User user = user(1L, "ELEVE");
        Student ownProfile = student(1L, null);
        when(studentRepository.findByUserId(1L)).thenReturn(Optional.of(ownProfile));

        try (MockedStatic<com.school.utils.SecurityUtils> utils = mockSecurity(user)) {
            assertThatThrownBy(() -> service.assertCanAccessStudent(2L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Accès refusé");
        }
    }

    @Test
    void adminCanAccessAnyStudent() {
        User user = user(1L, "SUPER_ADMIN");
        try (MockedStatic<com.school.utils.SecurityUtils> utils = mockSecurity(user)) {
            assertThatNoException().isThrownBy(() -> service.assertCanAccessStudent(999L));
        }
    }

    // ------------------------------------------------------------------
    // Parents multi-dossiers : un compte peut avoir plusieurs dossiers Parent
    // ------------------------------------------------------------------

    @Test
    void parentMultiDossiersAccedeATousSesEnfants() {
        User user = user(1L, "PARENT");
        Parent premierDossier = parent(10L, user);
        Parent secondDossier = parent(20L, user);
        Student enfantDuSecond = student(30L, secondDossier);
        // Un seul dossier ne doit plus suffire : retenir le premier (findFirst)
        // refusait l'accès aux enfants des dossiers suivants, de façon
        // incohérente selon l'endpoint (liste OK, fiche refusée).
        when(parentRepository.findByUserIdOrderByIdAsc(1L))
                .thenReturn(List.of(premierDossier, secondDossier));
        when(studentRepository.findById(30L)).thenReturn(Optional.of(enfantDuSecond));

        try (MockedStatic<com.school.utils.SecurityUtils> utils = mockSecurity(user)) {
            assertThatNoException().isThrownBy(() -> service.assertCanAccessStudent(30L));
        }
    }

    // ------------------------------------------------------------------
    // Périmètre des listes : un identifiant client ne peut pas l'élargir
    // ------------------------------------------------------------------

    @Test
    void resolveStudentScopeRefuseUnParentSansDossier() {
        User user = user(1L, "PARENT");
        when(parentRepository.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of());

        try (MockedStatic<com.school.utils.SecurityUtils> utils = mockSecurity(user)) {
            assertThatThrownBy(() -> service.resolveStudentScope(null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Aucun dossier parent");
        }
    }

    @Test
    void resolveStudentScopeRefuseUnIdentifiantEtrangerPourUnParent() {
        User user = user(1L, "PARENT");
        Parent dossier = parent(10L, user);
        Student enfantDunAutre = student(30L, parent(20L, user(2L, "PARENT")));
        when(parentRepository.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(dossier));
        when(studentRepository.findById(30L)).thenReturn(Optional.of(enfantDunAutre));

        try (MockedStatic<com.school.utils.SecurityUtils> utils = mockSecurity(user)) {
            assertThatThrownBy(() -> service.resolveStudentScope(30L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Accès refusé");
        }
    }

    @Test
    void resolveStudentScopeNeChangeRienPourUnAdmin() {
        User user = user(1L, "SUPER_ADMIN");
        try (MockedStatic<com.school.utils.SecurityUtils> utils = mockSecurity(user)) {
            var sansFiltre = service.resolveStudentScope(null);
            assertThat(sansFiltre.unrestricted()).isTrue();
            assertThat(sansFiltre.requestedId()).isNull();

            var avecFiltre = service.resolveStudentScope(42L);
            assertThat(avecFiltre.unrestricted()).isTrue();
            assertThat(avecFiltre.requestedId()).isEqualTo(42L);
            assertThat(avecFiltre.isEmpty()).isFalse();
        }
    }

    @Test
    void resolveStudentScopeRameneUnEleveASonPropreProfil() {
        User user = user(1L, "ELEVE");
        Student profil = student(1L, null);
        when(studentRepository.findByUserId(1L)).thenReturn(Optional.of(profil));

        try (MockedStatic<com.school.utils.SecurityUtils> utils = mockSecurity(user)) {
            var scope = service.resolveStudentScope(null);
            assertThat(scope.unrestricted()).isFalse();
            assertThat(scope.studentIds()).containsExactly(1L);
        }
    }

    @Test
    void resolveStudentScopeParentSansFiltreCouvreTousSesEnfants() {
        User user = user(1L, "PARENT");
        Parent premier = parent(10L, user);
        Parent second = parent(20L, user);
        when(parentRepository.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(premier, second));
        when(studentRepository.findByParentIdIn(List.of(10L, 20L)))
                .thenReturn(List.of(student(30L, second), student(31L, premier)));

        try (MockedStatic<com.school.utils.SecurityUtils> utils = mockSecurity(user)) {
            var scope = service.resolveStudentScope(null);
            assertThat(scope.unrestricted()).isFalse();
            assertThat(scope.studentIds()).containsExactlyInAnyOrder(30L, 31L);
            assertThat(scope.requestedId()).isNull();
        }
    }

    @Test
    void resolveStudentScopeParentSansEnfantNeRenvoieRien() {
        User user = user(1L, "PARENT");
        Parent dossier = parent(10L, user);
        when(parentRepository.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(dossier));
        when(studentRepository.findByParentIdIn(List.of(10L))).thenReturn(List.of());

        try (MockedStatic<com.school.utils.SecurityUtils> utils = mockSecurity(user)) {
            var scope = service.resolveStudentScope(null);
            // Un compte parent sans enfant ne doit PAS retomber sur « aucune
            // restriction » (c'était la fuite : tout l'établissement visible).
            assertThat(scope.unrestricted()).isFalse();
            assertThat(scope.isEmpty()).isTrue();
        }
    }

    // ------------------------------------------------------------------
    // Données « établissement » : interdites aux comptes parent / élève
    // ------------------------------------------------------------------

    @Test
    void staffOnlyRefuseUnParentEtUnEleve() {
        try (MockedStatic<com.school.utils.SecurityUtils> utils = mockSecurity(user(1L, "PARENT"))) {
            assertThatThrownBy(() -> service.assertStaffOnly("le pointage d'une classe"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Accès refusé");
        }
        try (MockedStatic<com.school.utils.SecurityUtils> utils = mockSecurity(user(2L, "ELEVE"))) {
            assertThatThrownBy(() -> service.assertStaffOnly("le pointage d'une classe"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Accès refusé");
        }
    }

    @Test
    void staffOnlyLaissePasserLePersonnel() {
        for (String role : List.of("SUPER_ADMIN", "DIRECTEUR", "SECRETAIRE", "COMPTABLE", "ENSEIGNANT")) {
            try (MockedStatic<com.school.utils.SecurityUtils> utils = mockSecurity(user(1L, role))) {
                assertThatNoException().isThrownBy(() -> service.assertStaffOnly("le pointage d'une classe"));
            }
        }
    }
}