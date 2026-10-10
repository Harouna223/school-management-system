package com.school.service;

import com.school.dto.response.AuditLogResponse;
import com.school.dto.response.DashboardResponse;
import com.school.dto.response.PageResponse;
import com.school.entity.AuditLog;
import com.school.enums.EducationCycle;
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