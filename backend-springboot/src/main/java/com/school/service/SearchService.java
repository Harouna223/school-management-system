package com.school.service;

import com.school.dto.response.GlobalSearchResponse;
import com.school.entity.*;
import com.school.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Recherche globale multi-modules : élèves, enseignants, classes, factures
 * et entités universitaires (facultés, départements, filières, programmes, UE, EC).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SearchService {

    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final SchoolClassRepository classRepository;
    private final InvoiceRepository invoiceRepository;
    private final FacultyRepository facultyRepository;
    private final DepartmentRepository departmentRepository;
    private final AcademicFieldRepository fieldRepository;
    private final ProgramRepository programRepository;
    private final UniversityUnitRepository ueRepository;
    private final CourseUnitRepository ecRepository;
    private final ConvocationRepository convocationRepository;

    public GlobalSearchResponse search(String q) {
        if (q == null || q.isBlank()) {
            return GlobalSearchResponse.builder().build();
        }
        String keyword = q.trim();

        List<Student> students = studentRepository
                .search(keyword, null, null, null, PageRequest.of(0, 8)).getContent();
        List<Teacher> teachers = teacherRepository
                .search(keyword, null, PageRequest.of(0, 8)).getContent();
        List<SchoolClass> classes = classRepository
                .search(keyword, null, null, PageRequest.of(0, 8)).getContent();
        List<Invoice> invoices = invoiceRepository
                .findTop8ByKeyword(keyword, PageRequest.of(0, 8));

        List<Faculty> faculties = facultyRepository
                .findTop8ByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(keyword, keyword);
        List<Department> departments = departmentRepository
                .findTop8ByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(keyword, keyword);
        List<AcademicField> fields = fieldRepository
                .findTop8ByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(keyword, keyword);
        List<Program> programs = programRepository
                .findTop8ByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(keyword, keyword);
        List<UniversityUnit> ues = ueRepository
                .findTop8ByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(keyword, keyword);
        List<CourseUnit> ecs = ecRepository
                .findTop8ByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(keyword, keyword);
        List<Convocation> convocations = convocationRepository
                .findTop8BySubjectContainingIgnoreCaseOrReferenceContainingIgnoreCase(keyword, keyword);

        return GlobalSearchResponse.builder()
                .students(students.stream().map(s -> GlobalSearchResponse.StudentSearchHit.builder()
                        .id(s.getId())
                        .fullName(s.getFirstName() + " " + s.getLastName())
                        .matricule(s.getMatricule())
                        .className(s.getSchoolClass() != null ? s.getSchoolClass().getName() : null)
                        .status(s.getStatus() != null ? s.getStatus().name() : null)
                        .build()).toList())
                .teachers(teachers.stream().map(t -> GlobalSearchResponse.TeacherSearchHit.builder()
                        .id(t.getId())
                        .fullName(t.getFirstName() + " " + t.getLastName())
                        .employeeNo(t.getEmployeeNo())
                        .status(t.getStatus() != null ? t.getStatus().name() : null)
                        .build()).toList())
                .classes(classes.stream().map(c -> GlobalSearchResponse.ClassSearchHit.builder()
                        .id(c.getId())
                        .name(c.getName())
                        .code(c.getCode())
                        .levelName(c.getLevel() != null ? c.getLevel().getName() : null)
                        .build()).toList())
                .invoices(invoices.stream().map(i -> GlobalSearchResponse.InvoiceSearchHit.builder()
                        .id(i.getId())
                        .invoiceNo(i.getInvoiceNo())
                        .studentName(i.getStudent() != null
                                ? i.getStudent().getFirstName() + " " + i.getStudent().getLastName() : null)
                        .feeTypeName(i.getFeeType() != null ? i.getFeeType().getName() : null)
                        .status(i.getStatus() != null ? i.getStatus().name() : null)
                        .build()).toList())
                .faculties(faculties.stream().map(f -> GlobalSearchResponse.UniversityHit.builder()
                        .id(f.getId()).name(f.getName()).code(f.getCode()).subInfo("Faculté / UFR").build()).toList())
                .departments(departments.stream().map(d -> GlobalSearchResponse.UniversityHit.builder()
                        .id(d.getId()).name(d.getName()).code(d.getCode())
                        .subInfo(d.getFaculty() != null ? d.getFaculty().getName() : "Département").build()).toList())
                .fields(fields.stream().map(f -> GlobalSearchResponse.UniversityHit.builder()
                        .id(f.getId()).name(f.getName()).code(f.getCode())
                        .subInfo(f.getDepartment() != null ? f.getDepartment().getName() : "Filière").build()).toList())
                .programs(programs.stream().map(p -> GlobalSearchResponse.UniversityHit.builder()
                        .id(p.getId()).name(p.getName()).code(p.getCode())
                        .subInfo(p.getDiploma() != null ? p.getDiploma() : "Programme").build()).toList())
                .ues(ues.stream().map(u -> GlobalSearchResponse.UniversityHit.builder()
                        .id(u.getId()).name(u.getName()).code(u.getCode())
                        .subInfo("UE • " + u.getSemester()).build()).toList())
                .ecs(ecs.stream().map(e -> GlobalSearchResponse.UniversityHit.builder()
                        .id(e.getId()).name(e.getName()).code(e.getCode())
                        .subInfo(e.getUe() != null ? "EC • " + e.getUe().getCode() : "EC").build()).toList())
                .convocations(convocations.stream().map(c -> GlobalSearchResponse.UniversityHit.builder()
                        .id(c.getId()).name(c.getSubject()).code(c.getReference())
                        .subInfo("Convocation").build()).toList())
                .build();
    }
}
