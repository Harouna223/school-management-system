package com.school.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookRequest {

    @NotBlank(message = "Le titre est obligatoire")
    private String title;

    private String author;
    private String isbn;
    private String category;
    private Integer quantity;
    private String publisher;
    private Integer publicationYear;
}