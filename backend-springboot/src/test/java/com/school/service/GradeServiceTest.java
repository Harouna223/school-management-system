package com.school.service;

import com.school.dto.request.GradeRequest;
import com.school.entity.Exam;
import com.school.entity.Student;
import com.school.exception.BusinessException;
import com.school.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Tests des notes : bornes 0-20 et validation des valeurs hors limites.
 */
@ExtendWith(MockitoExtension.class)
class GradeServiceTest {

    @Mock
    private GradeRepository gradeRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private BulletinRepository bulletinRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private ExamService examService;
    @Mock
    private AuditService auditService;
    @Mock
    private ReportService reportService;
    @Mock
    private WhatsAppService whatsappService;
    @Mock
    private EmailService emailService;
    @Mock
    private SmsService smsService;

    private GradeService service;

    @BeforeEach
    void setUp() {
        service = new GradeService(gradeRepository, studentRepository, bulletinRepository,
                notificationRepository, examService, auditService, reportService,
                whatsappService, emailService, smsService);
    }

    private GradeRequest request(String value, String maxValue) {
        return GradeRequest.builder()
                .studentId(10L)
                .examId(1L)
                .value(value != null ? new BigDecimal(value) : null)
                .maxValue(maxValue != null ? new BigDecimal(maxValue) : null)
                .build();
    }

    @Test
    void negativeGradeIsRejected() {
        when(examService.findById(1L)).thenReturn(Exam.builder().id(1L).build());
        when(studentRepository.findById(10L))
                .thenReturn(java.util.Optional.of(Student.builder().id(10L).build()));

        assertThatThrownBy(() -> service.save(request("-1", null), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("négative");
    }

    @Test
    void gradeAboveTwentyIsRejected() {
        when(examService.findById(1L)).thenReturn(Exam.builder().id(1L).build());
        when(studentRepository.findById(10L))
                .thenReturn(java.util.Optional.of(Student.builder().id(10L).build()));

        assertThatThrownBy(() -> service.save(request("21", null), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ne peut pas dépasser");
    }

    @Test
    void gradeAboveCustomMaxIsRejected() {
        when(examService.findById(1L)).thenReturn(Exam.builder().id(1L).build());
        when(studentRepository.findById(10L))
                .thenReturn(java.util.Optional.of(Student.builder().id(10L).build()));

        assertThatThrownBy(() -> service.save(request("15", "10"), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ne peut pas dépasser");
    }
}
