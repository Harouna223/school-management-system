package com.school.dto.request;

import com.school.enums.ContractType;
import com.school.enums.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class TeacherRequest {

    @NotBlank(message = "Le prénom est obligatoire")
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    private String lastName;

    private LocalDate birthDate;

    @NotNull(message = "Le genre est obligatoire")
    private Gender gender;

    private String phone;

    @Email(message = "Email invalide")
    private String email;

    private String address;

    @NotNull(message = "La date d'embauche est obligatoire")
    private LocalDate hireDate;

    @NotNull(message = "Le type de contrat est obligatoire")
    private ContractType contractType;

    private BigDecimal salary;

    private Boolean createUserAccount;
    private String username;
    private String password;
}