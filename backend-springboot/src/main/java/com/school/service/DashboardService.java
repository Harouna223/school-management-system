package com.school.service;

import com.school.dto.response.AuditLogResponse;
import com.school.dto.response.DashboardResponse;
import com.school.dto.response.PageResponse;
import com.school.dto.response.UniversityDashboardResponse;
import com.school.dto.response.UniversityReportResponse;
import com.school.entity.AuditLog;
import com.school.entity.LmdEnrollment;
import com.school.enums.EducationCycle;
import com.school.enums.LmdDecision;
import com.school.enums.StudentStatus;
import com.school.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Agrégation des statistiques des tableaux de bord.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DashboardService {

    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final SchoolClassRepository classRepository;
    private final SubjectRepository subjectRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final ExpenseRepository expenseRepository;
    private final AttendanceRepository attendanceRepository;
    private final BookRepository bookRepository;
    private final BorrowingRepository borrowingRepository;
    private final LeaveRepository leaveRepository;
    private final AuditLogRepository auditLogRepository;
    private final LmdEnrollmentRepository lmdEnrollmentRepository;
    private final AcademicFieldRepository fieldRepository;
    private final ProgramRepository programRepository;
    private final UniversityUnitRepository universityUnitRepository;
    private final LmdDeliberationRepository deliberationRepository;
    private final ConvocationRepository convocationRepository;
    private final UniversityExamRepository universityExamRepository;
    private final StageRepository stageRepository;

    public UniversityDashboardResponse universityStats() {
        List<LmdEnrollment> enrollments = lmdEnrollmentRepository.findAll();
        long admis = deliberationRepository.findAll().stream()
                .filter(d -> d.getDecision() == LmdDecision.ADMIS
                        || d.getDecision() == LmdDecision.PASSAGE_AVEC_DETTES).count();
        long ajournes = deliberationRepository.findAll().stream()
                .filter(d -> d.getDecision() == LmdDecision.AJOURNE
                        || d.getDecision() == LmdDecision.REDOUBLE).count();
        long totalDecisions = admis + ajournes;
        double successRate = totalDecisions == 0 ? 0 : Math.round(admis * 1000.0 / totalDecisions) / 10.0;

        // Répartition par niveau
        java.util.Map<String, Long> byLevel = enrollments.stream()
                .filter(e -> e.getLevel() != null)
                .collect(java.util.stream.Collectors.groupingBy(e -> e.getLevel().name(), java.util.stream.Collectors.counting()));
        // Répartition par filière
        java.util.Map<String, Long> byField = enrollments.stream()
                .filter(e -> e.getField() != null)
                .collect(java.util.stream.Collectors.groupingBy(e -> e.getField().getName(), java.util.stream.Collectors.counting()));

        return UniversityDashboardResponse.builder()
                .totalEnrollments(enrollments.size())
                .totalFields(fieldRepository.count())
                .totalPrograms(programRepository.count())
                .totalUes(universityUnitRepository.count())
                .totalTeachers(teacherRepository.count())
                .activeStudents(enrollments.stream().filter(LmdEnrollment::isActive).count())
                .admisCount(admis)
                .ajournesCount(ajournes)
                .successRate(successRate)
                .byLevel(byLevel.entrySet().stream()
                        .map(e -> UniversityDashboardResponse.ChartPoint.builder().label(e.getKey()).value(e.getValue()).build()).toList())
                .byField(byField.entrySet().stream()
                        .map(e -> UniversityDashboardResponse.ChartPoint.builder().label(e.getKey()).value(e.getValue()).build()).toList())
                .build();
    }

    /**
     * Rapports universitaires : effectifs, répartition par niveau/filière,
     * réussite par filière, dettes académiques.
     */
    public UniversityReportResponse universityReport() {
        List<LmdEnrollment> enrollments = lmdEnrollmentRepository.findAll();
        List<com.school.entity.LmdDeliberation> deliberations = deliberationRepository.findAll();

        // Répartition par filière
        List<UniversityReportResponse.FieldStat> byField = enrollments.stream()
                .filter(e -> e.getField() != null)
                .collect(java.util.stream.Collectors.groupingBy(e -> e.getField().getName(),
                        java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .map(e -> UniversityReportResponse.FieldStat.builder()
                        .fieldName(e.getKey()).count(e.getValue()).build())
                .toList();

        // Répartition par niveau
        List<UniversityReportResponse.LevelStat> byLevel = enrollments.stream()
                .filter(e -> e.getLevel() != null)
                .collect(java.util.stream.Collectors.groupingBy(e -> e.getLevel().name(),
                        java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .map(e -> UniversityReportResponse.LevelStat.builder()
                        .level(e.getKey()).count(e.getValue()).build())
                .toList();

        // Réussite par filière (sur les délibérations)
        java.util.Map<String, List<com.school.entity.LmdDeliberation>> byFieldName = deliberations.stream()
                .filter(d -> d.getField() != null)
                .collect(java.util.stream.Collectors.groupingBy(d -> d.getField().getName()));
        List<UniversityReportResponse.SuccessStat> successByField = byFieldName.entrySet().stream()
                .map(e -> {
                    long admis = e.getValue().stream()
                            .filter(d -> d.getDecision() == LmdDecision.ADMIS
                                    || d.getDecision() == LmdDecision.PASSAGE_AVEC_DETTES).count();
                    long ajournes = e.getValue().stream()
                            .filter(d -> d.getDecision() == LmdDecision.AJOURNE
                                    || d.getDecision() == LmdDecision.REDOUBLE).count();
                    long total = admis + ajournes;
                    BigDecimal rate = total == 0 ? BigDecimal.ZERO
                            : BigDecimal.valueOf(Math.round(admis * 10000.0 / total) / 100.0);
                    return UniversityReportResponse.SuccessStat.builder()
                            .fieldName(e.getKey()).admis(admis).ajournes(ajournes)
                            .successRate(rate).build();
                })
                .toList();

        long withDebts = deliberations.stream()
                .filter(d -> d.getUesToRetake() != null && !d.getUesToRetake().isBlank()
                        && d.getDecision() == LmdDecision.PASSAGE_AVEC_DETTES).count();

        return UniversityReportResponse.builder()
                .totalEnrollments(enrollments.size())
                .totalActive(enrollments.stream().filter(LmdEnrollment::isActive).count())
                .totalDeliberations(deliberations.size())
                .withDebts(withDebts)
                .totalExams(universityExamRepository.count())
                .totalStages(stageRepository.count())
                .totalConvocations(convocationRepository.count())
                .byField(byField)
                .byLevel(byLevel)
                .successByField(successByField)
                .build();
    }

    public DashboardResponse stats() {
        return stats(null);
    }

    public DashboardResponse stats(EducationCycle cycle) {
        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);

        // Élèves & enseignants (filtrés par cycle si fourni)
        long totalStudents = cycle != null
                ? studentRepository.countByEducationCycle(cycle)
                : studentRepository.count();
        long activeStudents = cycle != null
                ? studentRepository.countByStatusAndEducationCycle(StudentStatus.ACTIVE, cycle)
                : studentRepository.countByStatus(StudentStatus.ACTIVE);

        // Classes du cycle ou toutes
        List<com.school.entity.SchoolClass> cycleClasses = cycle != null
                ? classRepository.findByLevelEducationCycle(cycle)
                : classRepository.findAll();
        long totalClasses = cycleClasses.size();

        // Finances du mois (toujours global)
        BigDecimal monthlyRevenue = paymentRepository.sumBetween(
                currentMonth.atDay(1).atStartOfDay(), today.atTime(LocalTime.MAX));
        BigDecimal monthlyExpenses = expenseRepository.sumBetween(currentMonth.atDay(1), today.plusDays(1));

        // Présences du jour (filtrées par cycle)
        long todayPresent = attendanceRepository.countByDateAndStatusAndCycle(today,
                com.school.enums.AttendanceStatus.PRESENT, cycle);
        long todayAbsent = attendanceRepository.countByDateAndStatusAndCycle(today,
                com.school.enums.AttendanceStatus.ABSENT, cycle);
        long totalLate = attendanceRepository.countByDateAndStatusAndCycle(today,
                com.school.enums.AttendanceStatus.LATE, cycle);

        return DashboardResponse.builder()
                .totalStudents(totalStudents)
                .activeStudents(activeStudents)
                .totalTeachers(teacherRepository.count())
                .totalClasses(totalClasses)
                .totalSubjects(subjectRepository.count())
                .studentsPerClassAvg(totalStudents == 0 ? 0D :
                        (double) totalStudents / Math.max(1, totalClasses))
                .unpaidInvoices(invoiceRepository.countByStatus(com.school.enums.InvoiceStatus.UNPAID)
                        + invoiceRepository.countByStatus(com.school.enums.InvoiceStatus.PARTIAL))
                .totalPaymentsCount(paymentRepository.count())
                .monthlyRevenue(monthlyRevenue)
                .monthlyExpenses(monthlyExpenses)
                .totalRevenue(paymentRepository.sumTotal())
                .totalExpenses(expenseRepository.sumTotal())
                .todayPresent(todayPresent)
                .todayAbsent(todayAbsent)
                .totalLate(totalLate)
                .totalBooks(bookRepository.count())
                .borrowedBooks(borrowingRepository.countByStatus(com.school.enums.BorrowingStatus.BORROWED))
                .pendingLeaves(leaveRepository.findByStatus(com.school.enums.LeaveStatus.PENDING).size())
                .revenueByMonth(last6MonthsRevenue())
                .studentsByClass(studentsByClass(cycleClasses))
                .studentsByGender(studentsByGender(cycle))
                .build();
    }

    /**
     * Recettes des 6 derniers mois pour le graphique Recharts.
     */
    private List<DashboardResponse.ChartPoint> last6MonthsRevenue() {
        List<DashboardResponse.ChartPoint> points = new ArrayList<>();
        YearMonth current = YearMonth.now();
        for (int i = 5; i >= 0; i--) {
            YearMonth month = current.minusMonths(i);
            BigDecimal revenue = paymentRepository.sumBetween(
                    month.atDay(1).atStartOfDay(),
                    month.plusMonths(1).atDay(1).atStartOfDay());
            points.add(DashboardResponse.ChartPoint.builder()
                    .label(month.format(DateTimeFormatter.ofPattern("MMM")))
                    .value(revenue)
                    .build());
        }
        return points;
    }

    private List<DashboardResponse.ChartPoint> studentsByClass(
            List<com.school.entity.SchoolClass> cycleClasses) {
        return cycleClasses.stream()
                .map(c -> DashboardResponse.ChartPoint.builder()
                        .label(c.getName())
                        .value(studentRepository.countBySchoolClassId(c.getId()))
                        .build())
                .toList();
    }

    /**
     * Répartition des élèves par genre (MALE / FEMALE), filtrée par cycle.
     */
    private List<DashboardResponse.ChartPoint> studentsByGender(EducationCycle cycle) {
        return studentRepository.countByGender(cycle).stream()
                .map(row -> DashboardResponse.ChartPoint.builder()
                        .label(row[0] == null ? "Indéfini"
                                : row[0].toString().equals("MALE") ? "Garçons" : "Filles")
                        .value((Number) row[1])
                        .build())
                .toList();
    }

    // ---------- Audit logs ----------

    public PageResponse<AuditLogResponse> auditLogs(String username, String action, String entity,
                                                    LocalDate from, LocalDate to, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        LocalDateTime fromDt = from != null ? from.atStartOfDay() : null;
        LocalDateTime toDt = to != null ? to.atTime(LocalTime.MAX) : null;
        Page<AuditLog> result = auditLogRepository.search(
                username != null && !username.isBlank() ? username.trim() : null,
                action != null && !action.isBlank() ? action.trim() : null,
                entity != null && !entity.isBlank() ? entity.trim() : null,
                fromDt, toDt, pageable);
        return PageResponse.from(result, AuditLogResponse::from);
    }
}