package com.school.controller;

import com.school.dto.request.AlumnusRequest;
import com.school.dto.response.AlumnusResponse;
import com.school.dto.response.ApiResponse;
import com.school.service.AlumnusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Module diplômés (alumni).
 */
@RestController
@RequestMapping("/api/alumni")
@RequiredArgsConstructor
@Tag(name = "Alumni", description = "Diplômés et anciens étudiants")
public class AlumnusController {

    private final AlumnusService alumnusService;

    @GetMapping
    @Operation(summary = "Liste des diplômés")
    public ResponseEntity<ApiResponse<List<AlumnusResponse>>> list(@RequestParam(required = false) String fieldName) {
        return ok("Diplômés", alumnusService.listByField(fieldName));
    }

    @PostMapping
    @Operation(summary = "Ajouter un diplômé")
    public ResponseEntity<ApiResponse<AlumnusResponse>> create(@Valid @RequestBody AlumnusRequest request,
                                                               HttpServletRequest httpRequest) {
        return ok("Diplômé ajouté", alumnusService.create(request, httpRequest));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Mettre à jour le parcours d'un diplômé")
    public ResponseEntity<ApiResponse<AlumnusResponse>> update(@PathVariable Long id,
                                                               @Valid @RequestBody AlumnusRequest request,
                                                               HttpServletRequest httpRequest) {
        return ok("Diplômé mis à jour", alumnusService.update(id, request, httpRequest));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un diplômé")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        alumnusService.delete(id, httpRequest);
        return ok("Diplômé supprimé", null);
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).message(message).data(data)
                .timestamp(LocalDateTime.now()).build());
    }
}
