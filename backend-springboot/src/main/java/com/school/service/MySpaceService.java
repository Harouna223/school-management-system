package com.school.service;

import com.school.dto.response.AttendanceResponse;
import com.school.dto.response.BulletinResponse;
import com.school.dto.response.InvoiceResponse;
import com.school.dto.response.LmdReleveResponse;
import com.school.dto.response.MyGradeResponse;
import com.school.dto.response.MyTeacherClassResponse;
import com.school.dto.response.ScheduleResponse;
import com.school.dto.response.StudentResponse;
import com.school.dto.response.TeacherResponse;
import com.school.entity.Attendance;
import com.school.entity.EnrollmentHistory;
import com.school.entity.Grade;
import com.school.entity.Invoice;
import com.school.entity.LmdEnrollment;
import com.school.entity.Schedule;
import com.school.entity.Student;
import com.school.entity.Teacher;
import com.school.enums.Term;
import com.school.exception.BusinessException;
import com.school.repository.AttendanceRepository;
import com.school.repository.BulletinRepository;
import com.school.repository.EnrollmentHistoryRepository;
import com.school.repository.GradeRepository;
import com.school.repository.InvoiceRepository;
import com.school.repository.LmdEnrollmentRepository;
import com.school.repository.ScheduleRepository;
import com.school.repository.StudentRepository;
import com.school.repository.SubjectAssignmentRepository;
import com.school.repository.TeacherRepository;
import com.school.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Espace personnel (élève / enseignant) : accès aux données du compte connecté uniquement.
 */
@Service
@RequiredArgsConstructor
public class MySpaceService {

    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final ScheduleRepository scheduleRepository;
    private final GradeRepository gradeRepository;
    private final BulletinRepository bulletinRepository;
    private final AttendanceRepository attendanceRepository;
    private final InvoiceRepository invoiceRepository;
    private final SubjectAssignmentRepository subjectAssignmentRepository;
    private final LmdEnrollmentRepository lmdEnrollmentRepository;
    private final LmdService lmdService;
    private final EnrollmentHistoryRepository enrollmentHistoryRepository;

    private Student currentStudent() {
        Long userId = SecurityUtils.currentUserId();
        return studentRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("Aucun profil élève n'est lié à ce compte"));
    }

    private Teacher currentTeacher() {
        Long userId = SecurityUtils.currentUserId();
        return teacherRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("Aucun profil enseignant n'est lié à ce compte"));
    }

    @Transactional(readOnly = true)
    public StudentResponse myProfile() {
        return StudentResponse.from(currentStudent());
    }

    /** Inscriptions LMD de l'étudiant connecté (espace universitaire). */
    @Transactional(readOnly = true)
    public List<LmdEnrollment> myUniversityEnrollments() {
        return lmdEnrollmentRepository.findByStudentIdOrderByIdDesc(currentStudent().getId());
    }

    /** Relevé universitaire détaillé (UE/notes/crédits) de l'étudiant connecté. */
    @Transactional(readOnly = true)
    public LmdReleveResponse myUniversityReleve(Long fieldId, String semester, int session) {
        return lmdService.buildReleve(currentStudent().getId(), fieldId, semester, session);
    }

    /** Historique académique universitaire de l'étudiant connecté. */
    @Transactional(readOnly = true)
    public List<EnrollmentHistory> myUniversityHistory() {
        return enrollmentHistoryRepository.findByStudentIdOrderByCreatedAtDesc(currentStudent().getId());
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> mySchedule() {
        com.school.entity.SchoolClass schoolClass = currentStudent().getSchoolClass();
        if (schoolClass == null) {
            return java.util.List.of();
        }
        List<Schedule> list = scheduleRepository.findBySchoolClassId(schoolClass.getId());
        return list.stream()
                .sorted(Comparator.comparing(Schedule::getDayOfWeek).thenComparing(Schedule::getStartTime))
                .map(ScheduleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MyGradeResponse> myGrades(Term term) {
        List<Grade> grades = gradeRepository.findByStudentId(currentStudent().getId());
        if (term != null) {
            grades = grades.stream().filter(g -> g.getExam().getTerm() == term).toList();
        }
        return grades.stream()
                .sorted(Comparator.comparing((Grade g) -> g.getExam().getExamDate()).reversed())
                .map(MyGradeResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BulletinResponse> myBulletins() {
        return bulletinRepository
                .findByStudentIdOrderByAcademicYearDescTermDesc(currentStudent().getId())
                .stream().map(BulletinResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AttendanceResponse> myAttendances() {
        List<Attendance> list = attendanceRepository.findByStudentId(currentStudent().getId());
        return list.stream()
                .sorted(Comparator.comparing(Attendance::getDate).reversed())
                .map(AttendanceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> myInvoices() {
        List<Invoice> list = invoiceRepository.findByStudentId(currentStudent().getId());
        return list.stream()
                .sorted(Comparator.comparing(Invoice::getDueDate).reversed())
                .map(InvoiceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TeacherResponse myTeacherProfile() {
        return TeacherResponse.from(currentTeacher());
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> myTeacherSchedule() {
        List<Schedule> list = scheduleRepository.findByTeacherId(currentTeacher().getId());
        return list.stream()
                .sorted(Comparator.comparing(Schedule::getDayOfWeek).thenComparing(Schedule::getStartTime))
                .map(ScheduleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MyTeacherClassResponse> myTeacherClasses() {
        return subjectAssignmentRepository.findByTeacherId(currentTeacher().getId())
                .stream().map(MyTeacherClassResponse::from).toList();
    }
}