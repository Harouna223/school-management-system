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
