package com.school.controller;

import com.school.dto.request.BookRequest;
import com.school.dto.request.BorrowingRequest;
import com.school.dto.response.ApiResponse;
import com.school.dto.response.BookResponse;
import com.school.dto.response.PageResponse;
import com.school.enums.BorrowingStatus;
import com.school.service.LibraryService;
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
 * Module bibliothèque : livres, emprunts, retours.
 */
@RestController
@RequestMapping("/api/library")
@RequiredArgsConstructor
@Tag(name = "Bibliothèque", description = "Gestion des livres et emprunts")
public class LibraryController {

    private final LibraryService libraryService;

    @GetMapping("/books")
    @Operation(summary = "Rechercher des livres")
    public ResponseEntity<ApiResponse<PageResponse<BookResponse>>> searchBooks(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ok("Livres", libraryService.searchBooks(search, category, page, size));
    }

    @PostMapping("/books")
    public ResponseEntity<ApiResponse<BookResponse>> createBook(
            @Valid @RequestBody BookRequest request, HttpServletRequest httpRequest) {
        return ok("Livre ajouté", libraryService.createBook(request, httpRequest));
    }

    @PutMapping("/books/{id}")
    public ResponseEntity<ApiResponse<BookResponse>> updateBook(@PathVariable Long id,
                                                                @Valid @RequestBody BookRequest request,
                                                                HttpServletRequest httpRequest) {
        return ok("Livre modifié", libraryService.updateBook(id, request, httpRequest));
    }

    @DeleteMapping("/books/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteBook(@PathVariable Long id,
                                                        HttpServletRequest httpRequest) {
        libraryService.deleteBook(id, httpRequest);
        return ok("Livre supprimé", null);
    }

    @GetMapping("/borrowings")
    @Operation(summary = "Liste des emprunts")
    public ResponseEntity<ApiResponse<PageResponse<BookResponse.BorrowingResponse>>> borrowings(
            @RequestParam(required = false) BorrowingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ok("Emprunts", libraryService.searchBorrowings(status, page, size));
    }

    @GetMapping("/borrowings/student/{studentId}")
    public ResponseEntity<ApiResponse<List<BookResponse.BorrowingResponse>>> borrowingsByStudent(
            @PathVariable Long studentId) {
        return ok("Emprunts de l'élève", libraryService.borrowingsOfStudent(studentId));
    }

    @PostMapping("/borrowings")
    @Operation(summary = "Enregistrer un emprunt")
    public ResponseEntity<ApiResponse<BookResponse.BorrowingResponse>> borrow(
            @Valid @RequestBody BorrowingRequest request, HttpServletRequest httpRequest) {
        return ok("Emprunt enregistré", libraryService.borrow(request, httpRequest));
    }

    @PatchMapping("/borrowings/{id}/return")
    @Operation(summary = "Enregistrer un retour")
    public ResponseEntity<ApiResponse<BookResponse.BorrowingResponse>> returnBook(
            @PathVariable Long id, HttpServletRequest httpRequest) {
        return ok("Retour enregistré", libraryService.returnBook(id, httpRequest));
    }

    @PostMapping("/borrowings/mark-overdue")
    @Operation(summary = "Marquer les emprunts en retard")
    public ResponseEntity<ApiResponse<Integer>> markOverdue() {
        return ok("Emprunts en retard marqués", libraryService.markOverdue());
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).message(message).data(data)
                .timestamp(LocalDateTime.now()).build());
    }
}