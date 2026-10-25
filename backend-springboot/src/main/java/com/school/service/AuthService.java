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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
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
    private final LoginAttemptService loginAttemptService;

    /** Nombre maximal de tentatives avant verrouillage du compte. */
    private static final int MAX_ATTEMPTS = LoginAttemptService.MAX_ATTEMPTS;
    /** Durée du verrouillage en minutes. */
    private static final long LOCK_MINUTES = LoginAttemptService.LOCK_MINUTES;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    @Value("${app.jwt.expiration-ms}")
    private long accessExpirationMs;

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
                .expiresIn(accessExpirationMs)
                .user(UserResponse.from(user))
                .build();
    }

    /**
     * Enregistre un échec de connexion et verrouille le compte au-delà du seuil.
     *
     * <p>L'écriture se fait dans une transaction INDÉPENDANTE : le rollback de
     * {@link #login} (provoqué par l'exception d'authentification relancée)
     * annulait auparavant le compteur, rendant le verrouillage inopérant.</p>
     */
    private void registerFailedAttempt(User user) {
        if (user.getId() == null) {
            return; // utilisateur non persisté : rien à comptabiliser
        }
        loginAttemptService.registerFailedAttempt(user.getId());
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
        String token = request.getRefreshToken();
        RefreshToken refreshToken = refreshTokenRepository.findByToken(hashRefreshToken(token))
                .orElseThrow(() -> new TokenRefreshException(token,
                        "Jeton de rafraîchissement inconnu"));

        if (refreshToken.isRevoked() || refreshToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            // Réutilisation d'un jeton déjà consommé (vol probable) : on révoque
            // TOUTE la famille de jetons de l'utilisateur, pas seulement celui-ci.
            // Transaction indépendante : l'exception ci-dessous annulerait la
            // suppression si elle partageait la transaction de refresh().
            loginAttemptService.revokeAllRefreshTokens(refreshToken.getUser().getId());
            throw new TokenRefreshException(request.getRefreshToken(),
                    "Jeton de rafraîchissement expiré ou révoqué, veuillez vous reconnecter");
        }

        User user = refreshToken.getUser();
        // Un compte désactivé ou verrouillé ne doit plus pouvoir obtenir de jeton,
        // même en présentant un refresh token encore valide : cette garde existait
        // uniquement dans le filtre JWT, contournable via /api/auth/refresh.
        if (!user.isEnabled() || !user.isAccountNonLocked()) {
            loginAttemptService.revokeAllRefreshTokens(user.getId());
            throw new TokenRefreshException(request.getRefreshToken(),
                    "Compte désactivé ou verrouillé : veuillez contacter l'administrateur");
        }

        String newAccessToken = jwtService.generateToken(user);
        String newRefreshToken = createRefreshToken(user);
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(accessExpirationMs)
                .user(UserResponse.from(user))
                .build();
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByToken(hashRefreshToken(refreshToken))
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

    /**
     * Retire l'accès du compte rattaché à un dossier (élève, enseignant).
     *
     * <p>Sans cette révocation, supprimer ou radier un dossier laissait un
     * identifiant de connexion parfaitement valide : l'ancien élève continuait
     * de se connecter avec le rôle {@code ELEVE}. La désactivation bloque les
     * nouvelles connexions, mais les jetons de rafraîchissement déjà émis
     * survivraient et renouvelleraient la session en cours : ils sont donc
     * supprimés.</p>
     */
    @Transactional
    public void revokeLinkedAccount(User user) {
        if (user == null || !user.isEnabled()) {
            return;
        }
        user.setEnabled(false);
        userRepository.save(user);
        refreshTokenRepository.deleteByUserId(user.getId());
    }

    /**
     * Rétablit l'accès d'un compte rattaché (réinscription, retour à {@code ACTIVE}).
     *
     * <p>Le mot de passe est inchangé : l'utilisateur retrouve ses identifiants
     * d'origine.</p>
     */
    @Transactional
    public void restoreLinkedAccount(User user) {
        if (user == null || user.isEnabled()) {
            return;
        }
        user.setEnabled(true);
        userRepository.save(user);
    }

    private String createRefreshToken(User user) {
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                // Seul le HASH (SHA-256, préfixé « sha256: ») est stocké en base :
                // une fuite de la table refresh_tokens ne permet plus de réutiliser
                // les jetons. Le jeton brut n'est remis qu'au client, une seule fois.
                .token(hashRefreshToken(token))
                .expiryDate(LocalDateTime.now().plus(refreshExpirationMs, java.time.temporal.ChronoUnit.MILLIS))
                .build();
        refreshTokenRepository.save(refreshToken);
        return token;
    }

    /**
     * Purge les refresh tokens révoqués ou expirés. La table grossissait
     * indéfiniment (un jeton par connexion, jamais effacé) : 52 % des lignes
     * étaient des jetons révoqués inutiles.
     *
     * @return nombre de jetons supprimés
     */
    @Transactional
    public int purgeStaleRefreshTokens() {
        return refreshTokenRepository.deleteStale(LocalDateTime.now());
    }

    /**
     * Purge quotidienne des refresh tokens révoqués / expirés (3 h 30).
     *
     * <p><b>Le {@code @Transactional} doit être ICI, sur le point d'entrée.</b>
     * Le planificateur de Spring invoque cette méthode à travers le proxy
     * transactionnel : c'est le seul chemin qui ouvre réellement une transaction.
     * Posé uniquement sur {@link #purgeStaleRefreshTokens()}, il était
     * <em>inerte</em> : l'appel ci-dessous est une auto-invocation
     * ({@code this.…}) qui contourne le proxy, si bien que la requête
     * {@code @Modifying} {@code deleteStale} s'exécutait sans transaction et
     * levait {@code TransactionRequiredException} (« Executing an update/delete
     * query ») — chaque nuit à 3 h 30. Le test unitaire ne pouvait pas le voir
     * (un mock ne réclame aucune transaction) ; c'est
     * {@code ScheduledTasksIT} qui l'a mis en évidence.</p>
     */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void scheduledPurgeStaleRefreshTokens() {
        int purged = purgeStaleRefreshTokens();
        if (purged > 0) {
            log.info("Purge : {} refresh token(s) révoqué(s) ou expiré(s) supprimé(s)", purged);
        }
    }

    /**
     * Hashage SHA-256 (hex, préfixe « sha256: ») d'un refresh token brut.
     * Le préfixe rend la transformation idempotente et permet de distinguer les
     * jetons déjà hachés d'éventuels reliquats en clair.
     */
    static String hashRefreshToken(String rawToken) {
        if (rawToken == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}