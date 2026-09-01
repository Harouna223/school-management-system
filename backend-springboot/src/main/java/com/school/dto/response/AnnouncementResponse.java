package com.school.dto.response;

import com.school.entity.Announcement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementResponse {

    private Long id;
    private String title;
    private String content;
    private String targetRole;
    private boolean pinned;
    private String createdByName;
    private LocalDateTime createdAt;

    public static AnnouncementResponse from(Announcement a) {
        return AnnouncementResponse.builder()
                .id(a.getId())
                .title(a.getTitle())
                .content(a.getContent())
                .targetRole(a.getTargetRole())
                .pinned(a.isPinned())
                .createdByName(a.getCreatedBy() != null
                        ? a.getCreatedBy().getFirstName() + " " + a.getCreatedBy().getLastName()
                        : "Système")
                .createdAt(a.getCreatedAt())
                .build();
    }
}