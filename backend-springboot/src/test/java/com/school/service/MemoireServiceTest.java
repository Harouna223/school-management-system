package com.school.service;

import com.school.dto.request.MemoireRequest;
import com.school.dto.response.MemoireResponse;
import com.school.entity.Memoire;
import com.school.entity.Student;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.MemoireRepository;
import com.school.repository.StudentRepository;
import com.school.repository.TeacherRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests du module mémoires universitaires.
 */
@ExtendWith(MockitoExtension.class)
class MemoireServiceTest {

    @Mock
    private MemoireRepository memoireRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private TeacherRepository teacherRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private HttpServletRequest httpRequest;

    private MemoireService service;

    @BeforeEach
    void setUp() {
        service = new MemoireService(memoireRepository, studentRepository, teacherRepository, auditService);
    }

    private Student student(Long id) {
        return Student.builder().id(id).firstName("Awa").lastName("Diop").matricule("ETU-" + id).build();
    }

    @Test
    void createRequiresSubject() {
        MemoireRequest request = MemoireRequest.builder().studentId(1L).subject("  ").build();
        assertThatThrownBy(() -> service.create(request, httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("sujet");
    }

    @Test
    void createExposesStudentName() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student(1L)));
        when(memoireRepository.save(any(Memoire.class))).thenAnswer(inv -> {
            Memoire m = inv.getArgument(0);
            m.setId(3L);
            return m;
        });
        MemoireResponse saved = service.create(
                MemoireRequest.builder().studentId(1L).subject("Réseaux").status("EN_REDACTION").build(), httpRequest);
        assertThat(saved.getId()).isEqualTo(3L);
        assertThat(saved.getSubject()).isEqualTo("Réseaux");
        assertThat(saved.getStudentName()).isEqualTo("Awa Diop");
        assertThat(saved.getMatricule()).isEqualTo("ETU-1");
    }

    @Test
    void createThrowsWhenStudentNotFound() {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());
        MemoireRequest request = MemoireRequest.builder().studentId(99L).subject("Réseaux").build();
        assertThatThrownBy(() -> service.create(request, httpRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateCompletesDefenseInformation() {
        Memoire existing = Memoire.builder().id(7L).student(student(1L)).subject("Réseaux").build();
        when(memoireRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(memoireRepository.save(any(Memoire.class))).thenAnswer(inv -> inv.getArgument(0));
        MemoireResponse saved = service.update(7L, MemoireRequest.builder()
                .subject("Réseaux")
                .grade(new BigDecimal("15.5"))
                .decision("Admis")
                .status("VALIDEE")
                .build(), httpRequest);
        assertThat(saved.getGrade()).isEqualByComparingTo("15.5");
        assertThat(saved.getDecision()).isEqualTo("Admis");
        assertThat(saved.getStatus()).isEqualTo("VALIDEE");
    }

    @Test
    void updateThrowsWhenMemoireNotFound() {
        when(memoireRepository.findById(404L)).thenReturn(Optional.empty());
        MemoireRequest request = MemoireRequest.builder().subject("Réseaux").build();
        assertThatThrownBy(() -> service.update(404L, request, httpRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
