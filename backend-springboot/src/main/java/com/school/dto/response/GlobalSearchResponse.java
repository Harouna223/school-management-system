package com.school.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Résultat de la recherche globale multi-modules.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlobalSearchResponse {

    @Builder.Default
    private List<StudentSearchHit> students = List.of();

    @Builder.Default
    private List<TeacherSearchHit> teachers = List.of();

    @Builder.Default
    private List<ClassSearchHit> classes = List.of();

    @Builder.Default
    private List<InvoiceSearchHit> invoices = List.of();

    @Builder.Default
    private List<UniversityHit> faculties = List.of();

    @Builder.Default
    private List<UniversityHit> departments = List.of();

    @Builder.Default
    private List<UniversityHit> fields = List.of();

    @Builder.Default
    private List<UniversityHit> programs = List.of();

    @Builder.Default
    private List<UniversityHit> ues = List.of();

    @Builder.Default
    private List<UniversityHit> ecs = List.of();

    @Builder.Default
    private List<UniversityHit> convocations = List.of();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UniversityHit {
        private Long id;
        private String name;
        private String code;
        private String subInfo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudentSearchHit {
        private Long id;
        private String fullName;
        private String matricule;
        private String className;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeacherSearchHit {
        private Long id;
        private String fullName;
        private String employeeNo;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClassSearchHit {
        private Long id;
        private String name;
        private String code;
        private String levelName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvoiceSearchHit {
        private Long id;
        private String invoiceNo;
        private String studentName;
        private String feeTypeName;
        private String status;
    }
}
