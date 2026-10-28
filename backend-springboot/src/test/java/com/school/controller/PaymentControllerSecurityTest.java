package com.school.controller;

import com.school.dto.response.PageResponse;
import com.school.dto.response.PaymentResponse;
import com.school.entity.Parent;
import com.school.entity.Role;
import com.school.entity.Student;
import com.school.entity.User;
import com.school.repository.ParentRepository;
import com.school.repository.StudentRepository;
import com.school.service.AccessControlService;
import com.school.service.FinanceService;
import com.school.service.ReportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de la fuite horizontale du module paiements.
 *
 * <p>Régression corrigée : {@code GET /api/payments} n'appliquait la garde que
 * si {@code studentId} était fourni. Un compte PARENT ou ELEVE appelant
 * l'endpoint SANS filtre recevait donc les paiements de tout l'établissement
 * (et {@code /receipt/pdf} n'avait aucun contrôle du tout).</p>
 *
 * <p>Ces tests utilisent le VRAI {@link AccessControlService} (seuls ses
 * dépôts sont mockés) : ils échouent si la garde disparaît, contrairement à un
 * test où le service serait entièrement mocké.</p>
 */
@ExtendWith(MockitoExtension.class)
class PaymentControllerSecurityTest {

    @Mock private FinanceService financeService;
    @Mock private ReportService reportService;
    @Mock private StudentRepository studentRepository;
    @Mock private ParentRepository parentRepository;

    private PaymentController controller;

    @BeforeEach
    void setUp() {
        AccessControlService accessControl = new AccessControlService(studentRepository, parentRepository);
        controller = new PaymentController(financeService, reportService, accessControl);
        // lenient : le stub n'est sollicité que par les tests qui atteignent le service.
        org.mockito.Mockito.lenient().when(financeService.searchPayments(
                        any(AccessControlService.StudentScope.class),
                        any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(PageResponse.<PaymentResponse>builder().content(List.of()).build());
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authentifier(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private User utilisateur(Long id, String role) {
        Role r = Role.builder().name(role).build();
        return User.builder().id(id).username("user" + id)
                .roles(new java.util.HashSet<>(Set.of(r))).build();
    }

    @Test
    @DisplayName("PARENT sans filtre : la requête est bornée à SES enfants, jamais à l'établissement")
    void parentSansFiltreEstBorneASesEnfants() {
        User parentUser = utilisateur(1L, "PARENT");
        Parent dossier = Parent.builder().id(10L).user(parentUser).build();
        Student enfant = Student.builder().id(20L).firstName("Awa").lastName("Diop").parent(dossier).build();
        when(parentRepository.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(dossier));
        when(studentRepository.findByParentIdIn(List.of(10L))).thenReturn(List.of(enfant));
        authentifier(parentUser);

        controller.searchPayments(null, null, null, null, 0, 10);

        ArgumentCaptor<AccessControlService.StudentScope> captor =
                ArgumentCaptor.forClass(AccessControlService.StudentScope.class);
        verify(financeService).searchPayments(captor.capture(), isNull(), isNull(), isNull(), eq(0), eq(10));
        assertThat(captor.getValue().unrestricted()).isFalse();
        assertThat(captor.getValue().studentIds()).containsExactly(20L);
    }

    @Test
    @DisplayName("PARENT demandant un élève étranger : refus (pas de fuite par paramètre)")
    void parentNePeutPasCiblerUnAutreEleve() {
        User parentUser = utilisateur(1L, "PARENT");
        Parent dossier = Parent.builder().id(10L).user(parentUser).build();
        Parent autreDossier = Parent.builder().id(20L).user(utilisateur(2L, "PARENT")).build();
        Student enfantEtranger = Student.builder().id(30L).parent(autreDossier).build();
        when(parentRepository.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(dossier));
        when(studentRepository.findById(30L)).thenReturn(Optional.of(enfantEtranger));
        authentifier(parentUser);

        assertThatThrownBy(() -> controller.searchPayments(30L, null, null, null, 0, 10))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Accès refusé");
    }

    @Test
    @DisplayName("ELEVE sans filtre : borné à son propre profil")
    void eleveSansFiltreEstBorneASonProfil() {
        User eleve = utilisateur(5L, "ELEVE");
        Student profil = Student.builder().id(50L).firstName("Moussa").lastName("Ba").build();
        when(studentRepository.findByUserId(5L)).thenReturn(Optional.of(profil));
        authentifier(eleve);

        controller.searchInvoices(null, null, 0, 10);

        ArgumentCaptor<AccessControlService.StudentScope> captor =
                ArgumentCaptor.forClass(AccessControlService.StudentScope.class);
        verify(financeService).searchInvoices(captor.capture(), isNull(), eq(0), eq(10));
        assertThat(captor.getValue().unrestricted()).isFalse();
        assertThat(captor.getValue().studentIds()).containsExactly(50L);
    }

    @Test
    @DisplayName("SECRETAIRE sans filtre : accès complet (aucune régression pour le personnel)")
    void personnelConserveLAccesComplet() {
        authentifier(utilisateur(9L, "SECRETAIRE"));

        controller.searchPayments(null, null, null, null, 0, 10);

        ArgumentCaptor<AccessControlService.StudentScope> captor =
                ArgumentCaptor.forClass(AccessControlService.StudentScope.class);
        verify(financeService).searchPayments(captor.capture(), isNull(), isNull(), isNull(), eq(0), eq(10));
        assertThat(captor.getValue().unrestricted()).isTrue();
    }

    @Test
    @DisplayName("le reçu PDF d'un paiement d'un autre élève est refusé à un parent")
    void recuPdfDunAutreEleveRefuse() {
        User parentUser = utilisateur(1L, "PARENT");
        Parent dossier = Parent.builder().id(10L).user(parentUser).build();
        Parent autreDossier = Parent.builder().id(20L).user(utilisateur(2L, "PARENT")).build();
        when(parentRepository.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(dossier));
        when(studentRepository.findById(75L))
                .thenReturn(Optional.of(Student.builder().id(75L).parent(autreDossier).build()));
        when(financeService.getPayment(42L)).thenReturn(PaymentResponse.builder()
                .id(42L).studentId(75L).receiptNo("RCP-1").amount(java.math.BigDecimal.TEN).build());
        authentifier(parentUser);

        assertThatThrownBy(() -> controller.receiptPdf(42L, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Accès refusé");
    }
}
