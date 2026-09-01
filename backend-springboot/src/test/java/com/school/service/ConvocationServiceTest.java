package com.school.service;

import com.school.dto.request.ConvocationRequest;
import com.school.dto.response.ConvocationResponse;
import com.school.entity.Convocation;
import com.school.entity.Parent;
import com.school.entity.Student;
import com.school.entity.User;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.ConvocationRepository;
import com.school.repository.StudentRepository;
import com.school.service.ConvocationService;
import com.school.service.NotificationService;
import com.school.service.WhatsAppService;
import com.school.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests du module convocations : création, notification du parent, suppression.
 */
@ExtendWith(MockitoExtension.class)
class ConvocationServiceTest {

    @Mock
    private ConvocationRepository convocationRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private WhatsAppService whatsappService;
    @Mock
    private AuditService auditService;
    @Mock
    private HttpServletRequest httpRequest;

    private ConvocationService service;

    @BeforeEach
    void setUp() {
        service = new ConvocationService(convocationRepository, studentRepository,
                notificationService, whatsappService, auditService);
    }

    private Student student(Long id) {
        Parent parent = Parent.builder().id(7L)
                .user(User.builder().id(5L).username("parent1").build())
                .build();
        return Student.builder().id(id).firstName("Koffi").lastName("Konan")
                .matricule("ETU-" + id).parent(parent).build();
    }

    private ConvocationRequest request(Long studentId) {
        return ConvocationRequest.builder()
                .studentId(studentId)
                .context("SCHOOL")
                .subject("Réunion parents")
                .message("Votre présence est requise")
                .date(LocalDate.of(2026, 9, 10))
                .time(LocalDateTime.of(2026, 9, 10, 9, 0))
                .location("Salle polyvalente")
                .build();
    }

    @Test
    void createBuildsReferenceAndNotifiesParent() {
        Student student = student(10L);
        when(studentRepository.findById(10L)).thenReturn(Optional.of(student));
        when(convocationRepository.save(any(Convocation.class))).thenAnswer(inv -> {
            Convocation c = inv.getArgument(0);
            c.setId(99L);
            return c;
        });
        try (MockedStatic<com.school.utils.SecurityUtils> utils =
                     mockStatic(com.school.utils.SecurityUtils.class)) {
            utils.when(com.school.utils.SecurityUtils::currentUser).thenReturn(null);

            ConvocationResponse response = service.create(request(10L), httpRequest);

            assertThat(response.getId()).isEqualTo(99L);
            assertThat(response.getReference()).startsWith("CONV-");
            assertThat(response.getStudentId()).isEqualTo(10L);
            assertThat(response.getStudentName()).isEqualTo("Koffi Konan");
            assertThat(response.getSubject()).isEqualTo("Réunion parents");
            verify(convocationRepository).save(any(Convocation.class));
            verify(notificationService).notify(any(), any(), any(), any());
            verify(auditService).log(any(), any(), any(), any(), any());
        }
    }

    @Test
    void createThrowsWhenStudentNotFound() {
        when(studentRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(request(999L), httpRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteRemovesConvocation() {
        Convocation convocation = Convocation.builder().id(1L).reference("CONV-000001").build();
        when(convocationRepository.findById(1L)).thenReturn(Optional.of(convocation));
        service.delete(1L, httpRequest);
        verify(convocationRepository).delete(convocation);
    }

    @Test
    void deleteThrowsWhenNotFound() {
        when(convocationRepository.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(404L, httpRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createWithoutParentDoesNotNotify() {
        Student student = Student.builder().id(11L).firstName("Awa").lastName("Diallo")
                .matricule("ETU-11").parent(null).build();
        when(studentRepository.findById(11L)).thenReturn(Optional.of(student));
        when(convocationRepository.save(any(Convocation.class))).thenAnswer(inv -> {
            Convocation c = inv.getArgument(0);
            c.setId(100L);
            return c;
        });
        try (MockedStatic<com.school.utils.SecurityUtils> utils =
                     mockStatic(com.school.utils.SecurityUtils.class)) {
            utils.when(com.school.utils.SecurityUtils::currentUser).thenReturn(null);
            service.create(request(11L), httpRequest);
            verify(notificationService, never()).notify(any(), any(), any(), any());
        }
    }
}