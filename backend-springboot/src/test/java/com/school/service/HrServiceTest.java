package com.school.service;

import com.school.dto.request.ContractRequest;
import com.school.dto.request.LeaveRequest;
import com.school.dto.request.PayrollGenerateRequest;
import com.school.dto.response.ContractResponse;
import com.school.dto.response.LeaveResponse;
import com.school.dto.response.PayrollResponse;
import com.school.entity.Contract;
import com.school.entity.Leave;
import com.school.entity.Payroll;
import com.school.entity.Teacher;
import com.school.entity.User;
import com.school.enums.ContractStatus;
import com.school.enums.ContractType;
import com.school.enums.Gender;
import com.school.enums.LeaveStatus;
import com.school.enums.LeaveType;
import com.school.enums.PayrollStatus;
import com.school.enums.TeacherStatus;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.ContractRepository;
import com.school.repository.LeaveRepository;
import com.school.repository.PayrollRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Module RH : conges, contrats, paie.
 *
 * Ce service manipule de l'argent. Les invariants les plus importants ne concernent pas
 * le calcul lui-meme mais les <b>transitions d'etat</b> :
 * <ul>
 *   <li>un bulletin de paie ne se genere qu'une fois par mois et par enseignant ;</li>
 *   <li>un bulletin deja paye ne peut plus etre repayé ;</li>
 *   <li>l'approbation d'un conge bascule le statut de l'enseignant, le rejet non ;</li>
 *   <li>le salaire de base vient du dernier contrat ACTIF, pas d'un contrat expire.</li>
 * </ul>
 * Aucune de ces regles ne casse la compilation ni les tests existants : d'ou ce fichier.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HrServiceTest {

    @Mock
    private LeaveRepository leaveRepository;

    @Mock
    private TeacherService teacherService;

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private PayrollRepository payrollRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private HrService hrService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ helpers

    private Teacher enseignant(Long id) {
        return Teacher.builder()
                .id(id)
                .employeeNo("ENS-2026-0001")
                .firstName("Jean")
                .lastName("Kamdem")
                .gender(Gender.MALE)
                .hireDate(LocalDate.of(2026, 1, 1))
                .contractType(ContractType.CDI)
                .salary(new BigDecimal("250000.00"))
                .status(TeacherStatus.ACTIVE)
                .build();
    }

    private Leave conge(Long id, Teacher t, LeaveStatus statut) {
        return Leave.builder()
                .id(id)
                .teacher(t)
                .type(LeaveType.ANNUAL)
                .startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2026, 10, 5))
                .reason("Conges annuels")
                .status(statut)
                .build();
    }

    private Contract contrat(Long id, Teacher t, ContractStatus statut, BigDecimal salaire, LocalDate debut) {
        return Contract.builder()
                .id(id)
                .teacher(t)
                .type(ContractType.CDI)
                .startDate(debut)
                .baseSalary(salaire)
                .status(statut)
                .build();
    }

    private Payroll bulletin(Long id, Teacher t, PayrollStatus statut) {
        return Payroll.builder()
                .id(id)
                .teacher(t)
                .monthDate(LocalDate.of(2026, 10, 1))
                .baseSalary(new BigDecimal("250000.00"))
                .allowances(BigDecimal.ZERO)
                .deductions(BigDecimal.ZERO)
                .netSalary(new BigDecimal("250000.00"))
                .status(statut)
                .build();
    }

    private LeaveRequest demandeDeConge(Teacher t, LocalDate debut, LocalDate fin) {
        return LeaveRequest.builder()
                .teacherId(t.getId())
                .type(LeaveType.ANNUAL)
                .startDate(debut)
                .endDate(fin)
                .reason("Conges annuels")
                .build();
    }

    private void connecte(User utilisateur) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(utilisateur, null, List.of()));
    }

    // ------------------------------------------------------------------- conges

    @Test
    @DisplayName("requestLeave cree toujours une demande PENDING, jamais approuvee d'emblee")
    void requestLeaveCreeUneDemandeEnAttente() {
        Teacher t = enseignant(1L);
        when(teacherService.findById(1L)).thenReturn(t);
        when(leaveRepository.save(any(Leave.class))).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<Leave> captor = ArgumentCaptor.forClass(Leave.class);
        LeaveResponse reponse = hrService.requestLeave(
                demandeDeConge(t, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5)), httpRequest);
        verify(leaveRepository).save(captor.capture());

        // Une demande auto-approuvee contournerait toute la validation RH.
        assertThat(captor.getValue().getStatus()).isEqualTo(LeaveStatus.PENDING);
        assertThat(reponse.getStatus()).isEqualTo(LeaveStatus.PENDING);
        assertThat(captor.getValue().getTeacher()).isSameAs(t);
    }

    @Test
    @DisplayName("requestLeave refuse une date de fin anterieure a la date de debut")
    void requestLeaveRefuseUnePeriodeInversee() {
        Teacher t = enseignant(1L);
        LeaveRequest requete = demandeDeConge(t, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 1));

        assertThatThrownBy(() -> hrService.requestLeave(requete, httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("postérieure");

        // Rien ne doit etre ecrit ni journalise quand la demande est invalide.
        verifyNoInteractions(leaveRepository);
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("requestLeave accepte une demande d'un seul jour (debut == fin)")
    void requestLeaveAccepteUnJourUnique() {
        Teacher t = enseignant(1L);
        when(teacherService.findById(1L)).thenReturn(t);
        when(leaveRepository.save(any(Leave.class))).thenAnswer(inv -> inv.getArgument(0));
        LocalDate jour = LocalDate.of(2026, 10, 1);

        LeaveResponse reponse = hrService.requestLeave(demandeDeConge(t, jour, jour), httpRequest);

        assertThat(reponse.getStartDate()).isEqualTo(jour);
        assertThat(reponse.getEndDate()).isEqualTo(jour);
    }

    @Test
    @DisplayName("requestLeave journalise une action LEAVE")
    void requestLeaveJournalise() {
        Teacher t = enseignant(1L);
        when(teacherService.findById(1L)).thenReturn(t);
        when(leaveRepository.save(any(Leave.class))).thenAnswer(inv -> {
            Leave l = inv.getArgument(0);
            l.setId(42L);
            return l;
        });

        hrService.requestLeave(demandeDeConge(t, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5)), httpRequest);

        verify(auditService).log(eq("LEAVE"), eq("Leave"), eq(42L), any(), eq(httpRequest));
    }

    @Test
    @DisplayName("decide APPROVED bascule aussi le statut de l'enseignant en ON_LEAVE")
    void approuverUnCongeBasculeLEnseignantEnConge() {
        Teacher t = enseignant(1L);
        Leave conge = conge(9L, t, LeaveStatus.PENDING);
        User decideur = User.builder().id(77L).username("rh").build();
        connecte(decideur);
        when(leaveRepository.findById(9L)).thenReturn(Optional.of(conge));
        when(leaveRepository.save(any(Leave.class))).thenAnswer(inv -> inv.getArgument(0));

        LeaveResponse reponse = hrService.decide(9L, LeaveStatus.APPROVED, httpRequest);

        assertThat(reponse.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        // L'effet de bord metier : sans cela l'enseignant reste marque actif pendant son conge.
        verify(teacherService).updateStatus(1L, TeacherStatus.ON_LEAVE, httpRequest);
        assertThat(conge.getApprovedBy()).isSameAs(decideur);
    }

    @Test
    @DisplayName("decide REJECTED ne touche PAS au statut de l'enseignant")
    void rejeterUnCongeNeBasculePasLEnseignant() {
        Teacher t = enseignant(1L);
        Leave conge = conge(9L, t, LeaveStatus.PENDING);
        connecte(User.builder().id(77L).username("rh").build());
        when(leaveRepository.findById(9L)).thenReturn(Optional.of(conge));
        when(leaveRepository.save(any(Leave.class))).thenAnswer(inv -> inv.getArgument(0));

        LeaveResponse reponse = hrService.decide(9L, LeaveStatus.REJECTED, httpRequest);

        assertThat(reponse.getStatus()).isEqualTo(LeaveStatus.REJECTED);
        // Un rejet ne doit surtout pas mettre l'enseignant en conge.
        verify(teacherService, never()).updateStatus(anyLong(), any(), any());
    }

    @Test
    @DisplayName("decide refuse une decision autre que APPROVED ou REJECTED")
    void decideRefuseUneDecisionInvalide() {
        Leave conge = conge(9L, enseignant(1L), LeaveStatus.PENDING);
        when(leaveRepository.findById(9L)).thenReturn(Optional.of(conge));

        assertThatThrownBy(() -> hrService.decide(9L, LeaveStatus.PENDING, httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("APPROVED ou REJECTED");

        verify(leaveRepository, never()).save(any(Leave.class));
        verifyNoInteractions(teacherService);
    }

    @Test
    @DisplayName("decide sur un conge inconnu leve une exception nommant « Congé »")
    void decideSurUnCongeInconnuLeveUneException() {
        when(leaveRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hrService.decide(404L, LeaveStatus.APPROVED, httpRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Congé")
                .hasMessageContaining("404");
    }

    @Test
    @DisplayName("listLeaves transmet les filtres au repository")
    void listLeavesTransmetLesFiltres() {
        when(leaveRepository.search(1L, LeaveStatus.PENDING))
                .thenReturn(List.of(conge(9L, enseignant(1L), LeaveStatus.PENDING)));

        List<LeaveResponse> resultat = hrService.listLeaves(1L, LeaveStatus.PENDING);

        assertThat(resultat).hasSize(1);
        assertThat(resultat.get(0).getTeacherName()).isEqualTo("Jean Kamdem");
        assertThat(resultat.get(0).getTeacherId()).isEqualTo(1L);
    }

    // ----------------------------------------------------------------- contrats

    @Test
    @DisplayName("createContract deduit le statut : date de fin passee => EXPIRED")
    void createContractDeduitExpired() {
        Teacher t = enseignant(1L);
        when(teacherService.findById(1L)).thenReturn(t);
        when(contractRepository.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));
        ContractRequest requete = ContractRequest.builder()
                .teacherId(1L)
                .type(ContractType.CDD)
                .startDate(LocalDate.now().minusYears(2))
                .endDate(LocalDate.now().minusDays(1))
                .baseSalary(new BigDecimal("200000.00"))
                .build();

        ArgumentCaptor<Contract> captor = ArgumentCaptor.forClass(Contract.class);
        ContractResponse reponse = hrService.createContract(requete, httpRequest);
        verify(contractRepository).save(captor.capture());

        assertThat(captor.getValue().getStatus()).isEqualTo(ContractStatus.EXPIRED);
        assertThat(reponse.getStatus()).isEqualTo(ContractStatus.EXPIRED);
    }

    @Test
    @DisplayName("createContract deduit le statut : contrat sans terme => ACTIVE")
    void createContractDeduitActive() {
        Teacher t = enseignant(1L);
        when(teacherService.findById(1L)).thenReturn(t);
        when(contractRepository.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));
        ContractRequest requete = ContractRequest.builder()
                .teacherId(1L)
                .type(ContractType.CDI)
                .startDate(LocalDate.now())
                .baseSalary(new BigDecimal("300000.00"))
                .build();

        ContractResponse reponse = hrService.createContract(requete, httpRequest);

        assertThat(reponse.getStatus()).isEqualTo(ContractStatus.ACTIVE);
    }

    @Test
    @DisplayName("createContract respecte un statut fourni explicitement")
    void createContractRespecteUnStatutExplicite() {
        Teacher t = enseignant(1L);
        when(teacherService.findById(1L)).thenReturn(t);
        when(contractRepository.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));
        ContractRequest requete = ContractRequest.builder()
                .teacherId(1L)
                .type(ContractType.CDD)
                .startDate(LocalDate.now().minusYears(2))
                .endDate(LocalDate.now().minusDays(1))
                .baseSalary(new BigDecimal("200000.00"))
                .status(ContractStatus.TERMINATED)
                .build();

        ContractResponse reponse = hrService.createContract(requete, httpRequest);

        // Le statut explicite prime sur la deduction automatique.
        assertThat(reponse.getStatus()).isEqualTo(ContractStatus.TERMINATED);
    }

    @Test
    @DisplayName("createContract refuse une date de fin anterieure a la date de debut")
    void createContractRefuseUnePeriodeInversee() {
        Teacher t = enseignant(1L);
        ContractRequest requete = ContractRequest.builder()
                .teacherId(1L)
                .type(ContractType.CDD)
                .startDate(LocalDate.of(2026, 10, 5))
                .endDate(LocalDate.of(2026, 10, 1))
                .baseSalary(new BigDecimal("200000.00"))
                .build();

        assertThatThrownBy(() -> hrService.createContract(requete, httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("postérieure");

        verifyNoInteractions(contractRepository);
        // L'enseignant ne doit meme pas etre charge si la requete est invalide.
        verifyNoInteractions(teacherService);
    }

    @Test
    @DisplayName("createContract journalise le type et le nom de l'enseignant")
    void createContractJournalise() {
        Teacher t = enseignant(1L);
        when(teacherService.findById(1L)).thenReturn(t);
        when(contractRepository.save(any(Contract.class))).thenAnswer(inv -> {
            Contract c = inv.getArgument(0);
            c.setId(5L);
            return c;
        });
        ContractRequest requete = ContractRequest.builder()
                .teacherId(1L)
                .type(ContractType.CDI)
                .startDate(LocalDate.now())
                .baseSalary(new BigDecimal("300000.00"))
                .build();

        hrService.createContract(requete, httpRequest);

        verify(auditService).log(eq("CONTRACT"), eq("Contract"), eq(5L),
                contains("Jean Kamdem"), eq(httpRequest));
    }

    @Test
    @DisplayName("updateContract ecrase bien tous les champs modifiables")
    void updateContractEcraseLesChamps() {
        Teacher t = enseignant(1L);
        Contract existant = contrat(5L, t, ContractStatus.ACTIVE, new BigDecimal("100000.00"), LocalDate.now());
        when(contractRepository.findById(5L)).thenReturn(Optional.of(existant));
        when(contractRepository.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));
        ContractRequest requete = ContractRequest.builder()
                .teacherId(1L)
                .type(ContractType.VACATAIRE)
                .startDate(LocalDate.of(2026, 9, 1))
                .baseSalary(new BigDecimal("450000.00"))
                .description("Nouvelle mission")
                .build();

        ContractResponse reponse = hrService.updateContract(5L, requete, httpRequest);

        assertThat(reponse.getBaseSalary()).isEqualByComparingTo("450000.00");
        assertThat(reponse.getType()).isEqualTo(ContractType.VACATAIRE);
        assertThat(reponse.getDescription()).isEqualTo("Nouvelle mission");
        assertThat(reponse.getStatus()).isEqualTo(ContractStatus.ACTIVE);
    }

    @Test
    @DisplayName("updateContract peut faire repasser le salaire a la baisse (pas de garde intempestive)")
    void updateContractAccepteUneBaisseDeSalaire() {
        Teacher t = enseignant(1L);
        Contract existant = contrat(5L, t, ContractStatus.ACTIVE, new BigDecimal("450000.00"), LocalDate.now());
        when(contractRepository.findById(5L)).thenReturn(Optional.of(existant));
        when(contractRepository.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));
        ContractRequest requete = ContractRequest.builder()
                .teacherId(1L)
                .type(ContractType.CDI)
                .startDate(LocalDate.now())
                .baseSalary(new BigDecimal("120000.00"))
                .build();

        ContractResponse reponse = hrService.updateContract(5L, requete, httpRequest);

        assertThat(reponse.getBaseSalary()).isEqualByComparingTo("120000.00");
    }

    @Test
    @DisplayName("updateContract refuse une periode inversee et n'ecrit rien")
    void updateContractRefuseUnePeriodeInversee() {
        Teacher t = enseignant(1L);
        Contract existant = contrat(5L, t, ContractStatus.ACTIVE, new BigDecimal("100000.00"), LocalDate.now());
        when(contractRepository.findById(5L)).thenReturn(Optional.of(existant));
        ContractRequest requete = ContractRequest.builder()
                .teacherId(1L)
                .type(ContractType.CDI)
                .startDate(LocalDate.of(2026, 10, 5))
                .endDate(LocalDate.of(2026, 10, 1))
                .baseSalary(new BigDecimal("100000.00"))
                .build();

        assertThatThrownBy(() -> hrService.updateContract(5L, requete, httpRequest))
                .isInstanceOf(BusinessException.class);

        verify(contractRepository, never()).save(any(Contract.class));
    }

    @Test
    @DisplayName("deleteContract supprime puis journalise")
    void deleteContractSupprimeEtJournalise() {
        Contract existant = contrat(5L, enseignant(1L), ContractStatus.EXPIRED,
                new BigDecimal("100000.00"), LocalDate.now().minusYears(1));
        when(contractRepository.findById(5L)).thenReturn(Optional.of(existant));

        hrService.deleteContract(5L, httpRequest);

        verify(contractRepository).delete(existant);
        verify(auditService).log(eq("CONTRACT_DELETE"), eq("Contract"), eq(5L),
                contains("Jean Kamdem"), eq(httpRequest));
    }

    @Test
    @DisplayName("deleteContract sur un contrat inconnu ne supprime rien")
    void deleteContractSurUnContratInconnuNeSupprimeRien() {
        when(contractRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hrService.deleteContract(404L, httpRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Contrat");

        verify(contractRepository, never()).delete(any(Contract.class));
    }

    @Test
    @DisplayName("listContracts transmet les filtres et mappe le nom de l'enseignant")
    void listContractsTransmetLesFiltres() {
        when(contractRepository.search(1L, ContractStatus.ACTIVE))
                .thenReturn(List.of(contrat(5L, enseignant(1L), ContractStatus.ACTIVE,
                        new BigDecimal("300000.00"), LocalDate.now())));

        List<ContractResponse> resultat = hrService.listContracts(1L, ContractStatus.ACTIVE);

        assertThat(resultat).hasSize(1);
        assertThat(resultat.get(0).getTeacherName()).isEqualTo("Jean Kamdem");
    }

    // --------------------------------------------------------------------- paie

    @Test
    @DisplayName("generatePayroll exige le premier jour du mois")
    void generatePayrollExigeLePremierJourDuMois() {
        PayrollGenerateRequest requete = PayrollGenerateRequest.builder()
                .teacherId(1L)
                .monthDate(LocalDate.of(2026, 10, 15))
                .build();

        assertThatThrownBy(() -> hrService.generatePayroll(requete, httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("premier jour");

        // Avant tout acces aux repositories : la validation est la premiere ligne.
        verifyNoInteractions(payrollRepository);
        verifyNoInteractions(teacherService);
    }

    @Test
    @DisplayName("generatePayroll refuse un second bulletin pour le meme mois")
    void generatePayrollRefuseUnDoublon() {
        PayrollGenerateRequest requete = PayrollGenerateRequest.builder()
                .teacherId(1L)
                .monthDate(LocalDate.of(2026, 10, 1))
                .build();
        when(payrollRepository.findByTeacherIdAndMonthDate(1L, LocalDate.of(2026, 10, 1)))
                .thenReturn(Optional.of(bulletin(3L, enseignant(1L), PayrollStatus.PENDING)));

        assertThatThrownBy(() -> hrService.generatePayroll(requete, httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("existe déjà");

        // Un doublon = double paiement : rien ne doit etre sauvegarde.
        verify(payrollRepository, never()).save(any(Payroll.class));
    }

    @Test
    @DisplayName("generatePayroll prend le salaire du dernier contrat ACTIF")
    void generatePayrollPrendLeSalaireDuContratActif() {
        Teacher t = enseignant(1L);
        t.setSalary(new BigDecimal("250000.00"));
        when(payrollRepository.findByTeacherIdAndMonthDate(1L, LocalDate.of(2026, 10, 1)))
                .thenReturn(Optional.empty());
        when(teacherService.findById(1L)).thenReturn(t);
        // Le plus recent (10/2026) est ACTIVE ; un plus ancien est EXPIRED.
        when(contractRepository.findByTeacherIdOrderByStartDateDesc(1L)).thenReturn(List.of(
                contrat(2L, t, ContractStatus.ACTIVE, new BigDecimal("400000.00"), LocalDate.of(2026, 10, 1)),
                contrat(1L, t, ContractStatus.EXPIRED, new BigDecimal("150000.00"), LocalDate.of(2025, 1, 1))));
        when(payrollRepository.save(any(Payroll.class))).thenAnswer(inv -> inv.getArgument(0));

        PayrollGenerateRequest requete = PayrollGenerateRequest.builder()
                .teacherId(1L)
                .monthDate(LocalDate.of(2026, 10, 1))
                .build();
        ArgumentCaptor<Payroll> captor = ArgumentCaptor.forClass(Payroll.class);
        hrService.generatePayroll(requete, httpRequest);
        verify(payrollRepository).save(captor.capture());

        assertThat(captor.getValue().getBaseSalary()).isEqualByComparingTo("400000.00");
        assertThat(captor.getValue().getStatus()).isEqualTo(PayrollStatus.PENDING);
    }

    @Test
    @DisplayName("generatePayroll ignore un contrat ACTIVE plus ancien que le plus recent")
    void generatePayrollIgnoreUnContratActifNonRecent() {
        Teacher t = enseignant(1L);
        t.setSalary(new BigDecimal("250000.00"));
        when(payrollRepository.findByTeacherIdAndMonthDate(1L, LocalDate.of(2026, 10, 1)))
                .thenReturn(Optional.empty());
        when(teacherService.findById(1L)).thenReturn(t);
        when(contractRepository.findByTeacherIdOrderByStartDateDesc(1L)).thenReturn(List.of(
                contrat(2L, t, ContractStatus.TERMINATED, new BigDecimal("900000.00"), LocalDate.of(2026, 10, 1)),
                contrat(1L, t, ContractStatus.ACTIVE, new BigDecimal("310000.00"), LocalDate.of(2026, 1, 1))));
        when(payrollRepository.save(any(Payroll.class))).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<Payroll> captor = ArgumentCaptor.forClass(Payroll.class);
        hrService.generatePayroll(PayrollGenerateRequest.builder()
                .teacherId(1L).monthDate(LocalDate.of(2026, 10, 1)).build(), httpRequest);
        verify(payrollRepository).save(captor.capture());

        // Le contrat TERMINATED le plus recent est ecarte : on retombe sur l'ACTIVE.
        assertThat(captor.getValue().getBaseSalary()).isEqualByComparingTo("310000.00");
    }

    @Test
    @DisplayName("generatePayroll retombe sur le salaire du profil si aucun contrat actif")
    void generatePayrollRetombeSurLeSalaireDuProfil() {
        Teacher t = enseignant(1L);
        t.setSalary(new BigDecimal("250000.00"));
        when(payrollRepository.findByTeacherIdAndMonthDate(1L, LocalDate.of(2026, 10, 1)))
                .thenReturn(Optional.empty());
        when(teacherService.findById(1L)).thenReturn(t);
        when(contractRepository.findByTeacherIdOrderByStartDateDesc(1L)).thenReturn(List.of(
                contrat(1L, t, ContractStatus.EXPIRED, new BigDecimal("150000.00"), LocalDate.of(2025, 1, 1))));
        when(payrollRepository.save(any(Payroll.class))).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<Payroll> captor = ArgumentCaptor.forClass(Payroll.class);
        hrService.generatePayroll(PayrollGenerateRequest.builder()
                .teacherId(1L).monthDate(LocalDate.of(2026, 10, 1)).build(), httpRequest);
        verify(payrollRepository).save(captor.capture());

        assertThat(captor.getValue().getBaseSalary()).isEqualByComparingTo("250000.00");
    }

    @Test
    @DisplayName("generatePayroll : net = base + primes - retenues")
    void generatePayrollCalculeLeNet() {
        Teacher t = enseignant(1L);
        when(payrollRepository.findByTeacherIdAndMonthDate(1L, LocalDate.of(2026, 10, 1)))
                .thenReturn(Optional.empty());
        when(teacherService.findById(1L)).thenReturn(t);
        when(contractRepository.findByTeacherIdOrderByStartDateDesc(1L)).thenReturn(List.of(
                contrat(1L, t, ContractStatus.ACTIVE, new BigDecimal("300000.00"), LocalDate.now())));
        when(payrollRepository.save(any(Payroll.class))).thenAnswer(inv -> inv.getArgument(0));

        PayrollGenerateRequest requete = PayrollGenerateRequest.builder()
                .teacherId(1L)
                .monthDate(LocalDate.of(2026, 10, 1))
                .allowances(new BigDecimal("50000.00"))
                .deductions(new BigDecimal("20000.00"))
                .build();
        ArgumentCaptor<Payroll> captor = ArgumentCaptor.forClass(Payroll.class);
        hrService.generatePayroll(requete, httpRequest);
        verify(payrollRepository).save(captor.capture());

        // 300000 + 50000 - 20000 = 330000
        assertThat(captor.getValue().getNetSalary()).isEqualByComparingTo("330000.00");
    }

    @Test
    @DisplayName("generatePayroll : primes et retenues absentes valent zero, pas null")
    void generatePayrollTraiteLesPrimesAbsentesCommeZero() {
        Teacher t = enseignant(1L);
        when(payrollRepository.findByTeacherIdAndMonthDate(1L, LocalDate.of(2026, 10, 1)))
                .thenReturn(Optional.empty());
        when(teacherService.findById(1L)).thenReturn(t);
        when(contractRepository.findByTeacherIdOrderByStartDateDesc(1L)).thenReturn(List.of(
                contrat(1L, t, ContractStatus.ACTIVE, new BigDecimal("300000.00"), LocalDate.now())));
        when(payrollRepository.save(any(Payroll.class))).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<Payroll> captor = ArgumentCaptor.forClass(Payroll.class);
        hrService.generatePayroll(PayrollGenerateRequest.builder()
                .teacherId(1L).monthDate(LocalDate.of(2026, 10, 1)).build(), httpRequest);
        verify(payrollRepository).save(captor.capture());

        // Un null propagerait une NPE dans le calcul du net ; BigDecimal.ZERO est requis.
        assertThat(captor.getValue().getAllowances()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(captor.getValue().getDeductions()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(captor.getValue().getNetSalary()).isEqualByComparingTo("300000.00");
    }

    @Test
    @DisplayName("generatePayroll journalise le bulletin avec le mois")
    void generatePayrollJournalise() {
        Teacher t = enseignant(1L);
        when(payrollRepository.findByTeacherIdAndMonthDate(1L, LocalDate.of(2026, 10, 1)))
                .thenReturn(Optional.empty());
        when(teacherService.findById(1L)).thenReturn(t);
        when(contractRepository.findByTeacherIdOrderByStartDateDesc(1L)).thenReturn(List.of());
        when(payrollRepository.save(any(Payroll.class))).thenAnswer(inv -> {
            Payroll p = inv.getArgument(0);
            p.setId(88L);
            return p;
        });

        hrService.generatePayroll(PayrollGenerateRequest.builder()
                .teacherId(1L).monthDate(LocalDate.of(2026, 10, 1)).build(), httpRequest);

        verify(auditService).log(eq("PAYROLL"), eq("Payroll"), eq(88L),
                contains("Jean Kamdem"), eq(httpRequest));
    }

    @Test
    @DisplayName("markPayrollPaid passe le bulletin en PAID et horodate le paiement")
    void markPayrollPaidHorodateLePaiement() {
        Teacher t = enseignant(1L);
        Payroll bulletin = bulletin(88L, t, PayrollStatus.PENDING);
        when(payrollRepository.findById(88L)).thenReturn(Optional.of(bulletin));
        when(payrollRepository.save(any(Payroll.class))).thenAnswer(inv -> inv.getArgument(0));

        PayrollResponse reponse = hrService.markPayrollPaid(88L, httpRequest);

        assertThat(reponse.getStatus()).isEqualTo(PayrollStatus.PAID);
        // Sans paidAt, la date de paiement serait indeterminable.
        assertThat(bulletin.getPaidAt()).isNotNull();
        verify(auditService).log(eq("PAYROLL_PAID"), eq("Payroll"), eq(88L),
                contains("Jean Kamdem"), eq(httpRequest));
    }

    @Test
    @DisplayName("markPayrollPaid refuse de repayer un bulletin deja PAID")
    void markPayrollPaidRefuseUnDoublePaiement() {
        Payroll bulletin = bulletin(88L, enseignant(1L), PayrollStatus.PAID);
        when(payrollRepository.findById(88L)).thenReturn(Optional.of(bulletin));

        assertThatThrownBy(() -> hrService.markPayrollPaid(88L, httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("déjà");

        // Un second paiement est une perte financiere : aucune ecriture.
        verify(payrollRepository, never()).save(any(Payroll.class));
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("markPayrollPaid sur un bulletin inconnu leve une exception nommee")
    void markPayrollPaidSurUnBulletinInconnuLeveUneException() {
        when(payrollRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hrService.markPayrollPaid(404L, httpRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Bulletin de paie")
                .hasMessageContaining("404");
    }

    @Test
    @DisplayName("listPayrolls transmet les trois filtres")
    void listPayrollsTransmetLesFiltres() {
        when(payrollRepository.search(1L, LocalDate.of(2026, 10, 1), PayrollStatus.PENDING))
                .thenReturn(List.of(bulletin(88L, enseignant(1L), PayrollStatus.PENDING)));

        List<PayrollResponse> resultat = hrService.listPayrolls(1L, LocalDate.of(2026, 10, 1), PayrollStatus.PENDING);

        assertThat(resultat).hasSize(1);
        assertThat(resultat.get(0).getNetSalary()).isEqualByComparingTo("250000.00");
    }
}
