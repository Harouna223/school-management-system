package com.school.service;

import com.school.dto.request.CandidatureRequest;
import com.school.dto.response.CandidatureResponse;
import com.school.entity.Candidature;
import com.school.enums.CandidatureStatus;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.CandidatureRepository;
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
 * Tests du module admission (candidatures).
 */
@ExtendWith(MockitoExtension.class)
class CandidatureServiceTest {

    @Mock
    private CandidatureRepository candidatureRepository;
    @Mock
    private NumberSequenceService numberSequenceService;
    @Mock
    private AuditService auditService;
    @Mock
    private HttpServletRequest httpRequest;

    private CandidatureService service;

    @BeforeEach
    void setUp() {
        service = new CandidatureService(candidatureRepository, numberSequenceService, auditService);
    }

    private CandidatureRequest request() {
        return CandidatureRequest.builder().firstName(" Awa ").lastName("Diop").fieldName(" Gestion ").build();
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
        Candidature existing = Candidature.builder().id(3L).firstName("Awa").lastName("Diop")
                .fieldName("Gestion").reference("CAND-000042").build();
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
}
