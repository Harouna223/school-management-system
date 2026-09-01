package com.school.controller;

import com.school.dto.response.ApiResponse;
import com.school.entity.Stage;
import com.school.service.StageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Module stages universitaires.
 */
@RestController
@RequestMapping("/api/stages")
@RequiredArgsConstructor
@Tag(name = "Stages", description = "Stages universitaires : entreprises, conventions, soutenances")
public class StageController {

    private final StageService stageService;

    @GetMapping
    @Operation(summary = "Liste des stages")
    public ResponseEntity<ApiResponse<List<Stage>>> list(@RequestParam(required = false) Long studentId) {
        return ok("Stages", studentId != null ? stageService.listByStudent(studentId) : stageService.listAll());
    }

    @PostMapping
    @Operation(summary = "Créer un stage")
    public ResponseEntity<ApiResponse<Stage>> create(@RequestBody Stage stage, HttpServletRequest httpRequest) {
        return ok("Stage créé", stageService.create(stage, httpRequest));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un stage (rapport, soutenance, note)")
    public ResponseEntity<ApiResponse<Stage>> update(@PathVariable Long id, @RequestBody Stage stage,
                                                     HttpServletRequest httpRequest) {
        return ok("Stage modifié", stageService.update(id, stage, httpRequest));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un stage")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        stageService.delete(id, httpRequest);
        return ok("Stage supprimé", null);
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).message(message).data(data)
                .timestamp(LocalDateTime.now()).build());
    }
}