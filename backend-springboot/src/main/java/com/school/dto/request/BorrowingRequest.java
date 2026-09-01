package com.school.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BorrowingRequest {

    @NotNull(message = "Le livre est obligatoire")
    private Long bookId;

    @NotNull(message = "L'élève est obligatoire")
    private Long studentId;

    @NotNull(message = "La date d'emprunt est obligatoire")
    private LocalDate borrowDate;

    @NotNull(message = "La date de retour est obligatoire")
    private LocalDate dueDate;
}