package com.school.dto.response;

import com.school.entity.Student;
import com.school.enums.EducationCycle;
import com.school.enums.Gender;
import com.school.enums.StudentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Élève avec ses références de classe et de parent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentResponse {

    private Long id;
    private String matricule;
    private String firstName;
    private String lastName;
    private LocalDate birthDate;
    private String birthPlace;
    private Gender gender;
    private String address;
    private String phone;
    private String email;
    private String photo;
    private LocalDate enrollmentDate;
    private StudentStatus status;
    private EducationCycle educationCycle;
    private Long classId;
    private String className;
    private ParentResponse parent;

    public static StudentResponse from(Student s) {
        StudentResponse.StudentResponseBuilder b = StudentResponse.builder()
                .id(s.getId())
                .matricule(s.getMatricule())
                .firstName(s.getFirstName())
                .lastName(s.getLastName())
                .birthDate(s.getBirthDate())
                .birthPlace(s.getBirthPlace())
                .gender(s.getGender())
                .address(s.getAddress())
                .phone(s.getPhone())
                .email(s.getEmail())
                .photo(s.getPhoto())
                .enrollmentDate(s.getEnrollmentDate())
                .status(s.getStatus())
                .educationCycle(s.getEducationCycle())
                .classId(s.getSchoolClass() != null ? s.getSchoolClass().getId() : null)
                .className(s.getSchoolClass() != null ? s.getSchoolClass().getName() : null);
        if (s.getParent() != null) {
            b.parent(ParentResponse.from(s.getParent()));
        }
        return b.build();
    }
}
