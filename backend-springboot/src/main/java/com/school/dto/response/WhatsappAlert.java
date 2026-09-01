package com.school.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Alerte WhatsApp à ouvrir dans le navigateur via wa.me.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsappAlert {
    private String phone;
    private String message;
    private String waLink;
}