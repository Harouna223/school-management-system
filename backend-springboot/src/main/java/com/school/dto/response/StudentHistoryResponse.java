package com.school.dto.response;

import com.school.entity.StudentHistory;
import com.school.enums.StudentHistoryAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentHistoryResponse {

    private Long id;
    private StudentHistoryAction action;
    private String fromClass;
    private String toClass;
    private String reason;
    private String recordedBy;
    private LocalDateTime createdAt;

    public static StudentHistoryResponse from(StudentHistory h) {
        return StudentHistoryResponse.builder()
                .id(h.getId())
                .action(h.getAction())
                .fromClass(h.getFromClass())
                .toClass(h.getToClass())
                .reason(h.getReason())
                .recordedBy(h.getRecordedBy())
                .createdAt(h.getCreatedAt())
                .build();
    }
}