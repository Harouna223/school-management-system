package com.school.controller;

import com.school.dto.response.ApiResponse;
import com.school.service.BackupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Sauvegarde et restauration de la base de données (réservé au super admin).
 */
@RestController
@RequestMapping("/api/backup")
@RequiredArgsConstructor
@Tag(name = "Sauvegarde", description = "Sauvegarde et restauration de la base de données")
public class BackupController {

    private final BackupService backupService;

    @GetMapping("/export")
    @Operation(summary = "Télécharger une sauvegarde SQL complète de la base")
    public void export(HttpServletResponse response) throws IOException {
        byte[] dump = backupService.exportBackup();
        String filename = "sms-backup-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".sql";
        response.setContentType("application/sql");
        response.setHeader("Content-Disposition", "attachment; filename=\""
                + URLEncoder.encode(filename, StandardCharsets.UTF_8) + "\"");
        response.getOutputStream().write(dump);
    }

    @PostMapping(value = "/restore", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Restaurer la base à partir d'une sauvegarde générée par l'application")
    public ResponseEntity<ApiResponse<Long>> restore(@RequestPart("file") MultipartFile file) {
        int executed = backupService.restoreBackup(file);
        return ResponseEntity.ok(ApiResponse.<Long>builder()
                .success(true)
                .message("Restauration terminée (" + executed + " instructions exécutées)")
                .data((long) executed)
                .timestamp(LocalDateTime.now())
                .build());
    }
}
