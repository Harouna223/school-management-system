package com.school.dto.response;

import com.school.entity.Teacher;
import com.school.enums.ContractType;
import com.school.enums.Gender;
import com.school.enums.TeacherStatus;
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
public class TeacherResponse {

    private Long id;
    private String employeeNo;
    private String firstName;
    private String lastName;
    private LocalDate birthDate;
    private Gender gender;
    private String phone;
    private String email;
    private String address;
    private LocalDate hireDate;
    private ContractType contractType;
    private BigDecimal salary;
    private String photo;
    private TeacherStatus status;

    public static TeacherResponse from(Teacher t) {
        return TeacherResponse.builder()
                .id(t.getId())
                .employeeNo(t.getEmployeeNo())
                .firstName(t.getFirstName())
                .lastName(t.getLastName())
                .birthDate(t.getBirthDate())
                .gender(t.getGender())
                .phone(t.getPhone())
                .email(t.getEmail())
                .address(t.getAddress())
                .hireDate(t.getHireDate())
                .contractType(t.getContractType())
                .salary(t.getSalary())
                .photo(t.getPhoto())
                .status(t.getStatus())
                .build();
    }
}
