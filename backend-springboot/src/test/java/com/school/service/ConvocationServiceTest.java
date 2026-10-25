package com.school.service;

import com.school.dto.request.ConvocationRequest;
import com.school.dto.response.ConvocationResponse;
import com.school.entity.Convocation;
import com.school.entity.Parent;
import com.school.entity.Student;
import com.school.entity.Teacher;
import com.school.entity.User;
import com.school.enums.ConvocationTarget;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.ConvocationRepository;
import com.school.repository.ParentRepository;
import com.school.repository.StudentRepository;
import com.school.repository.TeacherRepository;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests du module convocations : création, notification (élève, parent,
 * enseignant) et suppression.
 */
@ExtendWith(MockitoExtension.class)
class ConvocationServiceTest {

    @Mock
    private ConvocationRepository convocationRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private ParentRepository parentRepository;
    @Mock
    private TeacherRepository teacherRepository;
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
                parentRepository, teacherRepository,
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

    // ==================================================================
    // Cibles élargies : parent d'élève et enseignant
    // ==================================================================

    /** Un parent convoqué directement : notification interne + WhatsApp, sans élève. */
    @Test
    void createPourUnParentNotifieSansEleve() {
        Parent parent = Parent.builder().id(7L).firstName("Koffi").lastName("Ama")
                .user(User.builder().id(5L).username("parent1").build()).build();
        when(parentRepository.findById(7L)).thenReturn(Optional.of(parent));
        when(convocationRepository.save(any(Convocation.class))).thenAnswer(inv -> {
            Convocation c = inv.getArgument(0);
            c.setId(101L);
            return c;
        });
        ConvocationRequest request = ConvocationRequest.builder()
                .target(ConvocationTarget.PARENT)
                .parentId(7L)
                .context("SCHOOL")
                .subject("Entretien")
                .date(LocalDate.of(2026, 9, 10))
                .time(LocalDateTime.of(2026, 9, 10, 9, 0))
                .location("Bureau du directeur")
                .build();
        try (MockedStatic<com.school.utils.SecurityUtils> utils =
                     mockStatic(com.school.utils.SecurityUtils.class)) {
            utils.when(com.school.utils.SecurityUtils::currentUser).thenReturn(null);

            ConvocationResponse response = service.create(request, httpRequest);

            assertThat(response.getTarget()).isEqualTo(ConvocationTarget.PARENT);
            assertThat(response.getParentId()).isEqualTo(7L);
            assertThat(response.getParentName()).isEqualTo("Koffi Ama");
            assertThat(response.getStudentId()).isNull();
            verify(notificationService).notify(any(), any(), any(), any());
            verify(whatsappService).sendConvocationToParent(eq(parent), eq("Entretien"),
                    any(), any(), any());
            verify(studentRepository, never()).findById(anyLong());
        }
    }

    /**
     * Un enseignant convoqué : notification interne uniquement — l'historique
     * {@code message_logs} ne porte pas de rattachement enseignant.
     */
    @Test
    void createPourUnEnseignantNotifieSansWhatsApp() {
        Teacher teacher = Teacher.builder().id(3L).firstName("Jean").lastName("Kamdem")
                .user(User.builder().id(9L).username("prof1").build()).build();
        when(teacherRepository.findById(3L)).thenReturn(Optional.of(teacher));
        when(convocationRepository.save(any(Convocation.class))).thenAnswer(inv -> {
            Convocation c = inv.getArgument(0);
            c.setId(102L);
            return c;
        });
        ConvocationRequest request = ConvocationRequest.builder()
                .target(ConvocationTarget.ENSEIGNANT)
                .teacherId(3L)
                .context("SCHOOL")
                .subject("Conseil de classe")
                .date(LocalDate.of(2026, 9, 10))
                .time(LocalDateTime.of(2026, 9, 10, 15, 0))
                .location("Salle des professeurs")
                .build();
        try (MockedStatic<com.school.utils.SecurityUtils> utils =
                     mockStatic(com.school.utils.SecurityUtils.class)) {
            utils.when(com.school.utils.SecurityUtils::currentUser).thenReturn(null);

            ConvocationResponse response = service.create(request, httpRequest);

            assertThat(response.getTarget()).isEqualTo(ConvocationTarget.ENSEIGNANT);
            assertThat(response.getTeacherId()).isEqualTo(3L);
            assertThat(response.getTeacherName()).isEqualTo("Jean Kamdem");
            verify(notificationService).notify(any(), any(), any(), any());
            verifyNoInteractions(whatsappService);
        }
    }

    /** Cible déclarée sans son identifiant : refus explicite, jamais d'entité orpheline. */
    @Test
    void createRefuseUneCibleSansIdentifiant() {
        ConvocationRequest request = ConvocationRequest.builder()
                .target(ConvocationTarget.PARENT)
                .context("SCHOOL")
                .subject("Entretien")
                .date(LocalDate.of(2026, 9, 10))
                .time(LocalDateTime.of(2026, 9, 10, 9, 0))
                .location("Bureau")
                .build();

        assertThatThrownBy(() -> service.create(request, httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("parent");
    }

    /** Cible absente : comportement historique conservé (convocation d'élève). */
    @Test
    void createSansCibleResteUneConvocationDEleve() {
        when(studentRepository.findById(10L)).thenReturn(Optional.of(student(10L)));
        when(convocationRepository.save(any(Convocation.class))).thenAnswer(inv -> {
            Convocation c = inv.getArgument(0);
            c.setId(103L);
            return c;
        });
        try (MockedStatic<com.school.utils.SecurityUtils> utils =
                     mockStatic(com.school.utils.SecurityUtils.class)) {
            utils.when(com.school.utils.SecurityUtils::currentUser).thenReturn(null);

            ConvocationResponse response = service.create(request(10L), httpRequest);

            assertThat(response.getTarget()).isEqualTo(ConvocationTarget.ELEVE);
            assertThat(response.getStudentId()).isEqualTo(10L);
        }
    }
}