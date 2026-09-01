package com.school.controller;

import com.school.dto.response.ApiResponse;
import com.school.dto.response.GlobalSearchResponse;
import com.school.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Recherche globale multi-modules (élèves, enseignants, classes, factures).
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Tag(name = "Recherche globale", description = "Recherche multi-modules")
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    @Operation(summary = "Rechercher dans tous les modules")
    public ResponseEntity<ApiResponse<GlobalSearchResponse>> search(@RequestParam String q) {
        return ResponseEntity.ok(ApiResponse.<GlobalSearchResponse>builder()
                .success(true).message("Résultats").data(searchService.search(q))
                .timestamp(LocalDateTime.now()).build());
    }
}
