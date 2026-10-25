package com.school.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Réponse à un message existant : seul le contenu est fourni. L'objet est
 * dérivé (« Re: … ») et le destinataire est l'autre participant du message.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageReplyRequest {

    @NotBlank(message = "Le contenu du message est obligatoire")
    @Size(max = 5000, message = "Le message ne peut dépasser 5000 caractères")
    private String content;
}
