package com.school.dto.response;

import com.school.entity.Expense;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseResponse {

    private Long id;
    private String description;
    private BigDecimal amount;
    private Long categoryId;
    private String categoryName;
    private LocalDate expenseDate;
    private String paidByName;

    public static ExpenseResponse from(Expense e) {
        return ExpenseResponse.builder()
                .id(e.getId())
                .description(e.getDescription())
                .amount(e.getAmount())
                .categoryId(e.getCategory().getId())
                .categoryName(e.getCategory().getName())
                .expenseDate(e.getExpenseDate())
                .paidByName(e.getPaidBy() != null
                        ? e.getPaidBy().getFirstName() + " " + e.getPaidBy().getLastName() : null)
                .build();
    }
}