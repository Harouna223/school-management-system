package com.school.dto.response;

import com.school.entity.Parent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParentResponse {

    private Long id;
    private String firstName;
    private String lastName;
    private String phone;
    private String email;
    private String profession;
    private String address;
    private boolean hasAccount;

    /**
     * Mot de passe provisoirement généré par le serveur pour le compte parent
     * (uniquement si aucun mot de passe parent n'a été fourni). À transmettre au
     * parent une seule fois ; jamais renvoyé pour un compte existant.
     */
    private String generatedPassword;

    /**
     * Nom d'utilisateur effectif du compte parent créé (peut différer de celui
     * demandé si un suffixe d'unicité a été ajouté).
     */
    private String accountUsername;

    public static ParentResponse from(Parent p) {
        return ParentResponse.builder()
                .id(p.getId())
                .firstName(p.getFirstName())
                .lastName(p.getLastName())
                .phone(p.getPhone())
                .email(p.getEmail())
                .profession(p.getProfession())
                .address(p.getAddress())
                .hasAccount(p.getUser() != null)
                .build();
    }
}
