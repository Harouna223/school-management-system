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
}