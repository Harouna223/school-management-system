package com.school.controller;

import com.school.dto.response.ApiResponse;
import com.school.dto.response.PageResponse;
import com.school.dto.response.UserResponse;
import com.school.entity.Role;
import com.school.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Administration : utilisateurs, rôles, comptes.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Utilisateurs", description = "Administration des comptes et rôles")
public class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Liste des utilisateurs")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> search(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ok("Utilisateurs", userService.search(search, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getById(@PathVariable Long id) {
        return ok("Utilisateur trouvé", userService.getById(id));
    }

    @GetMapping("/roles")
    @Operation(summary = "Liste des rôles")
    public ResponseEntity<ApiResponse<List<Role>>> roles() {
        return ok("Rôles", userService.listRoles());
    }

    @PutMapping("/{id}/roles")
    @Operation(summary = "Attribuer des rôles à un utilisateur")
    public ResponseEntity<ApiResponse<UserResponse>> updateRoles(@PathVariable Long id,
                                                                 @RequestBody Set<String> roleNames,
                                                                 HttpServletRequest httpRequest) {
        return ok("Rôles mis à jour", userService.updateRoles(id, roleNames, httpRequest));
    }

@PatchMapping("/{id}/enabled")
    @Operation(summary = "Activer / désactiver un compte")
    public ResponseEntity<ApiResponse<UserResponse>> toggleEnabled(@PathVariable Long id,
                                                                   @RequestBody(required = false) Map<String, Boolean> body,
                                                                   HttpServletRequest httpRequest) {
        boolean enabled = body != null ? body.getOrDefault("enabled", true) : true;
        return ok("Compte mis à jour",
                userService.toggleEnabled(id, enabled, httpRequest));
    }

    @PostMapping("/{id}/unlock")
    @Operation(summary = "Déverrouiller un compte verrouillé")
    public ResponseEntity<ApiResponse<UserResponse>> unlock(@PathVariable Long id,
                                                            HttpServletRequest httpRequest) {
        return ok("Compte déverrouillé", userService.unlock(id, httpRequest));
    }

    @PostMapping("/{id}/reset-password")
    @Operation(summary = "Réinitialiser le mot de passe")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@PathVariable Long id,
                                                           @RequestBody(required = false) Map<String, String> body,
                                                           HttpServletRequest httpRequest) {
        String newPassword = body != null ? body.get("newPassword") : null;
        if (newPassword == null || newPassword.isBlank()) {
            throw new com.school.exception.BusinessException("Le nouveau mot de passe est requis");
        }
        userService.resetPassword(id, newPassword, httpRequest);
        return ok("Mot de passe réinitialisé", null);
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).message(message).data(data)
                .timestamp(LocalDateTime.now()).build());
    }
}