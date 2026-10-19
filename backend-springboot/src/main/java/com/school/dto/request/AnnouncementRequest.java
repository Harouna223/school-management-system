package com.school.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementRequest {

    @NotBlank(message = "Le titre est obligatoire")
    @Size(max = 200, message = "Le titre ne peut dépasser 200 caractères")
    private String title;

    @NotBlank(message = "Le contenu est obligatoire")
    @Size(max = 5000, message = "Le contenu ne peut dépasser 5000 caractères")
    private String content;

    private String targetRole;

    private boolean pinned;
}