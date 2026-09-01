package com.school.service;

import com.school.dto.response.AcademicTimelineEntry;
import com.school.dto.response.AttendanceResponse;
import com.school.dto.response.BulletinResponse;
import com.school.dto.response.GradeResponse;
import com.school.dto.response.StudentResponse;
import com.school.entity.EnrollmentHistory;
import com.school.entity.LmdEnrollment;
import com.school.entity.Parent;
import com.school.entity.Student;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.AttendanceRepository;
import com.school.repository.BulletinRepository;
import com.school.repository.EnrollmentHistoryRepository;
import com.school.repository.GradeRepository;
import com.school.repository.LmdEnrollmentRepository;
import com.school.repository.ParentRepository;
import com.school.repository.StudentHistoryRepository;
import com.school.repository.StudentRepository;
import com.school.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Espace parent : accès restreint aux enfants liés au compte du parent connecté.
 */
@Service
@RequiredArgsConstructor
public class ParentService {

    private final ParentRepository parentRepository;
    private final StudentRepository studentRepository;
    private final BulletinRepository bulletinRepository;
    private final AttendanceRepository attendanceRepository;
    private final GradeRepository gradeRepository;
    private final StudentHistoryRepository studentHistoryRepository;
    private final EnrollmentHistoryRepository enrollmentHistoryRepository;
    private final LmdEnrollmentRepository lmdEnrollmentRepository;
    private final LmdService lmdService;

    @Transactional(readOnly = true)
    public List<StudentResponse> myChildren() {
        Parent parent = currentParent();
        return studentRepository.findByParentId(parent.getId()).stream()
                .map(StudentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BulletinResponse> childBulletins(Long studentId) {
        Student student = ownedStudent(studentId);
        return bulletinRepository.findByStudentId(student.getId()).stream()
                .map(BulletinResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AttendanceResponse> childAttendances(Long studentId) {
        Student student = ownedStudent(studentId);
        return attendanceRepository.findByStudentId(student.getId()).stream()
                .map(AttendanceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GradeResponse> childGrades(Long studentId) {
        Student student = ownedStudent(studentId);
        return gradeRepository.findByStudentId(student.getId()).stream()
                .map(GradeResponse::from)
                .toList();
    }

    /**
     * Timeline consolidée du parcours scolaire + universitaire d'un enfant.
     */
    @Transactional(readOnly = true)
    public List<AcademicTimelineEntry> childTimeline(Long studentId) {
        Student student = ownedStudent(studentId);
        List<AcademicTimelineEntry> entries = new ArrayList<>();
        studentHistoryRepository.findByStudentIdOrderByCreatedAtDesc(student.getId())
                .forEach(h -> entries.add(AcademicTimelineEntry.builder()
                        .id(h.getId())
                        .context("SCHOOL")
                        .action(h.getAction() != null ? h.getAction().name() : "EVENT")
                        .fromLabel(h.getFromClass())
                        .toLabel(h.getToClass())
                        .reason(h.getReason())
                        .date(h.getCreatedAt())
                        .build()));
        enrollmentHistoryRepository.findByStudentIdOrderByCreatedAtDesc(student.getId())
                .forEach(h -> entries.add(AcademicTimelineEntry.builder()
                        .id(h.getId())
                        .context("UNIVERSITY")
                        .action(h.getEnrollmentStatus() != null ? "ENROLLMENT_" + h.getEnrollmentStatus().name() : "LEVEL_CHANGE")
                        .fromLabel(h.getFromLevel() != null ? h.getFromLevel() : h.getFromSemester())
                        .toLabel(h.getToLevel() != null ? h.getToLevel() : h.getToSemester())
                        .academicYear(h.getAcademicYear())
                        .reason(h.getReason())
                        .status(h.getEnrollmentStatus() != null ? h.getEnrollmentStatus().name() : null)
                        .date(h.getCreatedAt())
                        .build()));
        entries.sort(Comparator.comparing(AcademicTimelineEntry::getDate,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return entries;
    }

    /**
     * Vérifie et retourne l'enfant appartenant au parent connecté.
     * Utilisé par les endpoints documentaires.
     */
    @Transactional(readOnly = true)
    public Student getOwnedChild(Long studentId) {
        return ownedStudent(studentId);
    }

    /** Inscriptions LMD d'un enfant appartenant au parent connecté. */
    @Transactional(readOnly = true)
    public List<com.school.entity.LmdEnrollment> childUniversityEnrollments(Long studentId) {
        Student student = ownedStudent(studentId);
        return lmdEnrollmentRepository.findByStudentIdOrderByIdDesc(student.getId());
    }

    /** Relevé universitaire d'un enfant (contrôle d'appartenance). */
    @Transactional(readOnly = true)
    public com.school.dto.response.LmdReleveResponse childUniversityReleve(Long studentId, Long fieldId,
                                                                           String semester, int session) {
        Student student = ownedStudent(studentId);
        return lmdService.buildReleve(student.getId(), fieldId, semester, session);
    }

    /** Attestation de réussite d'un enfant (contrôle d'appartenance). */
    @Transactional(readOnly = true)
    public com.school.dto.response.LmdAttestationResponse childUniversityAttestation(Long studentId, Long fieldId,
                                                                                      String semester, int session) {
        Student student = ownedStudent(studentId);
        return lmdService.buildAttestation(student.getId(), fieldId, semester, session);
    }

    private Parent currentParent() {
        Long userId = SecurityUtils.currentUserId();
        return parentRepository.findByUserIdOrderByIdAsc(userId).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException("Aucun dossier parent lié à ce compte"));
    }

    private Student ownedStudent(Long studentId) {
        Parent parent = currentParent();
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Élève", studentId));
        if (student.getParent() == null || !student.getParent().getId().equals(parent.getId())) {
            throw new BusinessException("Cet élève n'est pas lié à votre compte");
        }
        return student;
    }
}