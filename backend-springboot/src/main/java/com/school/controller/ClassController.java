package com.school.controller;

import com.school.dto.request.ClassRequest;
import com.school.dto.request.LevelRequest;
import com.school.dto.request.RoomRequest;
import com.school.dto.request.SectionRequest;
import com.school.dto.response.ApiResponse;
import com.school.dto.response.ClassResponse;
import com.school.dto.response.PageResponse;
import com.school.entity.Level;
import com.school.entity.Room;
import com.school.entity.Section;
import com.school.service.ClassService;
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
 * Module classes : niveaux, sections, salles.
 */
@RestController
@RequestMapping("/api/classes")
@RequiredArgsConstructor
@Tag(name = "Classes", description = "Gestion des classes, niveaux, sections et salles")
public class ClassController {

    private final ClassService classService;

    @GetMapping
    @Operation(summary = "Rechercher des classes")
    public ResponseEntity<ApiResponse<PageResponse<ClassResponse>>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long levelId,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ok("Recherche réussie", classService.search(search, levelId, sectionId, page, size));
    }

    @GetMapping("/all")
    @Operation(summary = "Toutes les classes (liste simple)")
    public ResponseEntity<ApiResponse<List<ClassResponse>>> findAll() {
        return ok("Liste des classes", classService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ClassResponse>> getById(@PathVariable Long id) {
        return ok("Classe trouvée", classService.getById(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ClassResponse>> create(@Valid @RequestBody ClassRequest request,
                                                             HttpServletRequest httpRequest) {
        return ok("Classe créée", classService.create(request, httpRequest));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ClassResponse>> update(@PathVariable Long id,
                                                             @Valid @RequestBody ClassRequest request,
                                                             HttpServletRequest httpRequest) {
        return ok("Classe modifiée", classService.update(id, request, httpRequest));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        classService.delete(id, httpRequest);
        return ok("Classe supprimée", null);
    }

    @GetMapping("/levels")
    @Operation(summary = "Liste des niveaux")
    public ResponseEntity<ApiResponse<List<Level>>> levels() {
        return ok("Niveaux", classService.listLevels());
    }

    @GetMapping("/sections")
    @Operation(summary = "Liste des sections")
    public ResponseEntity<ApiResponse<List<Section>>> sections() {
        return ok("Sections", classService.listSections());
    }

    @GetMapping("/rooms")
    @Operation(summary = "Liste des salles")
    public ResponseEntity<ApiResponse<List<Room>>> rooms() {
        return ok("Salles", classService.listRooms());
    }

    // ---------- CRUD Niveaux ----------

    @PostMapping("/levels")
    public ResponseEntity<ApiResponse<Level>> createLevel(@Valid @RequestBody LevelRequest request,
                                                          HttpServletRequest httpRequest) {
        return ok("Niveau créé", classService.createLevel(request, httpRequest));
    }

    @PutMapping("/levels/{id}")
    public ResponseEntity<ApiResponse<Level>> updateLevel(@PathVariable Long id,
                                                          @Valid @RequestBody LevelRequest request,
                                                          HttpServletRequest httpRequest) {
        return ok("Niveau modifié", classService.updateLevel(id, request, httpRequest));
    }

    @DeleteMapping("/levels/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteLevel(@PathVariable Long id,
                                                         HttpServletRequest httpRequest) {
        classService.deleteLevel(id, httpRequest);
        return ok("Niveau supprimé", null);
    }

    // ---------- CRUD Sections ----------

    @PostMapping("/sections")
    public ResponseEntity<ApiResponse<Section>> createSection(@Valid @RequestBody SectionRequest request,
                                                              HttpServletRequest httpRequest) {
        return ok("Section créée", classService.createSection(request, httpRequest));
    }

    @PutMapping("/sections/{id}")
    public ResponseEntity<ApiResponse<Section>> updateSection(@PathVariable Long id,
                                                              @Valid @RequestBody SectionRequest request,
                                                              HttpServletRequest httpRequest) {
        return ok("Section modifiée", classService.updateSection(id, request, httpRequest));
    }

    @DeleteMapping("/sections/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSection(@PathVariable Long id,
                                                           HttpServletRequest httpRequest) {
        classService.deleteSection(id, httpRequest);
        return ok("Section supprimée", null);
    }

    // ---------- CRUD Salles ----------

    @PostMapping("/rooms")
    public ResponseEntity<ApiResponse<Room>> createRoom(@Valid @RequestBody RoomRequest request,
                                                        HttpServletRequest httpRequest) {
        return ok("Salle créée", classService.createRoom(request, httpRequest));
    }

    @PutMapping("/rooms/{id}")
    public ResponseEntity<ApiResponse<Room>> updateRoom(@PathVariable Long id,
                                                        @Valid @RequestBody RoomRequest request,
                                                        HttpServletRequest httpRequest) {
        return ok("Salle modifiée", classService.updateRoom(id, request, httpRequest));
    }

    @DeleteMapping("/rooms/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRoom(@PathVariable Long id,
                                                        HttpServletRequest httpRequest) {
        classService.deleteRoom(id, httpRequest);
        return ok("Salle supprimée", null);
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).message(message).data(data)
                .timestamp(LocalDateTime.now()).build());
    }
}