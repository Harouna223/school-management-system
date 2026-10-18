package com.school.dto.response;

import com.school.entity.CourseResource;
import com.school.entity.SchoolClass;
import com.school.enums.CourseResourceType;
import com.school.enums.CourseStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Vue d'une ressource pédagogique envoyée au frontend.
 *
 * <p>Aucune URL de fichier n'est exposée ici : les endpoints de lecture
 * exigent un jeton de lecture à durée limitée, délivré par
 * {@code GET /api/courses/{id}/token}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseResourceResponse {

    private Long id;
    private String title;
    private String description;
    private String chapter;
    private CourseResourceType type;
    private CourseStatus status;
    private boolean downloadable;

    private String originalFilename;
    private Long sizeBytes;
    private String mimeType;
    private boolean hasFile;

    private Long subjectId;
    private String subjectName;

    private List<Long> classIds;
    private List<String> classNames;

    private Long authorId;
    private String authorName;

    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CourseResourceResponse from(CourseResource resource) {
        List<SchoolClass> classes = resource.getTargetClasses().stream()
                .sorted(Comparator.comparing(SchoolClass::getName))
                .toList();
        return CourseResourceResponse.builder()
                .id(resource.getId())
                .title(resource.getTitle())
                .description(resource.getDescription())
                .chapter(resource.getChapter())
                .type(resource.getType())
                .status(resource.getStatus())
                .downloadable(resource.isDownloadable())
                .originalFilename(resource.getOriginalFilename())
                .sizeBytes(resource.getSizeBytes())
                .mimeType(resource.getMimeType())
                .hasFile(resource.hasFile())
                .subjectId(resource.getSubject() != null ? resource.getSubject().getId() : null)
                .subjectName(resource.getSubject() != null ? resource.getSubject().getName() : null)
                .classIds(classes.stream().map(SchoolClass::getId).toList())
                .classNames(classes.stream().map(SchoolClass::getName).toList())
                .authorId(resource.getAuthor() != null ? resource.getAuthor().getId() : null)
                .authorName(resource.getAuthor() != null
                        ? (resource.getAuthor().getFirstName() + " " + resource.getAuthor().getLastName()).trim()
                        : null)
                .publishedAt(resource.getPublishedAt())
                .createdAt(resource.getCreatedAt())
                .updatedAt(resource.getUpdatedAt())
                .build();
    }
}
