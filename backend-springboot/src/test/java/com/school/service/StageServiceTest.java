package com.school.service;

import com.school.dto.request.StageRequest;
import com.school.dto.response.StageResponse;
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

    private StageRequest request(Long studentId) {
        return StageRequest.builder()
                .studentId(studentId)
                .company("TechCorp")
                .subject("Développement web")
                .status("EN_COURS")
                .build();
    }

    private Stage stageEntity(Long studentId) {
        return Stage.builder().id(1L).company("TechCorp").student(student(studentId))
                .subject("Développement web").status("EN_COURS").build();
    }

    @Test
    void createRejectsBlankCompany() {
        StageRequest request = StageRequest.builder().studentId(1L).company("  ").build();
        assertThatThrownBy(() -> service.create(request, httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("entreprise");
    }

    @Test
    void createRejectsMissingStudent() {
        StageRequest request = StageRequest.builder().company("TechCorp").build();
        assertThatThrownBy(() -> service.create(request, httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("étudiant");
    }

    @Test
    void createSavesValidStageAndExposesStudent() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student(1L)));
        when(stageRepository.save(any(Stage.class))).thenAnswer(inv -> {
            Stage s = inv.getArgument(0);
            s.setId(20L);
            return s;
        });
        StageResponse saved = service.create(request(1L), httpRequest);
        assertThat(saved.getId()).isEqualTo(20L);
        assertThat(saved.getCompany()).isEqualTo("TechCorp");
        assertThat(saved.getStudentName()).isEqualTo("Koffi Konan");
        assertThat(saved.getMatricule()).isEqualTo("ETU-1");
        verify(stageRepository).save(any(Stage.class));
        verify(auditService).log(any(), any(), any(), any(), any());
    }

    @Test
    void createThrowsWhenStudentNotFound() {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());
        StageRequest request = request(99L);
        assertThatThrownBy(() -> service.create(request, httpRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateSetsIdAndSaves() {
        when(studentRepository.findById(1L)).thenReturn(Optional.of(student(1L)));
        when(stageRepository.findById(5L)).thenReturn(Optional.of(stageEntity(1L)));
        when(stageRepository.save(any(Stage.class))).thenAnswer(inv -> inv.getArgument(0));
        StageResponse saved = service.update(5L, request(1L), httpRequest);
        assertThat(saved.getId()).isEqualTo(5L);
        assertThat(saved.getCompany()).isEqualTo("TechCorp");
        verify(stageRepository).save(any(Stage.class));
    }

    @Test
    void listByStudentReturnsStages() {
        when(stageRepository.findByStudentIdOrderByStartDateDesc(1L)).thenReturn(List.of(stageEntity(1L)));
        assertThat(service.listByStudent(1L)).hasSize(1);
    }

    @Test
    void deleteExisting() {
        Stage stage = stageEntity(1L);
        when(stageRepository.findById(5L)).thenReturn(Optional.of(stage));
        service.delete(5L, httpRequest);
        verify(stageRepository).delete(stage);
    }
}
