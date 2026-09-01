package com.school.service;

import com.school.dto.request.ChangePasswordRequest;
import com.school.dto.request.LoginRequest;
import com.school.dto.request.RefreshTokenRequest;
import com.school.dto.request.RegisterRequest;
import com.school.dto.response.AuthResponse;
import com.school.dto.response.UserResponse;
import com.school.entity.RefreshToken;
import com.school.entity.Role;
import com.school.entity.User;
import com.school.exception.BusinessException;
import com.school.exception.TokenRefreshException;
import com.school.repository.RefreshTokenRepository;
import com.school.repository.RoleRepository;
import com.school.repository.UserRepository;
import com.school.security.JwtService;
import com.school.utils.PasswordPolicy;
import com.school.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Authentification : login JWT, refresh token, logout, création de comptes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    /** Nombre maximal de tentatives avant verrouillage du compte. */
    private static final int MAX_ATTEMPTS = 5;
    /** Durée du verrouillage en minutes. */
    private static final long LOCK_MINUTES = 15;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        User user = userRepository.findByUsername(request.getUsername()).orElse(null);

        if (user != null && user.getLockedUntil() != null
                && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            long minutes = java.time.Duration.between(LocalDateTime.now(), user.getLockedUntil()).toMinutes() + 1;
            throw new BusinessException("Compte temporairement verrouillé après plusieurs tentatives. "
                    + "Réessayez dans " + minutes + " min.");
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        } catch (LockedException ex) {
            throw new BusinessException("Compte verrouillé. Contactez l'administrateur.");
        } catch (DisabledException ex) {
            throw new BusinessException("Ce compte est désactivé. Contactez l'administrateur.");
        } catch (AuthenticationException ex) {
            if (user != null) {
                registerFailedAttempt(user);
            }
            throw ex;
        }

        user = (User) authentication.getPrincipal();
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        String accessToken = jwtService.generateToken(user);
        String refreshToken = createRefreshToken(user);

        auditService.log("LOGIN", "User", user.getId(), "Connexion réussie", httpRequest);
        log.info("Connexion réussie : {}", user.getUsername());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(3600000)
                .user(UserResponse.from(user))
                .build();
    }

    /**
     * Enregistre un échec de connexion et verrouille le compte au-delà du seuil.
     */
    private void registerFailedAttempt(User user) {
        user.setFailedAttempts(user.getFailedAttempts() + 1);
        if (user.getFailedAttempts() >= MAX_ATTEMPTS) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
            user.setFailedAttempts(0);
            log.warn("Compte verrouillé {} min après {} échecs : {}", LOCK_MINUTES, MAX_ATTEMPTS,
                    user.getUsername());
        }
        userRepository.save(user);
    }

    /**
     * Changement de mot de passe (self-service) : vérifie l'ancien mot de passe
     * et applique la politique de complexité.
     */
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = SecurityUtils.currentUser();
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BusinessException("Le mot de passe actuel est incorrect");
        }
        PasswordPolicy.validate(request.getNewPassword());
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        auditService.log("PASSWORD_CHANGE", "User", user.getId(), "Changement de mot de passe", null);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new TokenRefreshException(request.getRefreshToken(),
                        "Jeton de rafraîchissement inconnu"));

        if (refreshToken.isRevoked() || refreshToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new TokenRefreshException(request.getRefreshToken(),
                    "Jeton de rafraîchissement expiré ou révoqué, veuillez vous reconnecter");
        }

        User user = refreshToken.getUser();
        String newAccessToken = jwtService.generateToken(user);
        String newRefreshToken = createRefreshToken(user);
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(3600000)
                .user(UserResponse.from(user))
                .build();
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken)
                .ifPresent(rt -> {
                    rt.setRevoked(true);
                    refreshTokenRepository.save(rt);
                });
    }

    /**
     * Création d'un compte utilisateur avec rôles.
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("Ce nom d'utilisateur est déjà pris");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Cet email est déjà utilisé");
        }
        PasswordPolicy.validate(request.getPassword());

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .enabled(true)
                .build();

        Set<Role> roles = new HashSet<>();
        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            for (String roleName : request.getRoles()) {
                Role role = roleRepository.findByName(roleName)
                        .orElseThrow(() -> new BusinessException("Rôle inconnu : " + roleName));
                roles.add(role);
            }
        } else {
            roles.add(roleRepository.findByName("ELEVE")
                    .orElseThrow(() -> new BusinessException("Rôle ELEVE non configuré")));
        }
        user.setRoles(roles);
        userRepository.save(user);
        return UserResponse.from(user);
    }

    /**
     * Création d'un compte pour un élève / enseignant (auto).
     */
    @Transactional
    public User createLinkedAccount(String username, String rawPassword, String email,
                                    String firstName, String lastName, String roleName) {
        if (userRepository.existsByUsername(username)) {
            return userRepository.findByUsername(username)
                    .orElseThrow(() -> new com.school.exception.BusinessException(
                            "Incohérence : utilisateur trouvé puis perdu"));
        }
        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(rawPassword))
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .enabled(true)
                .build();
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new BusinessException("Rôle non configuré : " + roleName));
        user.getRoles().add(role);
        return userRepository.save(user);
    }

    private String createRefreshToken(User user) {
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(token)
                .expiryDate(LocalDateTime.now().plus(refreshExpirationMs, java.time.temporal.ChronoUnit.MILLIS))
                .build();
        refreshTokenRepository.save(refreshToken);
        return token;
    }
}