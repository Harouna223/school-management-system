package com.school.controller;

import com.school.dto.request.ChangePasswordRequest;
import com.school.dto.request.LoginRequest;
import com.school.dto.request.RefreshTokenRequest;
import com.school.dto.request.RegisterRequest;
import com.school.dto.response.ApiResponse;
import com.school.dto.response.AuthResponse;
import com.school.dto.response.UserResponse;
import com.school.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Endpoints d'authentification : connexion, rafraîchissement, déconnexion.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification", description = "Connexion, refresh token, logout")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Connexion utilisateur", description = "Retourne un JWT d'accès + refresh token")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request,
                                                           HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.<AuthResponse>builder()
                .success(true)
                .message("Connexion réussie")
                .data(authService.login(request, httpRequest))
                .timestamp(LocalDateTime.now())
                .build());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rafraîchir le JWT", description = "Échange un refresh token contre un nouveau couple de jetons")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.<AuthResponse>builder()
                .success(true)
                .message("Jeton rafraîchi")
                .data(authService.refresh(request))
                .timestamp(LocalDateTime.now())
                .build());
    }

    @PostMapping("/logout")
    @Operation(summary = "Déconnexion", description = "Révoque le refresh token")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        if (request != null && request.getRefreshToken() != null) {
            authService.logout(request.getRefreshToken());
        }
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Déconnexion réussie")
                .timestamp(LocalDateTime.now())
                .build());
    }

    @PostMapping("/register")
    @Operation(summary = "Créer un compte", description = "Crée un utilisateur avec rôles (réservé aux administrateurs)")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(ApiResponse.<UserResponse>builder()
                .success(true)
                .message("Compte créé avec succès")
                .data(authService.register(request))
                .timestamp(LocalDateTime.now())
                .build());
    }

    @PostMapping("/change-password")
    @Operation(summary = "Changer son mot de passe", description = "Vérifie l'ancien mot de passe puis applique la politique de complexité")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Mot de passe modifié avec succès")
                .timestamp(LocalDateTime.now())
                .build());
    }
}