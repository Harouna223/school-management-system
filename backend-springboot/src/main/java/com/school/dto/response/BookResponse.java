package com.school.dto.response;

import com.school.entity.ExpenseCategory;
import com.school.entity.Book;
import com.school.entity.Borrowing;
import com.school.enums.BorrowingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookResponse {

    private Long id;
    private String title;
    private String author;
    private String isbn;
    private String category;
    private Integer quantity;
    private Integer available;
    private String publisher;
    private Integer publicationYear;

    public static BookResponse from(Book b) {
        return BookResponse.builder()
                .id(b.getId())
                .title(b.getTitle())
                .author(b.getAuthor())
                .isbn(b.getIsbn())
                .category(b.getCategory())
                .quantity(b.getQuantity())
                .available(b.getAvailable())
                .publisher(b.getPublisher())
                .publicationYear(b.getPublicationYear())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpenseCategoryResponse {
        private Long id;
        private String name;
        private String description;

        public static ExpenseCategoryResponse from(ExpenseCategory c) {
            return ExpenseCategoryResponse.builder()
                    .id(c.getId())
                    .name(c.getName())
                    .description(c.getDescription())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BorrowingResponse {
        private Long id;
        private Long bookId;
        private String bookTitle;
        private Long studentId;
        private String studentName;
        private String matricule;
        private LocalDate borrowDate;
        private LocalDate dueDate;
        private LocalDate returnDate;
        private BorrowingStatus status;

        public static BorrowingResponse from(Borrowing b) {
            return BorrowingResponse.builder()
                    .id(b.getId())
                    .bookId(b.getBook().getId())
                    .bookTitle(b.getBook().getTitle())
                    .studentId(b.getStudent().getId())
                    .studentName(b.getStudent().getFullName())
                    .matricule(b.getStudent().getMatricule())
                    .borrowDate(b.getBorrowDate())
                    .dueDate(b.getDueDate())
                    .returnDate(b.getReturnDate())
                    .status(b.getStatus())
                    .build();
        }
    }
}