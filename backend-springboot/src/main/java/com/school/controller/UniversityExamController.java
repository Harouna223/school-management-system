package com.school.controller;

import com.school.dto.response.ApiResponse;
import com.school.entity.UniversityExam;
import com.school.service.UniversityExamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Calendrier des examens universitaires.
 */
@RestController
@RequestMapping("/api/university-exams")
@RequiredArgsConstructor
@Tag(name = "Examens universitaires", description = "Calendrier des examens : EC, salles, surveillants, sessions")
public class UniversityExamController {

    private final UniversityExamService examService;

    @GetMapping
    @Operation(summary = "Examens planifiés (filtres : filière, date, semestre)")
    public ResponseEntity<ApiResponse<List<UniversityExam>>> list(
            @RequestParam(required = false) Long fieldId,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) String semester) {
        List<UniversityExam> result = date != null ? examService.listByDate(date)
                : semester != null ? examService.listBySemester(semester)
                : examService.listByField(fieldId);
        return ok("Examens", result);
    }

    @PostMapping
    @Operation(summary = "Planifier un examen")
    public ResponseEntity<ApiResponse<UniversityExam>> create(@RequestBody UniversityExam exam,
                                                              HttpServletRequest httpRequest) {
        return ok("Examen planifié", examService.save(exam, httpRequest));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un examen")
    public ResponseEntity<ApiResponse<UniversityExam>> update(@PathVariable Long id,
                                                              @RequestBody UniversityExam exam,
                                                              HttpServletRequest httpRequest) {
        exam.setId(id);
        return ok("Examen modifié", examService.save(exam, httpRequest));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un examen")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        examService.delete(id, httpRequest);
        return ok("Examen supprimé", null);
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).message(message).data(data)
                .timestamp(LocalDateTime.now()).build());
    }
}