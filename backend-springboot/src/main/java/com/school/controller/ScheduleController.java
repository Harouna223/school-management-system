package com.school.controller;

import com.school.dto.request.ScheduleRequest;
import com.school.dto.response.ApiResponse;
import com.school.dto.response.ScheduleResponse;
import com.school.service.ScheduleService;
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
 * Module emploi du temps : génération, conflits, vues classe/enseignant/salle.
 */
@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
@Tag(name = "Emploi du temps", description = "Génération des emplois du temps avec détection de conflits")
public class ScheduleController {

    private final ScheduleService scheduleService;

    @GetMapping
    @Operation(summary = "Tous les créneaux")
    public ResponseEntity<ApiResponse<List<ScheduleResponse>>> all() {
        return ok("Emploi du temps", scheduleService.listAll());
    }

    @GetMapping("/class/{classId}")
    @Operation(summary = "Emploi du temps d'une classe")
    public ResponseEntity<ApiResponse<List<ScheduleResponse>>> byClass(@PathVariable Long classId) {
        return ok("Emploi du temps de la classe", scheduleService.listByClass(classId));
    }

    @GetMapping("/teacher/{teacherId}")
    @Operation(summary = "Emploi du temps d'un enseignant")
    public ResponseEntity<ApiResponse<List<ScheduleResponse>>> byTeacher(@PathVariable Long teacherId) {
        return ok("Emploi du temps de l'enseignant", scheduleService.listByTeacher(teacherId));
    }

    @GetMapping("/room/{roomId}")
    @Operation(summary = "Emploi du temps d'une salle")
    public ResponseEntity<ApiResponse<List<ScheduleResponse>>> byRoom(@PathVariable Long roomId) {
        return ok("Emploi du temps de la salle", scheduleService.listByRoom(roomId));
    }

    @PostMapping
    @Operation(summary = "Créer un créneau", description = "Détecte les conflits classe/enseignant/salle")
    public ResponseEntity<ApiResponse<ScheduleResponse>> create(@Valid @RequestBody ScheduleRequest request,
                                                                HttpServletRequest httpRequest) {
        return ok("Créneau créé", scheduleService.create(request, httpRequest));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ScheduleResponse>> update(@PathVariable Long id,
                                                                @Valid @RequestBody ScheduleRequest request,
                                                                HttpServletRequest httpRequest) {
        return ok("Créneau modifié", scheduleService.update(id, request, httpRequest));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        scheduleService.delete(id, httpRequest);
        return ok("Créneau supprimé", null);
    }

    @GetMapping("/conflict")
    @Operation(summary = "Vérifier un conflit potentiel")
    public ResponseEntity<ApiResponse<Boolean>> checkConflict(
            @RequestParam com.school.enums.DayOfWeek day,
            @RequestParam java.time.LocalTime start,
            @RequestParam java.time.LocalTime end,
            @RequestParam Long classId,
            @RequestParam Long teacherId,
            @RequestParam(required = false) Long roomId) {
        return ok("Résultat du contrôle", scheduleService.hasConflict(
                day, start, end, classId, teacherId, roomId, null));
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).message(message).data(data)
                .timestamp(LocalDateTime.now()).build());
    }
}