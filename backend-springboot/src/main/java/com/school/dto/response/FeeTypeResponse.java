package com.school.dto.response;

import com.school.entity.FeeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeTypeResponse {

    private Long id;
    private String name;
    private BigDecimal amount;
    private String description;

    public static FeeTypeResponse from(FeeType f) {
        return FeeTypeResponse.builder()
                .id(f.getId())
                .name(f.getName())
                .amount(f.getAmount())
                .description(f.getDescription())
                .build();
    }
}