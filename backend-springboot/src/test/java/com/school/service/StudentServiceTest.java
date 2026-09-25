package com.school.service;

import com.school.dto.request.StudentRequest;
import com.school.dto.response.PageResponse;
import com.school.dto.response.StudentHistoryResponse;
import com.school.dto.response.StudentResponse;
import com.school.entity.Level;
import com.school.entity.Parent;
import com.school.entity.SchoolClass;
import com.school.entity.Student;
import com.school.entity.StudentHistory;
import com.school.entity.User;
import com.school.enums.EducationCycle;
import com.school.enums.Gender;
import com.school.enums.StudentHistoryAction;
import com.school.enums.StudentStatus;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.mapper.StudentMapper;
import com.school.repository.ParentRepository;
import com.school.repository.SchoolClassRepository;
import com.school.repository.StudentHistoryRepository;
import com.school.repository.StudentRepository;
import com.school.repository.UserRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Module eleves : inscription, matricule, parent, compte utilisateur, parcours scolaire.
 *
 * ⚠️ Le piege majeur de ce service est une <b>asymetrie de defaut</b> entre creation et mise a jour :
 * <ul>
 *   <li>{@code create} : le compte est cree PAR DEFAUT — seule la valeur explicite {@code FALSE}
 *       l'empeche ({@code !Boolean.FALSE.equals(...)}). Un appelant qui oublie le drapeau cree un compte.</li>
 *   <li>{@code update} : le compte n'est cree QUE si le drapeau vaut explicitement {@code TRUE}
 *       ({@code Boolean.TRUE.equals(...)}). Un appelant qui oublie le drapeau n'en cree aucun.</li>
 * </ul>
 * Cette difference est volontaire mais invisible a la lecture rapide : elle est epinglee ci-dessous.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudentServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private ParentRepository parentRepository;

    @Mock
    private SchoolClassRepository schoolClassRepository;

    @Mock
    private StudentHistoryRepository studentHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private AuthService authService;

    @Mock
    private AuditService auditService;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private StudentService studentService;

    // ------------------------------------------------------------------ helpers

    private StudentRequest demandeDeBase() {
        return StudentRequest.builder()
                .firstName("Aicha")
                .lastName("Kamdem")
                .gender(Gender.FEMALE)
                .build();
    }

    private Student eleve(Long id, String matricule, StudentStatus statut, SchoolClass classe) {
        return Student.builder()
                .id(id)
                .matricule(matricule)
                .firstName("Aicha")
                .lastName("Kamdem")
                .gender(Gender.FEMALE)
                .enrollmentDate(LocalDate.of(2026, 9, 1))
                .status(statut)
                .schoolClass(classe)
                .build();
    }

    private SchoolClass classe(Long id, String nom, EducationCycle cycle) {
        return SchoolClass.builder()
                .id(id)
                .name(nom)
                .level(cycle != null ? Level.builder().id(1L).name("Niveau").code("N1")
                        .educationCycle(cycle).build() : null)
                .build();
    }

    private Parent parent(Long id) {
        return Parent.builder().id(id).firstName("Harouna").lastName("Siby")
                .phone("66000000").email("h.siby@school.td").build();
    }

    private void saveEleveRenvoieSonArgument() {
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> {
            Student s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(6L);
            }
            return s;
        });
    }

    /** Par defaut, aucun compte pour cet eleve : `uniqueUsername` trouve « aicha.kamdem » libre. */
    private void matriculeLibre() {
        when(studentRepository.count()).thenReturn(0L);
        when(studentRepository.existsByMatricule(anyString())).thenReturn(false);
    }

    // -------------------------------------------------------------- inscription

    @Test
    @DisplayName("create genere un matricule ETU-AAAA-XXXXXX et force le statut ACTIVE")
    void createGenereMatriculeEtForceLeStatut() {
        matriculeLibre();
        saveEleveRenvoieSonArgument();
        when(studentMapper.toEntity(any())).thenReturn(eleve(null, null, StudentStatus.GRADUATED, null));

        ArgumentCaptor<Student> captor = ArgumentCaptor.forClass(Student.class);
        StudentResponse reponse = studentService.create(demandeDeBase(), httpRequest);
        verify(studentRepository).save(captor.capture());

        String annee = String.valueOf(LocalDate.now().getYear());
        assertThat(captor.getValue().getMatricule()).isEqualTo("ETU-" + annee + "-000001");
        // Le statut demande par le mapper ne doit jamais passer : un nouvel eleve est ACTIVE.
        assertThat(captor.getValue().getStatus()).isEqualTo(StudentStatus.ACTIVE);
        assertThat(reponse.getStatus()).isEqualTo(StudentStatus.ACTIVE);
    }

    @Test
    @DisplayName("create contourne une collision de matricule")
    void createContourneUneCollisionDeMatricule() {
        when(studentRepository.count()).thenReturn(0L);
        when(studentMapper.toEntity(any())).thenReturn(eleve(null, null, null, null));
        saveEleveRenvoieSonArgument();
        String annee = String.valueOf(LocalDate.now().getYear());
        when(studentRepository.existsByMatricule(anyString())).thenReturn(true);
        when(studentRepository.existsByMatricule("ETU-" + annee + "-000003")).thenReturn(false);

        StudentResponse reponse = studentService.create(demandeDeBase(), httpRequest);

        assertThat(reponse.getMatricule()).isEqualTo("ETU-" + annee + "-000003");
        verify(studentRepository).existsByMatricule("ETU-" + annee + "-000001");
        verify(studentRepository).existsByMatricule("ETU-" + annee + "-000002");
    }

    @Test
    @DisplayName("create fixe la date d'inscription a aujourd'hui si elle est absente")
    void createFixeLaDateDInscriptionParDefaut() {
        matriculeLibre();
        saveEleveRenvoieSonArgument();
        when(studentMapper.toEntity(any())).thenReturn(eleve(null, null, null, null));

        StudentResponse reponse = studentService.create(demandeDeBase(), httpRequest);

        assertThat(reponse.getEnrollmentDate()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("create respecte la date d'inscription fournie")
    void createRespecteLaDateDInscriptionFournie() {
        matriculeLibre();
        saveEleveRenvoieSonArgument();
        when(studentMapper.toEntity(any())).thenReturn(eleve(null, null, null, null));
        StudentRequest requete = demandeDeBase();
        LocalDate voulue = LocalDate.of(2025, 9, 15);
        requete.setEnrollmentDate(voulue);

        StudentResponse reponse = studentService.create(requete, httpRequest);

        assertThat(reponse.getEnrollmentDate()).isEqualTo(voulue);
    }

    @Test
    @DisplayName("create deduit le cycle depuis le niveau de la classe si le cycle est absent")
    void createDeduitLeCycleDepuisLaClasse() {
        matriculeLibre();
        saveEleveRenvoieSonArgument();
        when(studentMapper.toEntity(any())).thenReturn(eleve(null, null, null, null));
        when(schoolClassRepository.findById(3L)).thenReturn(Optional.of(classe(3L, "6eme A", EducationCycle.COLLEGE)));
        StudentRequest requete = demandeDeBase();
        requete.setClassId(3L);

        StudentResponse reponse = studentService.create(requete, httpRequest);

        assertThat(reponse.getClassName()).isEqualTo("6eme A");
        assertThat(reponse.getEducationCycle()).isEqualTo(EducationCycle.COLLEGE);
    }

    @Test
    @DisplayName("create respecte un cycle fourni explicitement meme s'il differe du niveau")
    void createRespecteLeCycleExplicite() {
        matriculeLibre();
        saveEleveRenvoieSonArgument();
        when(studentMapper.toEntity(any())).thenReturn(eleve(null, null, null, null));
        when(schoolClassRepository.findById(3L)).thenReturn(Optional.of(classe(3L, "6eme A", EducationCycle.COLLEGE)));
        StudentRequest requete = demandeDeBase();
        requete.setClassId(3L);
        requete.setEducationCycle(EducationCycle.LYCEE);

        StudentResponse reponse = studentService.create(requete, httpRequest);

        assertThat(reponse.getEducationCycle()).isEqualTo(EducationCycle.LYCEE);
    }

    @Test
    @DisplayName("create sans classe rattache l'eleve a aucune classe (cas universitaire)")
    void createSansClasseNeRattacheRien() {
        matriculeLibre();
        saveEleveRenvoieSonArgument();
        when(studentMapper.toEntity(any())).thenReturn(eleve(null, null, null, null));

        ArgumentCaptor<Student> captor = ArgumentCaptor.forClass(Student.class);
        StudentResponse reponse = studentService.create(demandeDeBase(), httpRequest);
        verify(studentRepository).save(captor.capture());

        assertThat(captor.getValue().getSchoolClass()).isNull();
        assertThat(reponse.getClassName()).isNull();
    }

    @Test
    @DisplayName("create avec une classe inexistante leve une exception et n'ecrit aucun eleve")
    void createAvecClasseInexistanteNecritRien() {
        when(studentMapper.toEntity(any())).thenReturn(eleve(null, null, null, null));
        when(studentRepository.count()).thenReturn(0L);
        when(studentRepository.existsByMatricule(anyString())).thenReturn(false);
        when(schoolClassRepository.findById(999L)).thenReturn(Optional.empty());
        StudentRequest requete = demandeDeBase();
        requete.setClassId(999L);

        assertThatThrownBy(() -> studentService.create(requete, httpRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Classe");

        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    @DisplayName("create journalise l'inscription avec le matricule")
    void createJournalise() {
        matriculeLibre();
        saveEleveRenvoieSonArgument();
        when(studentMapper.toEntity(any())).thenReturn(eleve(null, null, null, null));

        studentService.create(demandeDeBase(), httpRequest);

        verify(auditService).log(eq("CREATE"), eq("Student"), eq(6L),
                contains("Aicha Kamdem"), eq(httpRequest));
    }

    // ------------------------------------------- compte utilisateur : asymetrie

    @Test
    @DisplayName("create cree un compte ELEVE par defaut (drapeau absent)")
    void createCreeUnCompteParDefaut() {
        matriculeLibre();
        saveEleveRenvoieSonArgument();
        when(studentMapper.toEntity(any())).thenReturn(eleve(null, null, null, null));
        when(authService.createLinkedAccount(anyString(), anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(User.builder().id(50L).build());

        ArgumentCaptor<Student> captor = ArgumentCaptor.forClass(Student.class);
        StudentResponse reponse = studentService.create(demandeDeBase(), httpRequest);
        verify(studentRepository).save(captor.capture());

        // ⚠️ Asymetrie : c'est l'INVERSE de update. Le drapeau absent cree bien un compte.
        verify(authService).createLinkedAccount(eq("aicha.kamdem"), eq("Eleve@123"), any(),
                eq("Aicha"), eq("Kamdem"), eq("ELEVE"));
        assertThat(captor.getValue().getUser()).isNotNull();
        assertThat(reponse.getHasAccount()).isTrue();
    }

    @Test
    @DisplayName("create avec createUserAccount=FALSE ne cree AUCUN compte")
    void createAvecDrapeauFauxNeCreeAucunCompte() {
        matriculeLibre();
        saveEleveRenvoieSonArgument();
        when(studentMapper.toEntity(any())).thenReturn(eleve(null, null, null, null));
        StudentRequest requete = demandeDeBase();
        requete.setCreateUserAccount(Boolean.FALSE);

        StudentResponse reponse = studentService.create(requete, httpRequest);

        verifyNoInteractions(authService);
        assertThat(reponse.getHasAccount()).isFalse();
    }

    @Test
    @DisplayName("create utilise l'email fourni au lieu de l'email derive du matricule")
    void createUtiliseLEmailFourni() {
        matriculeLibre();
        saveEleveRenvoieSonArgument();
        when(studentMapper.toEntity(any())).thenReturn(eleve(null, null, null, null));
        when(authService.createLinkedAccount(anyString(), anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(User.builder().id(50L).build());
        StudentRequest requete = demandeDeBase();
        requete.setEmail("aicha@ecole.td");

        studentService.create(requete, httpRequest);

        verify(authService).createLinkedAccount(anyString(), anyString(), eq("aicha@ecole.td"),
                anyString(), anyString(), eq("ELEVE"));
    }

    @Test
    @DisplayName("create derive l'email du matricule si aucun email n'est fourni")
    void createDeriveLEmailDuMatricule() {
        matriculeLibre();
        saveEleveRenvoieSonArgument();
        when(studentMapper.toEntity(any())).thenReturn(eleve(null, null, null, null));
        when(authService.createLinkedAccount(anyString(), anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(User.builder().id(50L).build());

        studentService.create(demandeDeBase(), httpRequest);

        String annee = String.valueOf(LocalDate.now().getYear());
        verify(authService).createLinkedAccount(anyString(), anyString(),
                eq("etu-" + annee + "-000001@school.local"), anyString(), anyString(), eq("ELEVE"));
    }

    @Test
    @DisplayName("create suffixe le nom d'utilisateur quand il est deja pris")
    void createSuffixeUnNomDUtilisateurDejaPris() {
        matriculeLibre();
        saveEleveRenvoieSonArgument();
        when(studentMapper.toEntity(any())).thenReturn(eleve(null, null, null, null));
        when(authService.createLinkedAccount(anyString(), anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(User.builder().id(50L).build());
        // « aicha.kamdem » et « aicha.kamdem2 » sont pris, « aicha.kamdem3 » est libre.
        when(userRepository.existsByUsername("aicha.kamdem")).thenReturn(true);
        when(userRepository.existsByUsername("aicha.kamdem2")).thenReturn(true);
        when(userRepository.existsByUsername("aicha.kamdem3")).thenReturn(false);

        studentService.create(demandeDeBase(), httpRequest);

        verify(authService).createLinkedAccount(eq("aicha.kamdem3"), eq("Eleve@123"), any(),
                anyString(), anyString(), eq("ELEVE"));
    }

    @Test
    @DisplayName("create n'ecrase jamais le mot de passe fourni par Eleve@123")
    void createNEcrasePasLeMotDePasseFourni() {
        matriculeLibre();
        saveEleveRenvoieSonArgument();
        when(studentMapper.toEntity(any())).thenReturn(eleve(null, null, null, null));
        when(authService.createLinkedAccount(anyString(), anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(User.builder().id(50L).build());
        StudentRequest requete = demandeDeBase();
        requete.setPassword("MonMotDePasse!2026");

        studentService.create(requete, httpRequest);

        verify(authService, never()).createLinkedAccount(anyString(), eq("Eleve@123"),
                any(), anyString(), anyString(), anyString());
    }

    // ------------------------------------------------------------- parent

    @Test
    @DisplayName("create rattache le parent existant designe par parentId")
    void createRattacheLeParentExistant() {
        matriculeLibre();
        saveEleveRenvoieSonArgument();
        when(studentMapper.toEntity(any())).thenReturn(eleve(null, null, null, null));
        Parent p = parent(3L);
        when(parentRepository.findById(3L)).thenReturn(Optional.of(p));
        StudentRequest requete = demandeDeBase();
        requete.setParentId(3L);

        StudentResponse reponse = studentService.create(requete, httpRequest);

        assertThat(reponse.getParent()).isNotNull();
        assertThat(reponse.getParent().getId()).isEqualTo(3L);
        // Aucun parent ne doit etre cree puisqu'il existe deja.
        verify(parentRepository, never()).save(any(Parent.class));
    }

    @Test
    @DisplayName("create avec un parentId inconnu leve une exception et n'ecrit rien")
    void createAvecParentInconnuLeveUneException() {
        matriculeLibre();
        when(studentMapper.toEntity(any())).thenReturn(eleve(null, null, null, null));
        when(parentRepository.findById(404L)).thenReturn(Optional.empty());
        StudentRequest requete = demandeDeBase();
        requete.setParentId(404L);

        assertThatThrownBy(() -> studentService.create(requete, httpRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Parent");

        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    @DisplayName("create cree un parent quand des champs parent sont fournis sans parentId")
    void createCreeUnParentSiDesChampsSontFournis() {
        matriculeLibre();
        saveEleveRenvoieSonArgument();
        when(studentMapper.toEntity(any())).thenReturn(eleve(null, null, null, null));
        when(parentRepository.findByPhone("66000000")).thenReturn(Optional.empty());
        when(parentRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(parentRepository.save(any(Parent.class))).thenAnswer(inv -> {
            Parent p = inv.getArgument(0);
            p.setId(9L);
            return p;
        });
        StudentRequest requete = demandeDeBase();
        requete.setParentFirstName("Harouna");
        requete.setParentLastName("Siby");
        requete.setParentPhone("66000000");

        StudentResponse reponse = studentService.create(requete, httpRequest);

        verify(parentRepository).save(any(Parent.class));
        assertThat(reponse.getParent()).isNotNull();
    }

    @Test
    @DisplayName("create reutilise le parent retrouve par telephone au lieu d'en creer un second")
    void createReutiliseLeParentTrouveParTelephone() {
        matriculeLibre();
        saveEleveRenvoieSonArgument();
        when(studentMapper.toEntity(any())).thenReturn(eleve(null, null, null, null));
        Parent existant = parent(4L);
        when(parentRepository.findByPhone("66000000")).thenReturn(Optional.of(existant));
        StudentRequest requete = demandeDeBase();
        requete.setParentFirstName("Harouna");
        requete.setParentPhone("66000000");

        StudentResponse reponse = studentService.create(requete, httpRequest);

        // Un second parent pour le meme numero creerait un doublon de dossier.
        verify(parentRepository, never()).save(any(Parent.class));
        assertThat(reponse.getParent().getId()).isEqualTo(4L);
    }

    @Test
    @DisplayName("create n'ajoute aucun parent si aucun champ parent n'est fourni")
    void createSansChampParentNAjouteAucunParent() {
        matriculeLibre();
        saveEleveRenvoieSonArgument();
        when(studentMapper.toEntity(any())).thenReturn(eleve(null, null, null, null));

        StudentResponse reponse = studentService.create(demandeDeBase(), httpRequest);

        verifyNoInteractions(parentRepository);
        assertThat(reponse.getParent()).isNull();
    }

    @Test
    @DisplayName("create ne cree le compte PARENT que si createParentAccount=TRUE")
    void createCreeLeCompteParentUniquementSurDemande() {
        matriculeLibre();
        saveEleveRenvoieSonArgument();
        when(studentMapper.toEntity(any())).thenReturn(eleve(null, null, null, null));
        Parent p = parent(3L);
        when(parentRepository.findById(3L)).thenReturn(Optional.of(p));
        when(parentRepository.save(any(Parent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(authService.createLinkedAccount(anyString(), anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(User.builder().id(51L).build());
        StudentRequest requete = demandeDeBase();
        requete.setParentId(3L);
        requete.setCreateParentAccount(Boolean.TRUE);

        studentService.create(requete, httpRequest);

        // Le parent doit etre lie avec le role PARENT, et non ELEVE.
        verify(authService).createLinkedAccount(eq("harouna.siby"), eq("Parent@123"),
                eq("h.siby@school.td"), eq("Harouna"), eq("Siby"), eq("PARENT"));
    }

    @Test
    @DisplayName("create ne cree pas de compte parent si le parent en a deja un")
    void createNeRecreePasUnCompteParentExistant() {
        matriculeLibre();
        saveEleveRenvoieSonArgument();
        when(studentMapper.toEntity(any())).thenReturn(eleve(null, null, null, null));
        Parent p = parent(3L);
        p.setUser(User.builder().id(70L).build());
        when(parentRepository.findById(3L)).thenReturn(Optional.of(p));
        StudentRequest requete = demandeDeBase();
        requete.setParentId(3L);
        requete.setCreateParentAccount(Boolean.TRUE);

        studentService.create(requete, httpRequest);

        // Le compte eleve (par defaut) est cree, mais surtout PAS de second compte parent.
        verify(authService, never()).createLinkedAccount(anyString(), anyString(), any(),
                anyString(), anyString(), eq("PARENT"));
    }

    // ------------------------------------------------------------------ update

    @Test
    @DisplayName("update conserve le matricule, le statut et la photo")
    void updateConserveMatriculeStatutEtPhoto() {
        Student existant = eleve(6L, "ETU-2026-000006", StudentStatus.SUSPENDED, null);
        existant.setPhoto("/uploads/students/a.png");
        when(studentRepository.findById(6L)).thenReturn(Optional.of(existant));
        saveEleveRenvoieSonArgument();

        studentService.update(6L, demandeDeBase(), httpRequest);

        ArgumentCaptor<Student> captor = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).save(captor.capture());
        assertThat(captor.getValue().getMatricule()).isEqualTo("ETU-2026-000006");
        assertThat(captor.getValue().getStatus()).isEqualTo(StudentStatus.SUSPENDED);
        assertThat(captor.getValue().getPhoto()).isEqualTo("/uploads/students/a.png");
    }

    @Test
    @DisplayName("update ne cree AUCUN compte si le drapeau est absent (asymetrie avec create)")
    void updateAvecDrapeauAbsentNeCreeAucunCompte() {
        Student existant = eleve(6L, "ETU-2026-000006", StudentStatus.ACTIVE, null);
        when(studentRepository.findById(6L)).thenReturn(Optional.of(existant));
        saveEleveRenvoieSonArgument();

        studentService.update(6L, demandeDeBase(), httpRequest);

        // ⚠️ Asymetrie volontaire : contrairement a create, rien n'est cree sans drapeau.
        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("update cree le compte sur drapeau TRUE quand l'eleve n'en a pas")
    void updateCreeLeCompteSurDrapeauTrue() {
        Student existant = eleve(6L, "ETU-2026-000006", StudentStatus.ACTIVE, null);
        when(studentRepository.findById(6L)).thenReturn(Optional.of(existant));
        saveEleveRenvoieSonArgument();
        when(authService.createLinkedAccount(anyString(), anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn(User.builder().id(50L).build());
        StudentRequest requete = demandeDeBase();
        requete.setCreateUserAccount(Boolean.TRUE);

        StudentResponse reponse = studentService.update(6L, requete, httpRequest);

        verify(authService).createLinkedAccount(eq("aicha.kamdem"), eq("Eleve@123"), any(),
                anyString(), anyString(), eq("ELEVE"));
        assertThat(reponse.getHasAccount()).isTrue();
    }

    @Test
    @DisplayName("update ne remplace PAS un compte existant meme si le drapeau vaut TRUE")
    void updateNeRemplacePasUnCompteExistant() {
        Student existant = eleve(6L, "ETU-2026-000006", StudentStatus.ACTIVE, null);
        User deja = User.builder().id(50L).username("aicha.kamdem").build();
        existant.setUser(deja);
        when(studentRepository.findById(6L)).thenReturn(Optional.of(existant));
        saveEleveRenvoieSonArgument();
        StudentRequest requete = demandeDeBase();
        requete.setCreateUserAccount(Boolean.TRUE);

        studentService.update(6L, requete, httpRequest);

        // Recreer le compte ecraserait le mot de passe et le lien vers les donnees existantes.
        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("update recopie les nouvelles donnees du parent sur le dossier existant")
    void updateRecopieLesDonneesDuParent() {
        Student existant = eleve(6L, "ETU-2026-000006", StudentStatus.ACTIVE, null);
        Parent p = parent(3L);
        existant.setParent(p);
        when(studentRepository.findById(6L)).thenReturn(Optional.of(existant));
        saveEleveRenouvele();
        when(parentRepository.save(any(Parent.class))).thenAnswer(inv -> inv.getArgument(0));
        StudentRequest requete = demandeDeBase();
        requete.setParentPhone("66112233");

        studentService.update(6L, requete, httpRequest);

        assertThat(existant.getParent().getPhone()).isEqualTo("66112233");
    }

    private void saveEleveRenouvele() {
        when(studentRepository.save(any(Student.class))).thenAnswer(inv -> {
            Student s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(6L);
            }
            return s;
        });
    }

    @Test
    @DisplayName("update sur un eleve inconnu leve une exception")
    void updateSurUnEleveInconnuLeveUneException() {
        when(studentRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentService.update(404L, demandeDeBase(), httpRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("404");
    }

    // ------------------------------------------------------------ parcours

    @Test
    @DisplayName("transfer change la classe, remet ACTIVE et historise l'ancienne et la nouvelle")
    void transfertChangeLaClasseEtHistorise() {
        SchoolClass ancienne = classe(2L, "5eme B", EducationCycle.COLLEGE);
        SchoolClass nouvelle = classe(3L, "6eme A", EducationCycle.COLLEGE);
        Student existant = eleve(6L, "ETU-2026-000006", StudentStatus.INACTIVE, ancienne);
        when(studentRepository.findById(6L)).thenReturn(Optional.of(existant));
        when(schoolClassRepository.findById(3L)).thenReturn(Optional.of(nouvelle));
        saveEleveRenouvele();

        ArgumentCaptor<StudentHistory> captor = ArgumentCaptor.forClass(StudentHistory.class);
        StudentResponse reponse = studentService.transfer(6L, 3L, "Reussite", httpRequest);
        verify(studentHistoryRepository).save(captor.capture());

        assertThat(reponse.getClassName()).isEqualTo("6eme A");
        assertThat(reponse.getStatus()).isEqualTo(StudentStatus.ACTIVE);
        assertThat(captor.getValue().getAction()).isEqualTo(StudentHistoryAction.TRANSFER);
        assertThat(captor.getValue().getFromClass()).isEqualTo("5eme B");
        assertThat(captor.getValue().getToClass()).isEqualTo("6eme A");
        assertThat(captor.getValue().getReason()).isEqualTo("Reussite");
    }

    @Test
    @DisplayName("transfer refuse un eleve radie")
    void transfertRefuseUnEleveRadie() {
        Student radie = eleve(6L, "ETU-2026-000006", StudentStatus.RADIATED, classe(2L, "5eme B", null));
        when(studentRepository.findById(6L)).thenReturn(Optional.of(radie));

        assertThatThrownBy(() -> studentService.transfer(6L, 3L, "x", httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("radié");

        // Un radie ne peut etre deplace : seules la reinscription ou une nouvelle inscription le permettent.
        verify(studentRepository, never()).save(any(Student.class));
        verifyNoInteractions(studentHistoryRepository);
    }

    @Test
    @DisplayName("transfer refuse un transfert vers la classe deja occupee")
    void transfertRefuseLaMemeClasse() {
        SchoolClass memeClasse = classe(3L, "6eme A", EducationCycle.COLLEGE);
        Student existant = eleve(6L, "ETU-2026-000006", StudentStatus.ACTIVE, memeClasse);
        when(studentRepository.findById(6L)).thenReturn(Optional.of(existant));
        when(schoolClassRepository.findById(3L)).thenReturn(Optional.of(memeClasse));

        assertThatThrownBy(() -> studentService.transfer(6L, 3L, "x", httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("déjà dans la classe");

        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    @DisplayName("radiate passe le statut a RADIATED et historise")
    void radiatePasseLeStatutEtHistorise() {
        Student existant = eleve(6L, "ETU-2026-000006", StudentStatus.ACTIVE,
                classe(3L, "6eme A", EducationCycle.COLLEGE));
        when(studentRepository.findById(6L)).thenReturn(Optional.of(existant));
        saveEleveRenouvele();

        ArgumentCaptor<StudentHistory> captor = ArgumentCaptor.forClass(StudentHistory.class);
        StudentResponse reponse = studentService.radiate(6L, "Deménagement", httpRequest);
        verify(studentHistoryRepository).save(captor.capture());

        assertThat(reponse.getStatus()).isEqualTo(StudentStatus.RADIATED);
        assertThat(captor.getValue().getAction()).isEqualTo(StudentHistoryAction.RADIATION);
        assertThat(captor.getValue().getFromClass()).isEqualTo("6eme A");
        assertThat(captor.getValue().getToClass()).isNull();
    }

    @Test
    @DisplayName("radiate refuse de radier deux fois")
    void radiateRefuseUneDoubleRadiation() {
        Student radie = eleve(6L, "ETU-2026-000006", StudentStatus.RADIATED, null);
        when(studentRepository.findById(6L)).thenReturn(Optional.of(radie));

        assertThatThrownBy(() -> studentService.radiate(6L, "x", httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("déjà radié");

        verify(studentRepository, never()).save(any(Student.class));
        verifyNoInteractions(studentHistoryRepository);
    }

    @Test
    @DisplayName("reinscribe accepte un radie, force ACTIVE et remet la date du jour")
    void reinscribeUnRadie() {
        Student radie = eleve(6L, "ETU-2026-000006", StudentStatus.RADIATED, null);
        SchoolClass nouvelle = classe(3L, "6eme A", EducationCycle.COLLEGE);
        when(studentRepository.findById(6L)).thenReturn(Optional.of(radie));
        when(schoolClassRepository.findById(3L)).thenReturn(Optional.of(nouvelle));
        saveEleveRenouvele();

        ArgumentCaptor<StudentHistory> captor = ArgumentCaptor.forClass(StudentHistory.class);
        StudentResponse reponse = studentService.reinscribe(6L, 3L, "Retour", httpRequest);
        verify(studentHistoryRepository).save(captor.capture());

        assertThat(reponse.getStatus()).isEqualTo(StudentStatus.ACTIVE);
        assertThat(reponse.getEnrollmentDate()).isEqualTo(LocalDate.now());
        assertThat(captor.getValue().getAction()).isEqualTo(StudentHistoryAction.REINSCRIPTION);
        assertThat(captor.getValue().getFromClass()).isNull();
        assertThat(captor.getValue().getToClass()).isEqualTo("6eme A");
    }

    @Test
    @DisplayName("reinscribe accepte un inactif")
    void reinscribeAccepteUnInactif() {
        Student inactif = eleve(6L, "ETU-2026-000006", StudentStatus.INACTIVE, null);
        when(studentRepository.findById(6L)).thenReturn(Optional.of(inactif));
        saveEleveRenouvele();

        StudentResponse reponse = studentService.reinscribe(6L, null, "Retour", httpRequest);

        assertThat(reponse.getStatus()).isEqualTo(StudentStatus.ACTIVE);
    }

    @Test
    @DisplayName("reinscribe refuse un eleve deja actif")
    void reinscribeRefuseUnEleveActif() {
        Student actif = eleve(6L, "ETU-2026-000006", StudentStatus.ACTIVE, null);
        when(studentRepository.findById(6L)).thenReturn(Optional.of(actif));

        assertThatThrownBy(() -> studentService.reinscribe(6L, null, "x", httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("radié ou inactif");

        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    @DisplayName("reinscribe refuse un eleve diplome (seuls radie et inactif sont acceptes)")
    void reinscribeRefuseUnDiplome() {
        Student diplome = eleve(6L, "ETU-2026-000006", StudentStatus.GRADUATED, null);
        when(studentRepository.findById(6L)).thenReturn(Optional.of(diplome));

        assertThatThrownBy(() -> studentService.reinscribe(6L, null, "x", httpRequest))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("recordHistory inscrit « Systeme » quand la requete est absente")
    void recordHistorySansRequeteInscritSysteme() {
        Student existant = eleve(6L, "ETU-2026-000006", StudentStatus.ACTIVE, null);
        when(studentRepository.findById(6L)).thenReturn(Optional.of(existant));
        saveEleveRenouvele();

        ArgumentCaptor<StudentHistory> captor = ArgumentCaptor.forClass(StudentHistory.class);
        studentService.radiate(6L, "x", null);
        verify(studentHistoryRepository).save(captor.capture());

        assertThat(captor.getValue().getRecordedBy()).isEqualTo("Système");
    }

    @Test
    @DisplayName("recordHistory utilise le nom du principal authentifie")
    void recordHistoryUtiliseLePrincipal() {
        Student existant = eleve(6L, "ETU-2026-000006", StudentStatus.ACTIVE, null);
        when(studentRepository.findById(6L)).thenReturn(Optional.of(existant));
        saveEleveRenouvele();
        Principal principal = () -> "admin";
        when(httpRequest.getUserPrincipal()).thenReturn(principal);

        ArgumentCaptor<StudentHistory> captor = ArgumentCaptor.forClass(StudentHistory.class);
        studentService.radiate(6L, "x", httpRequest);
        verify(studentHistoryRepository).save(captor.capture());

        assertThat(captor.getValue().getRecordedBy()).isEqualTo("admin");
    }

    @Test
    @DisplayName("historyOf exige que l'eleve existe puis renvoie l'historique")
    void historyOfExigeQueLEleveExiste() {
        when(studentRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentService.historyOf(404L))
                .isInstanceOf(ResourceNotFoundException.class);

        // On ne doit pas interroger l'historique pour un eleve inexistant.
        verifyNoInteractions(studentHistoryRepository);
    }

    @Test
    @DisplayName("historyOf renvoie les entrees de l'eleve, de la plus recente a la plus ancienne")
    void historyOfRenvoieLHistorique() {
        Student existant = eleve(6L, "ETU-2026-000006", StudentStatus.RADIATED, null);
        when(studentRepository.findById(6L)).thenReturn(Optional.of(existant));
        when(studentHistoryRepository.findByStudentIdOrderByCreatedAtDesc(6L)).thenReturn(List.of(
                StudentHistory.builder().id(2L).student(existant)
                        .action(StudentHistoryAction.RADIATION).reason("Apres transfert").build(),
                StudentHistory.builder().id(1L).student(existant)
                        .action(StudentHistoryAction.TRANSFER).reason("Reussite").build()));

        List<StudentHistoryResponse> historique = studentService.historyOf(6L);

        assertThat(historique).hasSize(2);
        assertThat(historique.get(0).getAction()).isEqualTo(StudentHistoryAction.RADIATION);
        assertThat(historique.get(1).getAction()).isEqualTo(StudentHistoryAction.TRANSFER);
    }

    // ----------------------------------------------------- photo et suppression

    @Test
    @DisplayName("uploadPhoto delegue au stockage avec le sous-repertoire students")
    void uploadPhotoDelegueAuStockage() {
        Student existant = eleve(6L, "ETU-2026-000006", StudentStatus.ACTIVE, null);
        when(studentRepository.findById(6L)).thenReturn(Optional.of(existant));
        saveEleveRenouvele();
        MultipartFile fichier = org.mockito.Mockito.mock(MultipartFile.class);
        when(fileStorageService.store(fichier, "students")).thenReturn("/uploads/students/n.png");

        StudentResponse reponse = studentService.uploadPhoto(6L, fichier);

        assertThat(reponse.getPhoto()).isEqualTo("/uploads/students/n.png");
    }

    @Test
    @DisplayName("delete supprime l'eleve avec flush")
    void deleteSupprimeAvecFlush() {
        Student existant = eleve(6L, "ETU-2026-000006", StudentStatus.ACTIVE, null);
        when(studentRepository.findById(6L)).thenReturn(Optional.of(existant));

        studentService.delete(6L, httpRequest);

        verify(studentRepository).delete(existant);
        // Le flush est indispensable : c'est lui qui fait remonter la violation d'integrite.
        verify(studentRepository).flush();
    }

    @Test
    @DisplayName("delete traduit une violation d'integrite en message metier suggerant « Radier »")
    void deleteTraduitUneViolationDIntegrite() {
        Student existant = eleve(6L, "ETU-2026-000006", StudentStatus.ACTIVE, null);
        when(studentRepository.findById(6L)).thenReturn(Optional.of(existant));
        doThrow(new DataIntegrityViolationException("FK")).when(studentRepository).flush();

        assertThatThrownBy(() -> studentService.delete(6L, httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Radier");
    }

    @Test
    @DisplayName("delete journalise avant de supprimer")
    void deleteJournaliseAvantDeSupprimer() {
        Student existant = eleve(6L, "ETU-2026-000006", StudentStatus.ACTIVE, null);
        when(studentRepository.findById(6L)).thenReturn(Optional.of(existant));

        studentService.delete(6L, httpRequest);

        verify(auditService).log(eq("DELETE"), eq("Student"), eq(6L),
                contains("Aicha Kamdem"), eq(httpRequest));
    }

    // ---------------------------------------------------------------- lecture

    @Test
    @DisplayName("search impose le tri par nom croissant et transmet tous les filtres")
    void searchImposeLeTriEtLesFiltres() {
        Page<Student> page = new PageImpl<>(List.of(eleve(6L, "ETU-2026-000006", StudentStatus.ACTIVE, null)),
                PageRequest.of(0, 20), 1L);
        when(studentRepository.search(eq("kam"), eq(3L), eq(StudentStatus.ACTIVE),
                eq(EducationCycle.COLLEGE), any(Pageable.class))).thenReturn(page);

        PageResponse<StudentResponse> reponse = studentService.search(
                "kam", 3L, StudentStatus.ACTIVE, EducationCycle.COLLEGE, 0, 20);

        assertThat(reponse.getContent()).hasSize(1);
        assertThat(reponse.getTotalElements()).isEqualTo(1L);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(studentRepository).search(eq("kam"), eq(3L), eq(StudentStatus.ACTIVE),
                eq(EducationCycle.COLLEGE), captor.capture());
        assertThat(captor.getValue().getSort().getOrderFor("lastName")).isNotNull();
        assertThat(captor.getValue().getSort().getOrderFor("lastName").isAscending()).isTrue();
    }

    @Test
    @DisplayName("search accepte cinq filtres nuls")
    void searchAccepteDesFiltresNuls() {
        when(studentRepository.search(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0L));

        PageResponse<StudentResponse> reponse = studentService.search(null, null, null, null, 0, 10);

        assertThat(reponse.getContent()).isEmpty();
    }

    @Test
    @DisplayName("getById renvoie l'eleve demande avec son compte signale")
    void getByIdRenvoieLEleve() {
        Student existant = eleve(6L, "ETU-2026-000006", StudentStatus.ACTIVE, null);
        existant.setUser(User.builder().id(50L).build());
        when(studentRepository.findById(6L)).thenReturn(Optional.of(existant));

        StudentResponse reponse = studentService.getById(6L);

        assertThat(reponse.getId()).isEqualTo(6L);
        assertThat(reponse.getMatricule()).isEqualTo("ETU-2026-000006");
        assertThat(reponse.getHasAccount()).isTrue();
    }

    @Test
    @DisplayName("findById leve une exception nommant « Élève » et l'identifiant")
    void findByIdLeveUneExceptionNommee() {
        when(studentRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> studentService.findById(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Élève")
                .hasMessageContaining("404");
    }

    @Test
    @DisplayName("generateMatricule est expose publiquement pour la previsualisation du formulaire")
    void generateMatriculeEstPublic() {
        when(studentRepository.count()).thenReturn(0L);
        when(studentRepository.existsByMatricule(anyString())).thenReturn(false);

        String matricule = studentService.generateMatricule();

        String annee = String.valueOf(LocalDate.now().getYear());
        assertThat(matricule).isEqualTo("ETU-" + annee + "-000001");
    }

    @Test
    @DisplayName("create sauvegarde exactement une fois")
    void createNeSauvegardeQuUneFois() {
        matriculeLibre();
        saveEleveRenvoieSonArgument();
        when(studentMapper.toEntity(any())).thenReturn(eleve(null, null, null, null));

        studentService.create(demandeDeBase(), httpRequest);

        verify(studentRepository, times(1)).save(any(Student.class));
    }
}
