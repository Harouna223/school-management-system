package com.school.service;

import com.school.dto.request.AlumnusRequest;
import com.school.dto.response.AlumnusResponse;
import com.school.entity.Alumnus;
import com.school.entity.Student;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.AlumnusRepository;
import com.school.repository.StudentRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests du module diplômés (alumni).
 */
@ExtendWith(MockitoExtension.class)
class AlumnusServiceTest {

    @Mock
    private AlumnusRepository alumnusRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private HttpServletRequest httpRequest;

    private AlumnusService service;

    @BeforeEach
    void setUp() {
        service = new AlumnusService(alumnusRepository, studentRepository, auditService);
    }

    @Test
    void createExigePrenomEtNom() {
        AlumnusRequest request = AlumnusRequest.builder().firstName("  ").lastName("Ndoye").build();
        assertThatThrownBy(() -> service.create(request, httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("prénom");
    }

    @Test
    void createEnregistreUnDiplomeSaisiALaMain() {
        when(alumnusRepository.save(any(Alumnus.class))).thenAnswer(inv -> {
            Alumnus a = inv.getArgument(0);
            a.setId(5L);
            return a;
        });
        AlumnusResponse saved = service.create(AlumnusRequest.builder()
                .firstName(" Jean ")
                .lastName("Ndoye")
                .fieldName("Génie logiciel")
                .currentJob("Développeur")
                .build(), httpRequest);
        assertThat(saved.getId()).isEqualTo(5L);
        assertThat(saved.getFirstName()).isEqualTo("Jean");
        assertThat(saved.getStudentId()).isNull();
        assertThat(saved.getCurrentJob()).isEqualTo("Développeur");
    }

    @Test
    void updateRattacheLEleveEtActualiseLeParcours() {
        Alumnus existing = Alumnus.builder().id(9L).firstName("Jean").lastName("Ndoye").build();
        when(alumnusRepository.findById(9L)).thenReturn(Optional.of(existing));
        when(studentRepository.findById(1L)).thenReturn(Optional.of(
                Student.builder().id(1L).firstName("Jean").lastName("Ndoye").matricule("ETU-1").build()));
        when(alumnusRepository.save(any(Alumnus.class))).thenAnswer(inv -> inv.getArgument(0));
        AlumnusResponse saved = service.update(9L, AlumnusRequest.builder()
                .studentId(1L)
                .firstName("Jean")
                .lastName("Ndoye")
                .company("Sahel Tech")
                .build(), httpRequest);
        assertThat(saved.getStudentId()).isEqualTo(1L);
        assertThat(saved.getCompany()).isEqualTo("Sahel Tech");
    }

    @Test
    void updateRejetteUnDiplomeInconnu() {
        when(alumnusRepository.findById(404L)).thenReturn(Optional.empty());
        AlumnusRequest request = AlumnusRequest.builder().firstName("Jean").lastName("Ndoye").build();
        assertThatThrownBy(() -> service.update(404L, request, httpRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateRejetteUnEleveInconnu() {
        when(alumnusRepository.findById(9L)).thenReturn(Optional.of(Alumnus.builder().id(9L).build()));
        when(studentRepository.findById(404L)).thenReturn(Optional.empty());
        AlumnusRequest request = AlumnusRequest.builder()
                .studentId(404L).firstName("Jean").lastName("Ndoye").build();
        assertThatThrownBy(() -> service.update(9L, request, httpRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
