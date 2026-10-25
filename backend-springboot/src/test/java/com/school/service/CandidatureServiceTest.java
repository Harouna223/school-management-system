package com.school.service;

import com.school.dto.request.AdmissionEnrollmentRequest;
import com.school.dto.request.CandidatureRequest;
import com.school.dto.request.StudentRequest;
import com.school.dto.response.AdmissionEnrollmentResponse;
import com.school.dto.response.CandidatureResponse;
import com.school.dto.response.StudentResponse;
import com.school.entity.Candidature;
import com.school.entity.Student;
import com.school.enums.CandidatureStatus;
import com.school.enums.Gender;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.CandidatureRepository;
import com.school.repository.StudentRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests du module admission (candidatures et inscription).
 */
@ExtendWith(MockitoExtension.class)
class CandidatureServiceTest {

    @Mock
    private CandidatureRepository candidatureRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private StudentService studentService;
    @Mock
    private NumberSequenceService numberSequenceService;
    @Mock
    private AuditService auditService;
    @Mock
    private HttpServletRequest httpRequest;

    private CandidatureService service;

    @BeforeEach
    void setUp() {
        service = new CandidatureService(candidatureRepository, studentRepository, studentService,
                numberSequenceService, auditService);
    }

    private CandidatureRequest request() {
        return CandidatureRequest.builder().firstName(" Awa ").lastName("Diop").fieldName(" Gestion ").build();
    }

    private Candidature candidature(CandidatureStatus status) {
        return Candidature.builder().id(3L).firstName("Awa").lastName("Diop")
                .fieldName("Gestion").reference("CAND-000042").status(status).build();
    }

    private AdmissionEnrollmentRequest enrollmentRequest() {
        return AdmissionEnrollmentRequest.builder().gender(Gender.FEMALE).build();
    }

    @Test
    void createUtiliseLeCompteurPersisteEtForceLeStatutInitial() {
        when(numberSequenceService.next("CAND-")).thenReturn(42L);
        when(candidatureRepository.save(any(Candidature.class))).thenAnswer(inv -> {
            Candidature c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });
        CandidatureResponse saved = service.create(request(), httpRequest);
        // La référence vient du compteur verrouillé, plus d'un AtomicLong en mémoire.
        assertThat(saved.getReference()).isEqualTo("CAND-000042");
        assertThat(saved.getStatus()).isEqualTo(CandidatureStatus.EN_ATTENTE);
        assertThat(saved.getFirstName()).isEqualTo("Awa");
        assertThat(saved.getFieldName()).isEqualTo("Gestion");
    }

    @Test
    void updateStatusEnregistreLeNouveauStatut() {
        Candidature existing = candidature(CandidatureStatus.EN_ATTENTE);
        when(candidatureRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(candidatureRepository.save(any(Candidature.class))).thenAnswer(inv -> inv.getArgument(0));
        CandidatureResponse saved = service.updateStatus(3L, CandidatureStatus.ACCEPTEE, httpRequest);
        assertThat(saved.getStatus()).isEqualTo(CandidatureStatus.ACCEPTEE);
    }

    @Test
    void updateStatusRejetteUneCandidatureInconnue() {
        when(candidatureRepository.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateStatus(404L, CandidatureStatus.REFUSEE, httpRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteRejetteUneCandidatureInconnue() {
        when(candidatureRepository.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(404L, httpRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- Inscription ----------

    @Test
    void enrollCreeLEleveDepuisLeDossierEtRattacheLaCandidature() {
        Candidature existing = candidature(CandidatureStatus.ACCEPTEE);
        when(candidatureRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(studentService.create(any(StudentRequest.class), any()))
                .thenReturn(StudentResponse.builder().id(55L).matricule("ETU-2026-000055")
                        .firstName("Awa").lastName("Diop").build());
        when(studentRepository.findById(55L)).thenReturn(Optional.of(
                Student.builder().id(55L).matricule("ETU-2026-000055").firstName("Awa").lastName("Diop").build()));
        when(candidatureRepository.save(any(Candidature.class))).thenAnswer(inv -> inv.getArgument(0));

        AdmissionEnrollmentResponse response = service.enroll(3L, enrollmentRequest(), httpRequest);

        assertThat(response.getStudent().getMatricule()).isEqualTo("ETU-2026-000055");
        assertThat(response.getCandidature().getStatus()).isEqualTo(CandidatureStatus.INSCRITE);
        assertThat(response.getCandidature().getStudentId()).isEqualTo(55L);
        assertThat(response.getCandidature().getMatricule()).isEqualTo("ETU-2026-000055");

        // Les données civiles du dossier sont reprises, le genre vient du formulaire.
        ArgumentCaptor<StudentRequest> captor = ArgumentCaptor.forClass(StudentRequest.class);
        verify(studentService).create(captor.capture(), any());
        assertThat(captor.getValue().getFirstName()).isEqualTo("Awa");
        assertThat(captor.getValue().getLastName()).isEqualTo("Diop");
        assertThat(captor.getValue().getGender()).isEqualTo(Gender.FEMALE);
    }

    @Test
    void enrollRefuseUneCandidatureDejaInscrite() {
        Candidature existing = candidature(CandidatureStatus.INSCRITE);
        existing.setStudent(Student.builder().id(55L).matricule("ETU-2026-000055").build());
        when(candidatureRepository.findById(3L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.enroll(3L, enrollmentRequest(), httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("déjà été inscrite");
        verifyNoInteractions(studentService);
    }

    @Test
    void enrollRefuseUneCandidatureNonAcceptee() {
        when(candidatureRepository.findById(3L)).thenReturn(Optional.of(candidature(CandidatureStatus.EN_ATTENTE)));

        assertThatThrownBy(() -> service.enroll(3L, enrollmentRequest(), httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("acceptée");
        verifyNoInteractions(studentService);
    }

    @Test
    void enrollRejetteUneCandidatureInconnue() {
        when(candidatureRepository.findById(404L)).thenReturn(Optional.empty());
        AdmissionEnrollmentRequest request = enrollmentRequest();
        assertThatThrownBy(() -> service.enroll(404L, request, httpRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteRefuseUneCandidatureDejaInscrite() {
        Candidature existing = candidature(CandidatureStatus.INSCRITE);
        existing.setStudent(Student.builder().id(55L).matricule("ETU-2026-000055").build());
        when(candidatureRepository.findById(3L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.delete(3L, httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inscrite");
        verify(candidatureRepository, never()).delete(any(Candidature.class));
    }
}
