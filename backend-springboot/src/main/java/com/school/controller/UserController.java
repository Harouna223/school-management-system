package com.school.controller;

import com.school.dto.response.ApiResponse;
import com.school.dto.response.PageResponse;
import com.school.dto.response.UserResponse;
import com.school.entity.Role;
import com.school.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
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
@Validated
@Tag(name = "Utilisateurs", description = "Administration des comptes et rôles")
public class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Liste des utilisateurs")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> search(
            @RequestParam(required = false) String search,
            @Min(value = 0, message = "Le numéro de page ne peut pas être négatif") @RequestParam(defaultValue = "0") int page,
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
    @Operation(summary = "Activer / désactiver un compte",
            description = "Le corps doit contenir explicitement {\"enabled\": true|false}")
    public ResponseEntity<ApiResponse<UserResponse>> toggleEnabled(@PathVariable Long id,
                                                                   @RequestBody Map<String, Boolean> body,
                                                                   HttpServletRequest httpRequest) {
        Boolean enabled = body.get("enabled");
        if (enabled == null) {
            // Auparavant la valeur par défaut était « true » : un corps vide ou
            // mal formé RÉACTIVAIT le compte et effaçait son verrouillage.
            throw new com.school.exception.BusinessException(
                    "Le champ « enabled » est obligatoire (true pour activer, false pour désactiver)");
        }
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