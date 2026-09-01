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
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public PageResponse<UserResponse> search(String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
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
        Set<Role> roles = new HashSet<>();
        for (String name : roleNames) {
            Role role = roleRepository.findByName(name)
                    .orElseThrow(() -> new BusinessException("Rôle inconnu : " + name));
            roles.add(role);
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
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        auditService.log("PASSWORD_RESET", "User", id,
                "Réinitialisation du mot de passe de " + user.getUsername(), httpRequest);
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Utilisateur", id));
    }
}