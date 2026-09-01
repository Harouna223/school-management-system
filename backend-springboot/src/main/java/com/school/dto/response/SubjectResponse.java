package com.school.dto.response;

import com.school.entity.Subject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubjectResponse {

    private Long id;
    private String name;
    private String code;
    private Integer coefficient;
    private String description;

    public static SubjectResponse from(Subject s) {
        return SubjectResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .code(s.getCode())
                .coefficient(s.getCoefficient())
                .description(s.getDescription())
                .build();
    }
}