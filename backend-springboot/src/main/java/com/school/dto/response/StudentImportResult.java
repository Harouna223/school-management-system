package com.school.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Rapport d'import Excel d'élèves.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentImportResult {

    private int totalRows;
    private int imported;
    private int skipped;
    private List<RowError> errors;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RowError {
        private int row;
        private String message;
    }
}