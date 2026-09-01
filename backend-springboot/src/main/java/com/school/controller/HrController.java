package com.school.controller;

import com.school.dto.request.ContractRequest;
import com.school.dto.request.LeaveRequest;
import com.school.dto.request.PayrollGenerateRequest;
import com.school.dto.response.ApiResponse;
import com.school.dto.response.ContractResponse;
import com.school.dto.response.LeaveResponse;
import com.school.dto.response.PayrollResponse;
import com.school.enums.ContractStatus;
import com.school.enums.LeaveStatus;
import com.school.enums.PayrollStatus;
import com.school.service.HrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Module RH : congés, contrats et paie du personnel.
 */
@RestController
@RequestMapping("/api/hr")
@RequiredArgsConstructor
@Tag(name = "Ressources Humaines", description = "Congés, contrats et paie du personnel")
public class HrController {

    private final HrService hrService;

    @GetMapping("/leaves")
    @Operation(summary = "Liste des congés")
    public ResponseEntity<ApiResponse<List<LeaveResponse>>> leaves(
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) LeaveStatus status) {
        return ok("Congés", hrService.listLeaves(teacherId, status));
    }

    @PostMapping("/leaves")
    @Operation(summary = "Demander un congé")
    public ResponseEntity<ApiResponse<LeaveResponse>> requestLeave(
            @Valid @RequestBody LeaveRequest request, HttpServletRequest httpRequest) {
        return ok("Demande de congé enregistrée", hrService.requestLeave(request, httpRequest));
    }

    @PatchMapping("/leaves/{id}/decide")
    @Operation(summary = "Approuver ou rejeter un congé")
    public ResponseEntity<ApiResponse<LeaveResponse>> decide(
            @PathVariable Long id, @RequestParam LeaveStatus status, HttpServletRequest httpRequest) {
        return ok("Décision enregistrée", hrService.decide(id, status, httpRequest));
    }

    // ---------- Contrats ----------

    @GetMapping("/contracts")
    @Operation(summary = "Liste des contrats")
    public ResponseEntity<ApiResponse<List<ContractResponse>>> contracts(
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) ContractStatus status) {
        return ok("Contrats", hrService.listContracts(teacherId, status));
    }

    @PostMapping("/contracts")
    @Operation(summary = "Créer un contrat")
    public ResponseEntity<ApiResponse<ContractResponse>> createContract(
            @Valid @RequestBody ContractRequest request, HttpServletRequest httpRequest) {
        return ok("Contrat créé", hrService.createContract(request, httpRequest));
    }

    @PutMapping("/contracts/{id}")
    @Operation(summary = "Modifier un contrat")
    public ResponseEntity<ApiResponse<ContractResponse>> updateContract(
            @PathVariable Long id, @Valid @RequestBody ContractRequest request, HttpServletRequest httpRequest) {
        return ok("Contrat modifié", hrService.updateContract(id, request, httpRequest));
    }

    @DeleteMapping("/contracts/{id}")
    @Operation(summary = "Supprimer un contrat")
    public ResponseEntity<ApiResponse<Void>> deleteContract(
            @PathVariable Long id, HttpServletRequest httpRequest) {
        hrService.deleteContract(id, httpRequest);
        return ok("Contrat supprimé", null);
    }

    // ---------- Paie ----------

    @GetMapping("/payrolls")
    @Operation(summary = "Liste des bulletins de paie")
    public ResponseEntity<ApiResponse<List<PayrollResponse>>> payrolls(
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) LocalDate monthDate,
            @RequestParam(required = false) PayrollStatus status) {
        return ok("Bulletins de paie", hrService.listPayrolls(teacherId, monthDate, status));
    }

    @PostMapping("/payrolls/generate")
    @Operation(summary = "Générer un bulletin de paie mensuel")
    public ResponseEntity<ApiResponse<PayrollResponse>> generatePayroll(
            @Valid @RequestBody PayrollGenerateRequest request, HttpServletRequest httpRequest) {
        return ok("Bulletin généré", hrService.generatePayroll(request, httpRequest));
    }

    @PatchMapping("/payrolls/{id}/pay")
    @Operation(summary = "Marquer un bulletin comme payé")
    public ResponseEntity<ApiResponse<PayrollResponse>> markPaid(
            @PathVariable Long id, HttpServletRequest httpRequest) {
        return ok("Salaire marqué comme payé", hrService.markPayrollPaid(id, httpRequest));
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).message(message).data(data)
                .timestamp(LocalDateTime.now()).build());
    }
}