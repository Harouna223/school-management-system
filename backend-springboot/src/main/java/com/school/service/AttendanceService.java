package com.school.service;

import com.school.dto.request.AttendanceRequest;
import com.school.dto.request.TeacherAttendanceRequest;
import com.school.dto.response.AttendanceRecordResponse;
import com.school.dto.response.AttendanceResponse;
import com.school.dto.response.TeacherAttendanceResponse;
import com.school.dto.response.WhatsappAlert;
import com.school.entity.Attendance;
import com.school.entity.Notification;
import com.school.entity.Parent;
import com.school.entity.Student;
import com.school.entity.Teacher;
import com.school.entity.TeacherAttendance;
import com.school.enums.AttendanceStatus;
import com.school.enums.NotificationType;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.AttendanceRepository;
import com.school.repository.NotificationRepository;
import com.school.repository.StudentRepository;
import com.school.repository.TeacherAttendanceRepository;
import com.school.repository.TeacherRepository;
import com.school.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Module présences : pointage par classe/date, retards, justifications, notifications.
 */
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final StudentRepository studentRepository;
    private final NotificationRepository notificationRepository;
    private final TeacherAttendanceRepository teacherAttendanceRepository;
    private final TeacherRepository teacherRepository;
    private final AuditService auditService;
    private final WhatsAppService whatsappService;
    private final EmailService emailService;
    private final SmsService smsService;
    private final PhoneNumberService phoneNumberService;

    @Transactional(readOnly = true)
    public List<AttendanceResponse> listByClassAndDate(Long classId, LocalDate date) {
        return attendanceRepository.findBySchoolClassIdAndDate(classId, date).stream()
                .map(AttendanceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AttendanceResponse> listByStudent(Long studentId) {
        return attendanceRepository.findByStudentId(studentId).stream()
                .map(AttendanceResponse::from).toList();
    }

    /**
     * Pointage groupé : une entrée par élève pour la classe et la date données.
     * Retourne les présences enregistrées + les liens WhatsApp (wa.me) à ouvrir
     * dans le navigateur pour informer les parents des absents/retards.
     */
    @Transactional
    public AttendanceRecordResponse record(AttendanceRequest request, HttpServletRequest httpRequest) {
        List<AttendanceResponse> saved = request.getEntries().stream()
                .map(entry -> recordSingle(request.getClassId(), request.getDate(), entry, httpRequest))
                .map(AttendanceResponse::from)
                .toList();

        List<WhatsappAlert> alerts = buildWhatsappAlerts(request);

        long absent = request.getEntries().stream().filter(e -> e.getStatus() == AttendanceStatus.ABSENT).count();
        long late = request.getEntries().stream().filter(e -> e.getStatus() == AttendanceStatus.LATE).count();
        auditService.log("RECORD", "Attendance", null,
                "Pointage du " + request.getDate() + " (classe " + request.getClassId()
                        + ") : " + saved.size() + " élève(s), " + absent + " absent(s), " + late + " retard(s)",
                httpRequest);

        // Notification des parents en cas d'absence ou retard
        if (request.getEntries().stream().anyMatch(e ->
                e.getStatus() == AttendanceStatus.ABSENT || e.getStatus() == AttendanceStatus.LATE)) {
            notifyParents(request);
        }
        return AttendanceRecordResponse.builder()
                .attendance(saved)
                .alerts(alerts)
                .build();
    }

    /**
     * Construit les liens wa.me pour ouvrir WhatsApp (Desktop/Web) avec le message pré-rempli.
     */
    private List<WhatsappAlert> buildWhatsappAlerts(AttendanceRequest request) {
        List<WhatsappAlert> alerts = new ArrayList<>();
        for (AttendanceRequest.Entry entry : request.getEntries()) {
            if (entry.getStatus() != AttendanceStatus.ABSENT
                    && entry.getStatus() != AttendanceStatus.LATE) {
                continue;
            }
            Student student = studentRepository.findById(entry.getStudentId()).orElse(null);
            if (student == null || student.getParent() == null) {
                continue;
            }
            Parent parent = student.getParent();
            String phone = phoneNumberService.normalize(parent.getPhone());
            if (phone == null) {
                continue;
            }
            String message = whatsappService.absenceMessage(parent, student, request.getDate(),
                    entry.getStatus(), entry.getJustification());
            String waLink = "https://wa.me/" + phone.replace("+", "") + "?text="
                    + URLEncoder.encode(message, StandardCharsets.UTF_8);
            alerts.add(WhatsappAlert.builder()
                    .phone(phone)
                    .message(message)
                    .waLink(waLink)
                    .build());
        }
        return alerts;
    }

    private Attendance recordSingle(Long classId, LocalDate date, AttendanceRequest.Entry entry,
                                    HttpServletRequest httpRequest) {
        Student student = studentRepository.findById(entry.getStudentId())
                .orElseThrow(() -> ResourceNotFoundException.of("Élève", entry.getStudentId()));

        Attendance attendance = attendanceRepository
                .findByStudentIdAndDate(student.getId(), date)
                .orElseGet(() -> Attendance.builder()
                        .student(student)
                        .schoolClass(student.getSchoolClass())
                        .date(date)
                        .build());

        attendance.setStatus(entry.getStatus());
        attendance.setJustification(entry.getJustification());
        attendance.setRecordedBy(SecurityUtils.currentUser());
        return attendanceRepository.save(attendance);
    }

    /**
     * Notifie uniquement les parents des élèves réellement absents ou en retard.
     */
    private void notifyParents(AttendanceRequest request) {
        for (AttendanceRequest.Entry entry : request.getEntries()) {
            if (entry.getStatus() != AttendanceStatus.ABSENT
                    && entry.getStatus() != AttendanceStatus.LATE) {
                continue;
            }
            Student student = studentRepository.findById(entry.getStudentId())
                    .orElse(null);
            if (student == null || student.getParent() == null) {
                continue;
            }
            Parent parent = student.getParent();
            boolean absent = entry.getStatus() == AttendanceStatus.ABSENT;

            // 1. Notification in-app (nécessite un compte utilisateur parent)
            if (parent.getUser() != null) {
                notificationRepository.save(Notification.builder()
                        .user(parent.getUser())
                        .title("Absence signalée - " + student.getFullName())
                        .message(student.getFullName() + " a été signalé " +
                                (absent ? "absent" : "en retard") + " le " + request.getDate() +
                                (entry.getJustification() != null && !entry.getJustification().isBlank()
                                        ? " (justification : " + entry.getJustification() + ")" : ""))
                        .type(NotificationType.WARNING)
                        .link("/attendances")
                        .build());
            }

            // 2. WhatsApp — fonctionne avec le seul numéro de téléphone du parent
            whatsappService.sendAbsenceAlert(parent, student, request.getDate(),
                    entry.getStatus(), entry.getJustification());
            // 3. Email
            emailService.sendAbsenceAlert(parent, student, request.getDate(),
                    entry.getStatus(), entry.getJustification());
            // 4. SMS
            smsService.sendAbsenceAlert(parent, student, request.getDate(),
                    entry.getStatus(), entry.getJustification());
        }
    }

    @Transactional
    public AttendanceResponse justify(Long attendanceId, String justification,
                                      HttpServletRequest httpRequest) {
        Attendance attendance = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> ResourceNotFoundException.of("Présence", attendanceId));
        if (attendance.getStatus() != AttendanceStatus.ABSENT
                && attendance.getStatus() != AttendanceStatus.LATE) {
            throw new BusinessException("Seules les absences et retards peuvent être justifiés");
        }
        attendance.setStatus(AttendanceStatus.JUSTIFIED);
        attendance.setJustification(justification);
        auditService.log("JUSTIFY", "Attendance", attendanceId,
                "Justification : " + justification, httpRequest);
        return AttendanceResponse.from(attendanceRepository.save(attendance));
    }

    public long absencesOf(Long studentId) {
        return attendanceRepository.countByStudentIdAndStatus(studentId, AttendanceStatus.ABSENT);
    }

    public long presentsOf(Long studentId) {
        return attendanceRepository.countByStudentIdAndStatus(studentId, AttendanceStatus.PRESENT);
    }

    public long latesOf(Long studentId) {
        return attendanceRepository.countByStudentIdAndStatus(studentId, AttendanceStatus.LATE);
    }

    // ---------- Rapport de présences ----------

    /**
     * Rapport de présences d'une classe sur une période : compteurs par élève.
     */
    @Transactional(readOnly = true)
    public List<com.school.dto.response.AttendanceReportRow> classReport(Long classId, LocalDate from, LocalDate to) {
        List<Student> students = studentRepository.findBySchoolClassId(classId);
        if (from == null) from = LocalDate.now().minusMonths(1);
        if (to == null) to = LocalDate.now();
        java.util.List<Attendance> records =
                attendanceRepository.findBySchoolClassIdAndDateBetween(classId, from, to);

        java.util.Map<Long, java.util.List<Attendance>> byStudent = records.stream()
                .collect(java.util.stream.Collectors.groupingBy(a -> a.getStudent().getId()));

        return students.stream().map(student -> {
            java.util.List<Attendance> list = byStudent.getOrDefault(student.getId(), java.util.List.of());
            long present = list.stream().filter(a -> a.getStatus() == AttendanceStatus.PRESENT).count();
            long absent = list.stream().filter(a -> a.getStatus() == AttendanceStatus.ABSENT).count();
            long late = list.stream().filter(a -> a.getStatus() == AttendanceStatus.LATE).count();
            long justified = list.stream().filter(a -> a.getStatus() == AttendanceStatus.JUSTIFIED).count();
            long total = present + absent + late + justified;
            java.math.BigDecimal rate = total == 0 ? null
                    : java.math.BigDecimal.valueOf(present * 100.0 / total)
                            .setScale(1, java.math.RoundingMode.HALF_UP);
            return com.school.dto.response.AttendanceReportRow.builder()
                    .studentId(student.getId())
                    .matricule(student.getMatricule())
                    .lastName(student.getLastName())
                    .firstName(student.getFirstName())
                    .present(present)
                    .absent(absent)
                    .late(late)
                    .justified(justified)
                    .rate(rate)
                    .build();
        }).toList();
    }

    // ---------- Présences enseignants ----------

    @Transactional(readOnly = true)
    public List<TeacherAttendanceResponse> listTeachersByDate(LocalDate date) {
        return teacherAttendanceRepository.findByDateOrderByTeacherLastNameAsc(date).stream()
                .map(TeacherAttendanceResponse::from).toList();
    }

    /**
     * Pointage groupé des enseignants pour une date donnée (upsert).
     */
    @Transactional
    public List<TeacherAttendanceResponse> recordTeachers(TeacherAttendanceRequest request,
                                                          HttpServletRequest httpRequest) {
        List<TeacherAttendance> saved = request.getEntries().stream()
                .map(entry -> {
                    Teacher teacher = teacherRepository.findById(entry.getTeacherId())
                            .orElseThrow(() -> ResourceNotFoundException.of("Enseignant", entry.getTeacherId()));
                    TeacherAttendance attendance = teacherAttendanceRepository
                            .findByTeacherIdAndDate(teacher.getId(), request.getDate())
                            .orElseGet(() -> TeacherAttendance.builder()
                                    .teacher(teacher)
                                    .date(request.getDate())
                                    .build());
                    attendance.setStatus(entry.getStatus());
                    attendance.setJustification(entry.getJustification());
                    return teacherAttendanceRepository.save(attendance);
                })
                .toList();
        auditService.log("TEACHER_ATTENDANCE", "TeacherAttendance", null,
                "Pointage des enseignants du " + request.getDate(), httpRequest);
        return saved.stream().map(TeacherAttendanceResponse::from).toList();
    }
}