package com.school.controller;

import com.school.dto.response.ApiResponse;
import com.school.dto.response.SettingResponse;
import com.school.service.FileStorageService;
import com.school.service.SettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Paramètres de l'établissement (identité, reçus, WhatsApp).
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
@Tag(name = "Paramètres", description = "Configuration de l'établissement")
public class SettingController {

    private final SettingService settingService;
    private final FileStorageService fileStorageService;

    @GetMapping
    @Operation(summary = "Lister les paramètres")
    public ResponseEntity<ApiResponse<List<SettingResponse>>> all() {
        return ok("Paramètres", settingService.all());
    }

    @GetMapping("/defaults")
    @Operation(summary = "Valeurs par défaut issues de la configuration")
    public ResponseEntity<ApiResponse<Map<String, String>>> defaults() {
        return ok("Valeurs par défaut", settingService.defaults());
    }

    @PutMapping
    @Operation(summary = "Mettre à jour les paramètres (création si absents)")
    public ResponseEntity<ApiResponse<List<SettingResponse>>> update(
            @RequestBody Map<String, String> values, HttpServletRequest httpRequest) {
        return ok("Paramètres mis à jour", settingService.updateAll(values, httpRequest));
    }

    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Uploader le logo de l'établissement")
    public ResponseEntity<ApiResponse<String>> uploadLogo(@RequestPart("file") MultipartFile file,
                                                          HttpServletRequest httpRequest) {
        String url = fileStorageService.store(file, "school");
        settingService.updateAll(Map.of(SettingService.SCHOOL_LOGO, url), httpRequest);
        return ok("Logo mis à jour", url);
    }

    @GetMapping("/cycles")
    @Operation(summary = "Cycles d'enseignement activés (Jardin, Primaire, Collège, Lycée, Université)")
    public ResponseEntity<ApiResponse<List<String>>> cycles() {
        return ok("Cycles actifs", settingService.getActiveCycles());
    }

    @PutMapping("/cycles")
    @Operation(summary = "Activer / désactiver les cycles d'enseignement")
    public ResponseEntity<ApiResponse<List<String>>> updateCycles(
            @RequestBody List<String> cycles, HttpServletRequest httpRequest) {
        return ok("Cycles mis à jour", settingService.updateActiveCycles(cycles, httpRequest));
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).message(message).data(data)
                .timestamp(LocalDateTime.now()).build());
    }
}