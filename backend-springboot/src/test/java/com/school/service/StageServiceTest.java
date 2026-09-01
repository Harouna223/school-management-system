package com.school.service;

import com.school.entity.Stage;
import com.school.entity.Student;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.StageRepository;
import com.school.repository.StudentRepository;
import com.school.repository.TeacherRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests du module stages universitaires.
 */
@ExtendWith(MockitoExtension.class)
class StageServiceTest {

    @Mock
    private StageRepository stageRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private TeacherRepository teacherRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private HttpServletRequest httpRequest;

    private StageService service;

    @BeforeEach
    void setUp() {
        service = new StageService(stageRepository, studentRepository, teacherRepository, auditService);
    }

    private Student student(Long id) {
        return Student.builder().id(id).firstName("Koffi").lastName("Konan").matricule("ETU-" + id).build();
    }

    private Stage stage(Long studentId) {
        return Stage.builder().company("TechCorp").student(student(studentId))
                .subject("Développement web").status("EN_COURS").build();
    }

    @Test
    void createRejectsBlankCompany() {
        Stage stage = Stage.builder().company("  ").student(student(1L)).build();
        assertThatThrownBy(() -> service.create(stage, httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("entreprise");
    }

    @Test
    void createRejectsMissingStudent() {
        Stage stage = Stage.builder().company("TechCorp").build();
        assertThatThrownBy(() -> service.create(stage, httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("étudiant");
    }

    @Test
    void createSavesValidStage() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student(1L)));
        when(stageRepository.save(any(Stage.class))).thenAnswer(inv -> {
            Stage s = inv.getArgument(0);
            s.setId(20L);
            return s;
        });
        Stage saved = service.create(stage(1L), httpRequest);
        assertThat(saved.getId()).isEqualTo(20L);
        assertThat(saved.getCompany()).isEqualTo("TechCorp");
        verify(stageRepository).save(any(Stage.class));
        verify(auditService).log(any(), any(), any(), any(), any());
    }

    @Test
    void createThrowsWhenStudentNotFound() {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(stage(99L), httpRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateSetsIdAndSaves() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student(1L)));
        when(stageRepository.findById(5L)).thenReturn(Optional.of(stage(1L)));
        when(stageRepository.save(any(Stage.class))).thenAnswer(inv -> inv.getArgument(0));
        Stage updated = Stage.builder().company("NewCorp").student(student(1L)).status("TERMINE")
                .grade(new BigDecimal("16")).build();
        Stage saved = service.update(5L, updated, httpRequest);
        assertThat(saved.getId()).isEqualTo(5L);
        assertThat(saved.getCompany()).isEqualTo("NewCorp");
        verify(stageRepository).save(any(Stage.class));
    }

    @Test
    void listByStudentReturnsStages() {
        when(stageRepository.findByStudentIdOrderByStartDateDesc(1L)).thenReturn(List.of(stage(1L)));
        assertThat(service.listByStudent(1L)).hasSize(1);
    }

    @Test
    void deleteExisting() {
        Stage stage = stage(1L);
        when(stageRepository.findById(5L)).thenReturn(Optional.of(stage));
        service.delete(5L, httpRequest);
        verify(stageRepository).delete(stage);
    }
}