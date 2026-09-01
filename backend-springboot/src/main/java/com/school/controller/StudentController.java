package com.school.controller;

import com.school.dto.request.StudentRequest;
import com.school.dto.response.ApiResponse;
import com.school.dto.response.PageResponse;
import com.school.dto.response.StudentHistoryResponse;
import com.school.dto.response.StudentImportResult;
import com.school.dto.response.StudentResponse;
import com.school.enums.StudentStatus;
import com.school.service.AccessControlService;
import com.school.service.QrCodeService;
import com.school.service.ReportService;
import com.school.service.StudentImportService;
import com.school.service.StudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Module élèves : CRUD, recherche, pagination, photos, export PDF/Excel, import Excel, QR code.
 */
@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
@Tag(name = "Élèves", description = "Gestion des élèves et dossiers scolaires")
public class StudentController {

    private final StudentService studentService;
    private final ReportService reportService;
    private final QrCodeService qrCodeService;
    private final StudentImportService studentImportService;
    private final AccessControlService accessControlService;

    @GetMapping
    @Operation(summary = "Rechercher des élèves", description = "Recherche avancée avec pagination (nom, matricule, classe, statut, cycle)")
    public ResponseEntity<ApiResponse<PageResponse<StudentResponse>>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) StudentStatus status,
            @RequestParam(required = false) com.school.enums.EducationCycle cycle,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ok("Recherche réussie", studentService.search(search, classId, status, cycle, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détails d'un élève")
    public ResponseEntity<ApiResponse<StudentResponse>> getById(@PathVariable Long id) {
        accessControlService.assertCanAccessStudent(id);
        return ok("Élève trouvé", studentService.getById(id));
    }

    @PostMapping
    @Operation(summary = "Inscrire un élève", description = "Génère le matricule automatiquement et crée le parent si nécessaire")
    public ResponseEntity<ApiResponse<StudentResponse>> create(@Valid @RequestBody StudentRequest request,
                                                               HttpServletRequest httpRequest) {
        return ok("Élève inscrit avec succès", studentService.create(request, httpRequest));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un élève")
    public ResponseEntity<ApiResponse<StudentResponse>> update(@PathVariable Long id,
                                                               @Valid @RequestBody StudentRequest request,
                                                               HttpServletRequest httpRequest) {
        return ok("Élève modifié avec succès", studentService.update(id, request, httpRequest));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un élève")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        studentService.delete(id, httpRequest);
        return ok("Élève supprimé", null);
    }

    // ------------------------------------------------------------------
    // Parcours scolaire
    // ------------------------------------------------------------------

    @PatchMapping("/{id}/transfer")
    @Operation(summary = "Transférer un élève vers une autre classe")
    public ResponseEntity<ApiResponse<StudentResponse>> transfer(@PathVariable Long id,
                                                                  @RequestParam Long newClassId,
                                                                  @RequestParam(required = false) String reason,
                                                                  HttpServletRequest httpRequest) {
        return ok("Élève transféré", studentService.transfer(id, newClassId, reason, httpRequest));
    }

    @PatchMapping("/{id}/radiate")
    @Operation(summary = "Radier un élève (quitte l'établissement)")
    public ResponseEntity<ApiResponse<StudentResponse>> radiate(@PathVariable Long id,
                                                                 @RequestParam(required = false) String reason,
                                                                 HttpServletRequest httpRequest) {
        return ok("Élève radié", studentService.radiate(id, reason, httpRequest));
    }

    @PatchMapping("/{id}/reinscribe")
    @Operation(summary = "Réinscrire un élève radié ou inactif")
    public ResponseEntity<ApiResponse<StudentResponse>> reinscribe(@PathVariable Long id,
                                                                   @RequestParam(required = false) Long classId,
                                                                   @RequestParam(required = false) String reason,
                                                                   HttpServletRequest httpRequest) {
        return ok("Élève réinscrit", studentService.reinscribe(id, classId, reason, httpRequest));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Historique du parcours (transferts, radiations, réinscriptions)")
    public ResponseEntity<ApiResponse<List<StudentHistoryResponse>>> history(@PathVariable Long id) {
        accessControlService.assertCanAccessStudent(id);
        return ok("Historique", studentService.historyOf(id));
    }

    @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Uploader la photo d'un élève")
    public ResponseEntity<ApiResponse<StudentResponse>> uploadPhoto(@PathVariable Long id,
                                                                    @RequestPart("file") MultipartFile file) {
        return ok("Photo enregistrée", studentService.uploadPhoto(id, file));
    }

    @GetMapping("/export/pdf")
    @Operation(summary = "Exporter les élèves en PDF")
    public void exportPdf(HttpServletResponse response,
                          @RequestParam(required = false) String search,
                          @RequestParam(required = false) Long classId) throws IOException {
        List<StudentResponse> students = studentService.search(search, classId, null, null, 0, 10000)
                .getContent();
        List<String[]> rows = students.stream()
                .map(s -> new String[]{s.getMatricule(), s.getLastName(), s.getFirstName(),
                        s.getGender() != null ? s.getGender().name() : "", s.getClassName(),
                        s.getStatus() != null ? s.getStatus().name() : "",
                        s.getPhone() != null ? s.getPhone() : ""})
                .toList();
        reportService.exportStudentsPdf(response, rows,
                new String[]{"Matricule", "Nom", "Prénom", "Genre", "Classe", "Statut", "Téléphone"},
                "liste-eleves");
    }

    @GetMapping("/export/excel")
    @Operation(summary = "Exporter les élèves en Excel")
    public void exportExcel(HttpServletResponse response,
                            @RequestParam(required = false) String search,
                            @RequestParam(required = false) Long classId) throws IOException {
        List<StudentResponse> students = studentService.search(search, classId, null, null, 0, 10000)
                .getContent();
        List<String[]> rows = students.stream()
                .map(s -> new String[]{s.getMatricule(), s.getLastName(), s.getFirstName(),
                        s.getGender() != null ? s.getGender().name() : "", s.getClassName(),
                        s.getStatus() != null ? s.getStatus().name() : "",
                        s.getPhone() != null ? s.getPhone() : ""})
                .toList();
        reportService.exportExcel(response,
                new String[]{"Matricule", "Nom", "Prénom", "Genre", "Classe", "Statut", "Téléphone"},
                rows, "liste-eleves");
    }

    @GetMapping("/import/template")
    @Operation(summary = "Modèle Excel d'import des élèves")
    public void importTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"modele-import-eleves.xlsx\"");
        response.getOutputStream().write(studentImportService.template());
    }

    @PostMapping(value = "/import/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importer des élèves depuis un fichier Excel",
            description = "Valide chaque ligne, détecte les doublons, importe les lignes valides et retourne un rapport")
    public ResponseEntity<ApiResponse<StudentImportResult>> importExcel(
            @RequestPart("file") MultipartFile file, HttpServletRequest httpRequest) throws IOException {
        return ok("Import terminé", studentImportService.importFromExcel(file, httpRequest));
    }

    @GetMapping("/{id}/qr")
    @Operation(summary = "QR Code de la carte scolaire d'un élève")
    public void studentQr(@PathVariable Long id, HttpServletResponse response) throws IOException {
        accessControlService.assertCanAccessStudent(id);
        StudentResponse student = studentService.getById(id);
        qrCodeService.generateStudentQr(response, student.getMatricule(),
                student.getFirstName() + " " + student.getLastName(), student.getClassName());
    }

    @GetMapping("/{id}/certificate")
    @Operation(summary = "Certificat de scolarité PDF d'un élève")
    public void certificate(@PathVariable Long id, HttpServletResponse response) throws IOException {
        accessControlService.assertCanAccessStudent(id);
        reportService.schoolCertificatePdf(response, studentService.getById(id));
    }

    @GetMapping("/{id}/card")
    @Operation(summary = "Carte d'identité scolaire PDF (format ID-1) d'un élève")
    public void card(@PathVariable Long id, HttpServletResponse response) throws IOException {
        accessControlService.assertCanAccessStudent(id);
        reportService.schoolIdCardPdf(response, studentService.getById(id));
    }

    @GetMapping("/{id}/attendance-certificate")
    @Operation(summary = "Certificat de fréquentation PDF d'un élève")
    public void attendanceCertificate(@PathVariable Long id, HttpServletResponse response) throws IOException {
        accessControlService.assertCanAccessStudent(id);
        reportService.attendanceCertificatePdf(response, studentService.getById(id));
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).message(message).data(data)
                .timestamp(LocalDateTime.now()).build());
    }
}