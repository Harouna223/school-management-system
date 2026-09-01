package com.school.dto.response;

import com.school.entity.Setting;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingResponse {

    private String key;
    private String value;
    private String description;
    private LocalDateTime updatedAt;

    public static SettingResponse from(Setting s) {
        return SettingResponse.builder()
                .key(s.getKey())
                .value(s.getValue())
                .description(s.getDescription())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}