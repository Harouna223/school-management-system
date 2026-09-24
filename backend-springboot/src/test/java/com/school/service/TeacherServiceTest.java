package com.school.service;

import com.school.dto.request.TeacherRequest;
import com.school.dto.response.PageResponse;
import com.school.dto.response.TeacherResponse;
import com.school.entity.Teacher;
import com.school.entity.User;
import com.school.enums.ContractType;
import com.school.enums.Gender;
import com.school.enums.TeacherStatus;
import com.school.exception.ResourceNotFoundException;
import com.school.mapper.TeacherMapper;
import com.school.repository.TeacherRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Garde le module enseignants : creation avec compte lie, generation du matricule,
 * valeurs par defaut, audit, photo, suppression.
 *
 * Le point le plus sensible est le compte utilisateur : il ne doit etre cree QUE si
 * {@code createUserAccount} vaut explicitement TRUE, et le mot de passe par defaut ne doit
 * jamais ecraser un mot de passe fourni.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TeacherServiceTest {

    @Mock
    private TeacherRepository teacherRepository;

    @Mock
    private TeacherMapper teacherMapper;

    @Mock
    private AuthService authService;

    @Mock
    private AuditService auditService;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private TeacherService teacherService;

    // ------------------------------------------------------------------ helpers

    private TeacherRequest requestDeBase() {
        return TeacherRequest.builder()
                .firstName("Jean")
                .lastName("Kamdem")
                .gender(Gender.MALE)
                .hireDate(LocalDate.of(2026, 9, 1))
                .contractType(ContractType.CDI)
                .build();
    }

    private Teacher enseignant(Long id, String firstName, String lastName, String employeeNo) {
        return Teacher.builder()
                .id(id)
                .employeeNo(employeeNo)
                .firstName(firstName)
                .lastName(lastName)
                .gender(Gender.MALE)
                .hireDate(LocalDate.of(2026, 9, 1))
                .contractType(ContractType.CDI)
                .salary(new BigDecimal("250000.00"))
                .status(TeacherStatus.ACTIVE)
                .build();
    }

    /** Le repository renvoie l'entite telle qu'elle lui a ete confiee (mock de save). */
    private void saveRenvoieSonArgument() {
        when(teacherRepository.save(any(Teacher.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ----------------------------------------------------- generation matricule

    @Test
    @DisplayName("Le matricule suit le format ENS-AAAA-XXXX et utilise count()+1")
    void leMatriculeSuitLeFormatAttendu() {
        when(teacherRepository.count()).thenReturn(0L);
        when(teacherRepository.existsByEmployeeNo(anyString())).thenReturn(false);
        saveRenvoieSonArgument();
        when(teacherMapper.toEntity(any())).thenReturn(enseignant(null, "Jean", "Kamdem", null));

        TeacherResponse reponse = teacherService.create(requestDeBase(), httpRequest);

        // ENS-<annee courante>-0001 : on reconstruit l'annee attendue plutot que de la coder en dur.
        String annee = String.valueOf(LocalDate.now().getYear());
        assertThat(reponse.getEmployeeNo()).isEqualTo("ENS-" + annee + "-0001");
    }

    @Test
    @DisplayName("Une collision de matricule est contournee : on incremente jusqu'a un numero libre")
    void uneCollisionDeMatriculeEstContournee() {
        when(teacherRepository.count()).thenReturn(0L);
        saveRenvoieSonArgument();
        when(teacherMapper.toEntity(any())).thenReturn(enseignant(null, "Jean", "Kamdem", null));
        // ENS-…-0001 et ENS-…-0002 sont deja pris, ENS-…-0003 est libre.
        String annee = String.valueOf(LocalDate.now().getYear());
        when(teacherRepository.existsByEmployeeNo(anyString())).thenReturn(true);
        when(teacherRepository.existsByEmployeeNo("ENS-" + annee + "-0003")).thenReturn(false);

        TeacherResponse reponse = teacherService.create(requestDeBase(), httpRequest);

        assertThat(reponse.getEmployeeNo()).isEqualTo("ENS-" + annee + "-0003");
        // Les deux numeros en collision ont bien ete testes avant de trouver le libre.
        verify(teacherRepository).existsByEmployeeNo("ENS-" + annee + "-0001");
        verify(teacherRepository).existsByEmployeeNo("ENS-" + annee + "-0002");
    }

    @Test
    @DisplayName("Un matricule deja present n'est jamais reutilise meme apres plusieurs collisions")
    void laBoucleDeCollisionNeBouclePasInfiniment() {
        when(teacherRepository.count()).thenReturn(41L);
        saveRenvoieSonArgument();
        when(teacherMapper.toEntity(any())).thenReturn(enseignant(null, "Jean", "Kamdem", null));
        String annee = String.valueOf(LocalDate.now().getYear());
        when(teacherRepository.existsByEmployeeNo(anyString())).thenReturn(true);
        when(teacherRepository.existsByEmployeeNo("ENS-" + annee + "-0044")).thenReturn(false);

        TeacherResponse reponse = teacherService.create(requestDeBase(), httpRequest);

        // count()=41 => premier essai 0042, pris ; 0043 pris ; 0044 libre.
        assertThat(reponse.getEmployeeNo()).isEqualTo("ENS-" + annee + "-0044");
    }

    // ------------------------------------------------------------- create : defauts

    @Test
    @DisplayName("create force le statut ACTIVE et un salaire a zero si le salaire est absent")
    void createForceStatutActifEtSalaireZeroParDefaut() {
        when(teacherRepository.count()).thenReturn(0L);
        when(teacherRepository.existsByEmployeeNo(anyString())).thenReturn(false);
        saveRenvoieSonArgument();
        Teacher entite = enseignant(null, "Jean", "Kamdem", null);
        entite.setStatus(null);
        entite.setSalary(null);
        when(teacherMapper.toEntity(any())).thenReturn(entite);

        ArgumentCaptor<Teacher> captor = ArgumentCaptor.forClass(Teacher.class);
        TeacherResponse reponse = teacherService.create(requestDeBase(), httpRequest);
        verify(teacherRepository).save(captor.capture());

        assertThat(captor.getValue().getStatus()).isEqualTo(TeacherStatus.ACTIVE);
        assertThat(captor.getValue().getSalary()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(reponse.getStatus()).isEqualTo(TeacherStatus.ACTIVE);
    }

    @Test
    @DisplayName("create respecte le salaire fourni dans la requete")
    void createRespecteLeSalaireFourni() {
        when(teacherRepository.count()).thenReturn(0L);
        when(teacherRepository.existsByEmployeeNo(anyString())).thenReturn(false);
        saveRenvoieSonArgument();
        when(teacherMapper.toEntity(any())).thenReturn(enseignant(null, "Jean", "Kamdem", null));
        TeacherRequest requete = requestDeBase();
        requete.setSalary(new BigDecimal("480000.00"));

        teacherService.create(requete, httpRequest);

        ArgumentCaptor<Teacher> captor = ArgumentCaptor.forClass(Teacher.class);
        verify(teacherRepository).save(captor.capture());
        assertThat(captor.getValue().getSalary()).isEqualByComparingTo("480000.00");
    }

    @Test
    @DisplayName("create journalise une action CREATE avec le matricule")
    void createJournaliseUneActionCreate() {
        when(teacherRepository.count()).thenReturn(0L);
        when(teacherRepository.existsByEmployeeNo(anyString())).thenReturn(false);
        saveRenvoieSonArgument();
        when(teacherMapper.toEntity(any())).thenReturn(enseignant(12L, "Jean", "Kamdem", null));

        teacherService.create(requestDeBase(), httpRequest);

        verify(auditService).log(eq("CREATE"), eq("Teacher"), eq(12L),
                contains("Jean Kamdem"), eq(httpRequest));
    }

    // ------------------------------------------------ create : compte utilisateur lie

    @Test
    @DisplayName("Aucun compte utilisateur n'est cree si createUserAccount est absent")
    void aucunCompteCreeSiLeDrapeauEstAbsent() {
        when(teacherRepository.count()).thenReturn(0L);
        when(teacherRepository.existsByEmployeeNo(anyString())).thenReturn(false);
        saveRenvoieSonArgument();
        when(teacherMapper.toEntity(any())).thenReturn(enseignant(1L, "Jean", "Kamdem", null));

        TeacherResponse reponse = teacherService.create(requestDeBase(), httpRequest);

        // L'invariant le plus important : pas de compte par accident.
        verifyNoInteractions(authService);
        assertThat(reponse.getEmail()).isNull();
    }

    @Test
    @DisplayName("Aucun compte cree si createUserAccount vaut explicitement FALSE")
    void aucunCompteCreeSiLeDrapeauEstFaux() {
        when(teacherRepository.count()).thenReturn(0L);
        when(teacherRepository.existsByEmployeeNo(anyString())).thenReturn(false);
        saveRenvoieSonArgument();
        when(teacherMapper.toEntity(any())).thenReturn(enseignant(1L, "Jean", "Kamdem", null));
        TeacherRequest requete = requestDeBase();
        requete.setCreateUserAccount(Boolean.FALSE);

        teacherService.create(requete, httpRequest);

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("Un compte ENSEIGNANT est cree quand createUserAccount vaut TRUE")
    void unCompteEnseignantEstCreeSiLeDrapeauEstVrai() {
        when(teacherRepository.count()).thenReturn(0L);
        when(teacherRepository.existsByEmployeeNo(anyString())).thenReturn(false);
        saveRenvoieSonArgument();
        Teacher entite = enseignant(1L, "Jean", "Kamdem", null);
        when(teacherMapper.toEntity(any())).thenReturn(entite);
        User utilisateur = User.builder().id(99L).username("j.kamdem").build();
        when(authService.createLinkedAccount(anyString(), anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(utilisateur);

        TeacherRequest requete = requestDeBase();
        requete.setCreateUserAccount(Boolean.TRUE);
        requete.setUsername("j.kamdem");
        requete.setPassword("MonMotDePasse!42");
        requete.setEmail("j.kamdem@school.td");

        teacherService.create(requete, httpRequest);

        ArgumentCaptor<Teacher> captor = ArgumentCaptor.forClass(Teacher.class);
        verify(teacherRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(utilisateur);
        // Le role doit etre exactement « ENSEIGNANT » : un role errone ouvrirait un acces admin.
        verify(authService).createLinkedAccount("j.kamdem", "MonMotDePasse!42", "j.kamdem@school.td",
                "Jean", "Kamdem", "ENSEIGNANT");
    }

    @Test
    @DisplayName("Sans identifiants, le nom d'utilisateur est derive du nom et le mot de passe est le defaut")
    void lesIdentifiantsParDefautSontDerivesDuNom() {
        when(teacherRepository.count()).thenReturn(0L);
        when(teacherRepository.existsByEmployeeNo(anyString())).thenReturn(false);
        saveRenvoieSonArgument();
        when(teacherMapper.toEntity(any())).thenReturn(enseignant(1L, "Jean", "Kamdem", null));
        when(authService.createLinkedAccount(anyString(), anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(User.builder().id(99L).build());
        TeacherRequest requete = requestDeBase();
        requete.setCreateUserAccount(Boolean.TRUE);

        teacherService.create(requete, httpRequest);

        verify(authService).createLinkedAccount("jean.kamdem", "Enseignant@123", null,
                "Jean", "Kamdem", "ENSEIGNANT");
    }

    @Test
    @DisplayName("Le nom d'utilisateur derive est nettoye (accents, espaces, apostrophes)")
    void leNomDUtilisateurDeriveEstNettoye() {
        when(teacherRepository.count()).thenReturn(0L);
        when(teacherRepository.existsByEmployeeNo(anyString())).thenReturn(false);
        saveRenvoieSonArgument();
        when(teacherMapper.toEntity(any())).thenReturn(enseignant(1L, "Aicha", "N'Diaye", null));
        when(authService.createLinkedAccount(anyString(), anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(User.builder().id(99L).build());
        TeacherRequest requete = requestDeBase();
        requete.setFirstName("Aicha");
        requete.setLastName("N'Diaye");
        requete.setCreateUserAccount(Boolean.TRUE);

        teacherService.create(requete, httpRequest);

        // Tout caractere hors [a-z0-9.] doit disparaitre : « aicha.ndiaye ».
        verify(authService).createLinkedAccount("aicha.ndiaye", "Enseignant@123", null,
                "Aicha", "N'Diaye", "ENSEIGNANT");
    }

    @Test
    @DisplayName("Un mot de passe fourni n'est jamais remplace par le mot de passe par defaut")
    void unMotDePasseFourniNestJamaisRemplace() {
        when(teacherRepository.count()).thenReturn(0L);
        when(teacherRepository.existsByEmployeeNo(anyString())).thenReturn(false);
        saveRenvoieSonArgument();
        when(teacherMapper.toEntity(any())).thenReturn(enseignant(1L, "Jean", "Kamdem", null));
        when(authService.createLinkedAccount(anyString(), anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(User.builder().id(99L).build());
        TeacherRequest requete = requestDeBase();
        requete.setCreateUserAccount(Boolean.TRUE);
        requete.setUsername("jean.k");
        requete.setPassword("UnVraiMotDePasse!2026");

        teacherService.create(requete, httpRequest);

        // Never() sur le defaut : si le mot de passe fourni etait ecrase, l'utilisateur
        // ne pourrait plus se connecter avec celui qu'il a choisi.
        verify(authService, never()).createLinkedAccount(anyString(), eq("Enseignant@123"),
                any(), anyString(), anyString(), anyString());
    }

    // ------------------------------------------------------------------- update

    @Test
    @DisplayName("update conserve le matricule, le statut et la photo existants")
    void updateNeTouchePasAuMatriculeNiAuStatut() {
        Teacher existant = enseignant(7L, "Jean", "Kamdem", "ENS-2026-0001");
        existant.setPhoto("/uploads/teachers/a.png");
        when(teacherRepository.findById(7L)).thenReturn(Optional.of(existant));
        saveRenvoieSonArgument();
        TeacherRequest requete = requestDeBase();
        requete.setPhone("66000000");

        teacherService.update(7L, requete, httpRequest);

        ArgumentCaptor<Teacher> captor = ArgumentCaptor.forClass(Teacher.class);
        verify(teacherRepository).save(captor.capture());
        assertThat(captor.getValue().getEmployeeNo()).isEqualTo("ENS-2026-0001");
        assertThat(captor.getValue().getStatus()).isEqualTo(TeacherStatus.ACTIVE);
        assertThat(captor.getValue().getPhoto()).isEqualTo("/uploads/teachers/a.png");
        assertThat(captor.getValue().getId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("update n'ecrase pas le salaire si la requete n'en fournit pas")
    void updateNeffacePasLeSalaireSiLaRequeteEstMuette() {
        Teacher existant = enseignant(7L, "Jean", "Kamdem", "ENS-2026-0001");
        when(teacherRepository.findById(7L)).thenReturn(Optional.of(existant));
        saveRenvoieSonArgument();

        teacherService.update(7L, requestDeBase(), httpRequest);

        ArgumentCaptor<Teacher> captor = ArgumentCaptor.forClass(Teacher.class);
        verify(teacherRepository).save(captor.capture());
        assertThat(captor.getValue().getSalary()).isEqualByComparingTo("250000.00");
    }

    @Test
    @DisplayName("update ne cree aucun compte si createUserAccount est absent")
    void updateNeCreeAucunCompteSansDrapeau() {
        Teacher existant = enseignant(7L, "Jean", "Kamdem", "ENS-2026-0001");
        when(teacherRepository.findById(7L)).thenReturn(Optional.of(existant));
        saveRenvoieSonArgument();

        teacherService.update(7L, requestDeBase(), httpRequest);

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("update rattache un compte ENSEIGNANT si le drapeau vaut TRUE")
    void updateRattacheUnCompteSiDemande() {
        Teacher existant = enseignant(7L, "Jean", "Kamdem", "ENS-2026-0001");
        when(teacherRepository.findById(7L)).thenReturn(Optional.of(existant));
        saveRenvoieSonArgument();
        User utilisateur = User.builder().id(99L).username("jean.kamdem").build();
        when(authService.createLinkedAccount(anyString(), anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(utilisateur);
        TeacherRequest requete = requestDeBase();
        requete.setCreateUserAccount(Boolean.TRUE);

        teacherService.update(7L, requete, httpRequest);

        ArgumentCaptor<Teacher> captor = ArgumentCaptor.forClass(Teacher.class);
        verify(teacherRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(utilisateur);
        verify(authService).createLinkedAccount("jean.kamdem", "Enseignant@123", null,
                "Jean", "Kamdem", "ENSEIGNANT");
    }

    @Test
    @DisplayName("update journalise une action UPDATE nommant l'enseignant")
    void updateJournaliseUneActionUpdate() {
        Teacher existant = enseignant(7L, "Jean", "Kamdem", "ENS-2026-0001");
        when(teacherRepository.findById(7L)).thenReturn(Optional.of(existant));
        saveRenvoieSonArgument();

        teacherService.update(7L, requestDeBase(), httpRequest);

        verify(auditService).log(eq("UPDATE"), eq("Teacher"), eq(7L),
                contains("Jean Kamdem"), eq(httpRequest));
    }

    @Test
    @DisplayName("update sur un identifiant inconnu leve une ResourceNotFoundException")
    void updateSurIdentifiantInconnuLeveUneException() {
        when(teacherRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teacherService.update(404L, requestDeBase(), httpRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("404");
        verify(teacherRepository, never()).save(any(Teacher.class));
    }

    // ------------------------------------------------------------- updateStatus

    @Test
    @DisplayName("updateStatus applique le nouveau statut et le journalise")
    void updateStatusAppliqueLeNouveauStatut() {
        Teacher existant = enseignant(7L, "Jean", "Kamdem", "ENS-2026-0001");
        when(teacherRepository.findById(7L)).thenReturn(Optional.of(existant));
        saveRenvoieSonArgument();

        TeacherResponse reponse = teacherService.updateStatus(7L, TeacherStatus.ON_LEAVE, httpRequest);

        assertThat(reponse.getStatus()).isEqualTo(TeacherStatus.ON_LEAVE);
        verify(auditService).log("UPDATE", "Teacher", 7L, "Statut changé : ON_LEAVE", httpRequest);
    }

    // -------------------------------------------------------------- uploadPhoto

    @Test
    @DisplayName("uploadPhoto delegue au stockage avec le sous-repertoire teachers")
    void uploadPhotoDelegueAuStockage() {
        Teacher existant = enseignant(7L, "Jean", "Kamdem", "ENS-2026-0001");
        when(teacherRepository.findById(7L)).thenReturn(Optional.of(existant));
        saveRenvoieSonArgument();
        MultipartFile fichier = org.mockito.Mockito.mock(MultipartFile.class);
        when(fileStorageService.store(fichier, "teachers")).thenReturn("/uploads/teachers/nouvelle.png");

        TeacherResponse reponse = teacherService.uploadPhoto(7L, fichier);

        assertThat(reponse.getPhoto()).isEqualTo("/uploads/teachers/nouvelle.png");
        // Le chemin renvoye par le stockage doit etre persiste tel quel.
        ArgumentCaptor<Teacher> captor = ArgumentCaptor.forClass(Teacher.class);
        verify(teacherRepository).save(captor.capture());
        assertThat(captor.getValue().getPhoto()).isEqualTo("/uploads/teachers/nouvelle.png");
    }

    @Test
    @DisplayName("uploadPhoto sur un identifiant inconnu ne stocke rien")
    void uploadPhotoSurIdentifiantInconnuNeStockeRien() {
        when(teacherRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teacherService.uploadPhoto(404L, org.mockito.Mockito.mock(MultipartFile.class)))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(fileStorageService);
    }

    // ------------------------------------------------------------------- delete

    @Test
    @DisplayName("delete journalise AVANT de supprimer (le profil reste decrit)")
    void deleteJournalisePuisSupprime() {
        Teacher existant = enseignant(7L, "Jean", "Kamdem", "ENS-2026-0001");
        when(teacherRepository.findById(7L)).thenReturn(Optional.of(existant));

        teacherService.delete(7L, httpRequest);

        verify(auditService).log(eq("DELETE"), eq("Teacher"), eq(7L),
                contains("Jean Kamdem"), eq(httpRequest));
        verify(teacherRepository).delete(existant);
    }

    @Test
    @DisplayName("delete sur un identifiant inconnu leve une exception et ne supprime rien")
    void deleteSurIdentifiantInconnuNeSupprimeRien() {
        when(teacherRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teacherService.delete(404L, httpRequest))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(teacherRepository, never()).delete(any(Teacher.class));
        verifyNoInteractions(auditService);
    }

    // -------------------------------------------------------------- search / get

    @Test
    @DisplayName("search transmet le filtre et la pagination, tries par nom croissant")
    void searchTransmetFiltreEtPagination() {
        Page<Teacher> page = new PageImpl<>(
                List.of(enseignant(1L, "Jean", "Kamdem", "ENS-2026-0001")),
                org.springframework.data.domain.PageRequest.of(0, 5), 1L);
        when(teacherRepository.search(eq("kam"), eq(TeacherStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(page);

        PageResponse<TeacherResponse> reponse = teacherService.search("kam", TeacherStatus.ACTIVE, 0, 5);

        assertThat(reponse.getContent()).hasSize(1);
        assertThat(reponse.getContent().get(0).getEmployeeNo()).isEqualTo("ENS-2026-0001");
        assertThat(reponse.getTotalElements()).isEqualTo(1L);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(teacherRepository).search(eq("kam"), eq(TeacherStatus.ACTIVE), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
        // Le tri est impose par le service, pas par l'appelant.
        assertThat(captor.getValue().getSort().getOrderFor("lastName")).isNotNull();
        assertThat(captor.getValue().getSort().getOrderFor("lastName").isAscending()).isTrue();
    }

    @Test
    @DisplayName("search accepte des filtres nuls (aucun critere)")
    void searchAccepteDesFiltresNuls() {
        Page<Teacher> vide = new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(0, 10), 0L);
        when(teacherRepository.search(isNull(), isNull(), any(Pageable.class))).thenReturn(vide);

        PageResponse<TeacherResponse> reponse = teacherService.search(null, null, 0, 10);

        assertThat(reponse.getContent()).isEmpty();
        assertThat(reponse.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("findById sur un identifiant inconnu leve une exception nommant l'entite")
    void findByIdInconnuLeveUneExceptionNommee() {
        when(teacherRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teacherService.findById(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Enseignant")
                .hasMessageContaining("404");
    }

    @Test
    @DisplayName("getById renvoie l'enseignant demande")
    void getByIdRenvoieLEnseignant() {
        when(teacherRepository.findById(7L)).thenReturn(Optional.of(enseignant(7L, "Jean", "Kamdem", "ENS-2026-0001")));

        TeacherResponse reponse = teacherService.getById(7L);

        assertThat(reponse.getId()).isEqualTo(7L);
        assertThat(reponse.getFirstName()).isEqualTo("Jean");
        assertThat(reponse.getLastName()).isEqualTo("Kamdem");
    }

    @Test
    @DisplayName("create sauvegarde exactement une fois (pas de double insert)")
    void createNeSauvegardeQuUneFois() {
        when(teacherRepository.count()).thenReturn(0L);
        when(teacherRepository.existsByEmployeeNo(anyString())).thenReturn(false);
        saveRenvoieSonArgument();
        when(teacherMapper.toEntity(any())).thenReturn(enseignant(1L, "Jean", "Kamdem", null));

        teacherService.create(requestDeBase(), httpRequest);

        verify(teacherRepository, times(1)).save(any(Teacher.class));
        verify(teacherRepository, never()).delete(any(Teacher.class));
    }
}
