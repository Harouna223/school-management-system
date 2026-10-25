package com.school.controller;

import com.school.dto.request.MemoireRequest;
import com.school.dto.response.ApiResponse;
import com.school.dto.response.MemoireResponse;
import com.school.service.AccessControlService;
import com.school.service.MemoireService;
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
 * Module mémoires et soutenances universitaires.
 */
@RestController
@RequestMapping("/api/memoires")
@RequiredArgsConstructor
@Tag(name = "Mémoires", description = "Mémoires et soutenances universitaires")
public class MemoireController {

    private final MemoireService memoireService;
    private final AccessControlService accessControlService;

    @GetMapping
    @Operation(summary = "Liste des mémoires (filtrer par étudiant)")
    public ResponseEntity<ApiResponse<List<MemoireResponse>>> list(@RequestParam(required = false) Long studentId) {
        if (studentId != null) accessControlService.assertCanAccessStudent(studentId);
        return ok("Mémoires", studentId != null ? memoireService.listByStudent(studentId) : memoireService.listAll());
    }

    @PostMapping
    @Operation(summary = "Créer un mémoire")
    public ResponseEntity<ApiResponse<MemoireResponse>> create(@Valid @RequestBody MemoireRequest request,
                                                               HttpServletRequest httpRequest) {
        return ok("Mémoire créé", memoireService.create(request, httpRequest));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un mémoire (jury, note, décision)")
    public ResponseEntity<ApiResponse<MemoireResponse>> update(@PathVariable Long id,
                                                               @Valid @RequestBody MemoireRequest request,
                                                               HttpServletRequest httpRequest) {
        return ok("Mémoire modifié", memoireService.update(id, request, httpRequest));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un mémoire")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        memoireService.delete(id, httpRequest);
        return ok("Mémoire supprimé", null);
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).message(message).data(data)
                .timestamp(LocalDateTime.now()).build());
    }
}
