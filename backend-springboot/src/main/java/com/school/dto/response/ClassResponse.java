package com.school.dto.response;

import com.school.entity.SchoolClass;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassResponse {

    private Long id;
    private String name;
    private String code;
    private Long levelId;
    private String levelName;
    private Long sectionId;
    private String sectionName;
    private Long roomId;
    private String roomName;
    private Integer capacity;
    private long studentCount;

    public static ClassResponse from(SchoolClass c) {
        return ClassResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .code(c.getCode())
                .levelId(c.getLevel() != null ? c.getLevel().getId() : null)
                .levelName(c.getLevel() != null ? c.getLevel().getName() : null)
                .sectionId(c.getSection() != null ? c.getSection().getId() : null)
                .sectionName(c.getSection() != null ? c.getSection().getName() : null)
                .roomId(c.getRoom() != null ? c.getRoom().getId() : null)
                .roomName(c.getRoom() != null ? c.getRoom().getName() : null)
                .capacity(c.getCapacity())
                .studentCount(c.getStudents() != null ? c.getStudents().size() : 0)
                .build();
    }
}
