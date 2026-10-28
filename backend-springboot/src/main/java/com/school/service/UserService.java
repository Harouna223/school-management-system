package com.school.service;

import com.school.dto.response.PageResponse;
import com.school.dto.response.UserResponse;
import com.school.entity.Role;
import com.school.entity.User;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.RoleRepository;
import com.school.repository.UserRepository;
import com.school.utils.PasswordPolicy;
import com.school.utils.Pagination;
import com.school.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Administration des utilisateurs et rôles.
 *
 * <p>Garde-fous d'escalade de privilèges : les rôles attribuables sont limités
 * à une liste blanche, seul un SUPER_ADMIN peut attribuer SUPER_ADMIN, personne
 * ne peut modifier ses propres rôles ni désactiver son propre compte, et le
 * dernier SUPER_ADMIN actif est protégé.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService {

    /** Rôles attribuables via l'API (miroir de {@code RoleName}). */
    private static final Set<String> ASSIGNABLE_ROLES = Set.of(
            "SUPER_ADMIN", "DIRECTEUR", "COMPTABLE", "SECRETAIRE", "ENSEIGNANT", "PARENT", "ELEVE");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public PageResponse<UserResponse> search(String search, int page, int size) {
        Pageable pageable = Pagination.of(page, size, Sort.by("createdAt").descending());
        Page<User> result = (search == null || search.isBlank())
                ? userRepository.findAll(pageable)
                : userRepository.findByUsernameContainingOrFirstNameContainingOrLastNameContaining(
                        search.trim(), search.trim(), search.trim(), pageable);
        return PageResponse.from(result, UserResponse::from);
    }

    public List<UserResponse> findAll() {
        return userRepository.findAll(Sort.by("username")).stream()
                .map(UserResponse::from).toList();
    }

    public UserResponse getById(Long id) {
        return UserResponse.from(findById(id));
    }

    public List<Role> listRoles() {
        return roleRepository.findAll(Sort.by("name"));
    }

    @Transactional
    public UserResponse updateRoles(Long id, Set<String> roleNames, HttpServletRequest httpRequest) {
        User user = findById(id);
        assertNotSelf(id, "modifier vos propres rôles");
        if (roleNames == null || roleNames.isEmpty()) {
            throw new BusinessException("Au moins un rôle doit être attribué");
        }
        Set<Role> roles = new HashSet<>();
        for (String name : roleNames) {
            if (name == null || !ASSIGNABLE_ROLES.contains(name)) {
                throw new BusinessException("Rôle non attribuable : " + name);
            }
            Role role = roleRepository.findByName(name)
                    .orElseThrow(() -> new BusinessException("Rôle inconnu : " + name));
            roles.add(role);
        }
        boolean gainsSuperAdmin = roles.stream().anyMatch(r -> "SUPER_ADMIN".equals(r.getName()));
        boolean losesSuperAdmin = user.getRoles().stream().anyMatch(r -> "SUPER_ADMIN".equals(r.getName()))
                && !gainsSuperAdmin;
        if (gainsSuperAdmin && !SecurityUtils.hasRole("SUPER_ADMIN")) {
            throw new BusinessException("Seul un SUPER_ADMIN peut attribuer le rôle SUPER_ADMIN");
        }
        if (losesSuperAdmin) {
            assertNotLastSuperAdmin(user, "retirer le rôle SUPER_ADMIN");
        }
        user.setRoles(roles);
        UserResponse saved = UserResponse.from(userRepository.save(user));
        auditService.log("ROLES_UPDATE", "User", id,
                "Rôles de " + saved.getUsername() + " : " + String.join(", ", roleNames), httpRequest);
        return saved;
    }

    @Transactional
    public UserResponse toggleEnabled(Long id, boolean enabled, HttpServletRequest httpRequest) {
        User user = findById(id);
        if (!enabled) {
            assertNotSelf(id, "désactiver votre propre compte");
            if (hasRole(user, "SUPER_ADMIN")) {
                assertNotLastSuperAdmin(user, "désactiver ce compte SUPER_ADMIN");
            }
        }
        user.setEnabled(enabled);
        user.setLockedUntil(null);
        user.setFailedAttempts(0);
        UserResponse saved = UserResponse.from(userRepository.save(user));
        auditService.log("TOGGLE_ENABLED", "User", id,
                "Compte " + saved.getUsername() + (enabled ? " activé" : " désactivé"), httpRequest);
        return saved;
    }

    @Transactional
    public UserResponse unlock(Long id, HttpServletRequest httpRequest) {
        User user = findById(id);
        user.setLockedUntil(null);
        user.setFailedAttempts(0);
        UserResponse saved = UserResponse.from(userRepository.save(user));
        auditService.log("UNLOCK", "User", id,
                "Déverrouillage du compte " + saved.getUsername(), httpRequest);
        return saved;
    }

    @Transactional
    public void resetPassword(Long id, String newPassword, HttpServletRequest httpRequest) {
        PasswordPolicy.validate(newPassword);
        User user = findById(id);
        assertNotSelf(id, "réinitialiser votre propre mot de passe depuis cette API");
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        auditService.log("PASSWORD_RESET", "User", id,
                "Réinitialisation du mot de passe de " + user.getUsername(), httpRequest);
    }

    /** Interdit toute action d'administration sur son propre compte. */
    private void assertNotSelf(Long targetId, String action) {
        Long currentId = SecurityUtils.currentUserId();
        if (currentId != null && currentId.equals(targetId)) {
            throw new BusinessException("Action impossible : vous ne pouvez pas " + action
                    + ". Demandez à un autre administrateur.");
        }
    }

    /**
     * Interdit de laisser l'établissement sans SUPER_ADMIN actif : sans cette
     * garde, un dernier super-administrateur rétrogradé ou désactivé rendait la
     * plateforme définitivement non administrable.
     */
    private void assertNotLastSuperAdmin(User user, String action) {
        boolean anotherActiveSuperAdmin = userRepository.findAll().stream()
                .filter(other -> !other.getId().equals(user.getId()))
                .filter(User::isEnabled)
                .anyMatch(other -> hasRole(other, "SUPER_ADMIN"));
        if (!anotherActiveSuperAdmin) {
            throw new BusinessException("Action impossible : " + action
                    + " laisserait l'établissement sans SUPER_ADMIN actif.");
        }
    }

    private boolean hasRole(User user, String roleName) {
        return user.getRoles() != null && user.getRoles().stream()
                .anyMatch(r -> roleName.equals(r.getName()));
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Utilisateur", id));
    }
}