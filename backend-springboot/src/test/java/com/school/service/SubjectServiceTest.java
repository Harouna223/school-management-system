package com.school.service;

import com.school.dto.request.AssignmentRequest;
import com.school.dto.request.SubjectRequest;
import com.school.dto.response.AssignmentResponse;
import com.school.dto.response.PageResponse;
import com.school.dto.response.SubjectResponse;
import com.school.entity.SchoolClass;
import com.school.entity.Subject;
import com.school.entity.SubjectAssignment;
import com.school.entity.Teacher;
import com.school.enums.ContractType;
import com.school.enums.Gender;
import com.school.enums.TeacherStatus;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.mapper.SubjectMapper;
import com.school.repository.SubjectAssignmentRepository;
import com.school.repository.SubjectRepository;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Module matieres : CRUD + affectations enseignant -> matiere -> classe.
 *
 * <p>Les affectations sont ce qui relie un enseignant a ce qu'il a le droit de noter : une affectation
 * en double ou manquante fausse la saisie des notes sans jamais casser la compilation.
 *
 * <p><b>Trois points epingles :</b>
 * <ol>
 *   <li><b>Ordre de priorite de {@code listAssignments}</b> : le filtre {@code teacherId} l'emporte sur
 *       {@code classId}, et l'absence des deux renvoie {@code findAll()}. Intervertir les deux branches
 *       reste invisible tant que personne ne passe les deux parametres a la fois.</li>
 *   <li><b>Le doublon d'affectation est refuse AVANT tout chargement</b> : enseignant, matiere et classe
 *       ne sont meme pas resolus quand le triplet existe deja. Une erreur ici renverrait
 *       « Enseignant introuvable » au lieu du vrai motif.</li>
 *   <li><b>{@code delete} d'une matiere n'a AUCUNE garde</b>, contrairement a {@code ClassService.delete}
 *       (eleves) ou a la suppression d'un niveau (classes). Une matiere referencee par des notes ou des
 *       affectations part donc et laisse la base decider. Comportement epingle tel quel, pas souhaite.</li>
 * </ol>
 *
 * <p>⚠️ {@code SubjectMapper} est un mock : {@code updateEntity()} ne fait <b>rien</b> par defaut. Il faut
 * un {@code doAnswer} reproduisant MapStruct, sinon toute assertion sur le resultat observe l'entite
 * d'origine et le test passe pour une raison absente de la production.
 *
 * <p><b>Controles negatifs effectues</b> (copie de travail, jamais le depot) :
 * garde de doublon d'affectation retiree =&gt; 1 echec ; controle d'unicite du code retire =&gt; 1 echec ;
 * priorite {@code teacherId}/{@code classId} intervertie =&gt; 1 echec.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubjectServiceTest {

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private SubjectAssignmentRepository assignmentRepository;

    @Mock
    private SubjectMapper subjectMapper;

    @Mock
    private TeacherService teacherService;

    @Mock
    private ClassService classService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private SubjectService subjectService;

    @Mock
    private HttpServletRequest httpRequest;

    // ------------------------------------------------------------------ helpers

    private Subject matiere(Long id, String nom, String code, Integer coefficient) {
        return Subject.builder().id(id).name(nom).code(code)
                .coefficient(coefficient).description("Description " + nom).build();
    }

    private Teacher enseignant(Long id) {
        return Teacher.builder()
                .id(id).employeeNo("ENS-2026-0001")
                .firstName("Jean").lastName("Kamdem")
                .gender(Gender.MALE).hireDate(LocalDate.of(2026, 1, 1))
                .contractType(ContractType.CDI).salary(new BigDecimal("250000.00"))
                .status(TeacherStatus.ACTIVE)
                .build();
    }

    private SchoolClass classe(Long id, String nom) {
        return SchoolClass.builder().id(id).name(nom).code("CODE-" + id).build();
    }

    private SubjectRequest demande(String nom, String code, Integer coefficient) {
        return SubjectRequest.builder().name(nom).code(code).coefficient(coefficient)
                .description("Desc").build();
    }

    private AssignmentRequest affectation(Long teacherId, Long subjectId, Long classId) {
        return AssignmentRequest.builder()
                .teacherId(teacherId).subjectId(subjectId).classId(classId).build();
    }

    /** Reproduit le comportement MapStruct pour `updateEntity`, qui ne fait rien sur un mock. */
    private void mapperModifie(Subject cible) {
        doAnswer(inv -> {
            SubjectRequest r = inv.getArgument(0);
            cible.setName(r.getName());
            cible.setCode(r.getCode());
            cible.setCoefficient(r.getCoefficient());
            cible.setDescription(r.getDescription());
            return null;
        }).when(subjectMapper).updateEntity(any(SubjectRequest.class), any(Subject.class));
    }

    private void mapperCree(SubjectRequest request) {
        when(subjectMapper.toEntity(any(SubjectRequest.class))).thenAnswer(inv -> {
            SubjectRequest r = inv.getArgument(0);
            return Subject.builder()
                    .name(r.getName()).code(r.getCode())
                    .coefficient(r.getCoefficient()).description(r.getDescription())
                    .build();
        });
    }

    // ================================================================== lecture

    @Test
    @DisplayName("search trie par nom croissant et transmet le filtre")
    void searchTrieParNomEtTransmetLeFiltre() {
        Page<Subject> page = new PageImpl<>(
                List.of(matiere(1L, "Mathematiques", "MATH", 4)),
                PageRequest.of(0, 20, Sort.by("name").ascending()), 1L);
        when(subjectRepository.search(eq("math"), any(Pageable.class))).thenReturn(page);

        PageResponse<SubjectResponse> reponse = subjectService.search("math", 0, 20);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(subjectRepository).search(eq("math"), captor.capture());
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by("name").ascending());
        assertThat(reponse.getContent()).hasSize(1);
        assertThat(reponse.getContent().get(0).getCoefficient()).isEqualTo(4);
    }

    @Test
    @DisplayName("findAll trie par nom croissant")
    void findAllTrieParNom() {
        when(subjectRepository.findAll(Sort.by("name")))
                .thenReturn(List.of(matiere(1L, "Anglais", "ANG", 2), matiere(2L, "Mathematiques", "MATH", 4)));

        List<SubjectResponse> matieres = subjectService.findAll();

        assertThat(matieres).extracting(SubjectResponse::getName)
                .containsExactly("Anglais", "Mathematiques");
        verify(subjectRepository).findAll(Sort.by("name"));
    }

    @Test
    @DisplayName("getById expose la matiere")
    void getByIdExposeLaMatiere() {
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(matiere(1L, "Mathematiques", "MATH", 4)));

        SubjectResponse reponse = subjectService.getById(1L);

        assertThat(reponse.getName()).isEqualTo("Mathematiques");
        assertThat(reponse.getCode()).isEqualTo("MATH");
    }

    @Test
    @DisplayName("getById sur une matiere inconnue nomme la ressource")
    void getByIdSurUneMatiereInconnue() {
        when(subjectRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subjectService.getById(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Matière")
                .hasMessageContaining("404");
    }

    @Test
    @DisplayName("la lecture n'appelle jamais le mapper")
    void laLectureNAppellePasLeMapper() {
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(matiere(1L, "Mathematiques", "MATH", 4)));

        subjectService.getById(1L);

        verifyNoInteractions(subjectMapper);
    }

    // ================================================================== creation matiere

    @Test
    @DisplayName("create refuse un code deja utilise")
    void createRefuseUnCodeDejaUtilise() {
        when(subjectRepository.existsByCode("MATH")).thenReturn(true);

        assertThatThrownBy(() -> subjectService.create(demande("Mathematiques", "MATH", 4), httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MATH");

        verify(subjectRepository, never()).save(any(Subject.class));
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("create enregistre et journalise")
    void createEnregistre() {
        mapperCree(demande("Mathematiques", "MATH", 4));
        when(subjectRepository.existsByCode("MATH")).thenReturn(false);
        when(subjectRepository.save(any(Subject.class))).thenAnswer(inv -> {
            Subject s = inv.getArgument(0);
            s.setId(7L);
            return s;
        });

        SubjectResponse reponse = subjectService.create(demande("Mathematiques", "MATH", 4), httpRequest);

        assertThat(reponse.getId()).isEqualTo(7L);
        assertThat(reponse.getCoefficient()).isEqualTo(4);
        verify(auditService).log(eq("CREATE"), eq("Subject"), eq(7L), contains("Mathematiques"), eq(httpRequest));
    }

    @Test
    @DisplayName("create transmet bien le coefficient du client (non recalcule)")
    void createTransmetLeCoefficient() {
        mapperCree(demande("Mathematiques", "MATH", 6));
        when(subjectRepository.existsByCode("MATH")).thenReturn(false);
        when(subjectRepository.save(any(Subject.class))).thenAnswer(inv -> inv.getArgument(0));

        subjectService.create(demande("Mathematiques", "MATH", 6), httpRequest);

        ArgumentCaptor<Subject> captor = ArgumentCaptor.forClass(Subject.class);
        verify(subjectRepository).save(captor.capture());
        // Le coefficient vient de la requete : aucune valeur par defaut appliquee ici.
        assertThat(captor.getValue().getCoefficient()).isEqualTo(6);
    }

    // ================================================================== modification matiere

    @Test
    @DisplayName("update recopie les champs modifiables et journalise")
    void updateRecopieLesChamps() {
        Subject existante = matiere(1L, "Mathematiques", "MATH", 4);
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(existante));
        when(subjectRepository.save(any(Subject.class))).thenAnswer(inv -> inv.getArgument(0));
        mapperModifie(existante);

        SubjectResponse reponse = subjectService.update(1L, demande("Maths", "MATH2", 5), httpRequest);

        assertThat(reponse.getName()).isEqualTo("Maths");
        assertThat(reponse.getCode()).isEqualTo("MATH2");
        assertThat(reponse.getCoefficient()).isEqualTo(5);
        verify(auditService).log(eq("UPDATE"), eq("Subject"), eq(1L), contains("Maths"), eq(httpRequest));
    }

    @Test
    @DisplayName("update documente l'ABSENCE de controle de doublon sur le code")
    void updateNaAucunControleDeDoublonSurLeCode() {
        Subject existante = matiere(1L, "Mathematiques", "MATH", 4);
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(existante));
        when(subjectRepository.save(any(Subject.class))).thenAnswer(inv -> inv.getArgument(0));
        mapperModifie(existante);

        // « ANG » appartient deja a une autre matiere : l'API accepte et laisse la contrainte unique
        // de la base refuser. Comportement epingle, pas souhaite.
        subjectService.update(1L, demande("Mathematiques", "ANG", 4), httpRequest);

        verify(subjectRepository, never()).existsByCode(any());
        assertThat(existante.getCode()).isEqualTo("ANG");
    }

    @Test
    @DisplayName("update sur une matiere inconnue leve une exception")
    void updateSurUneMatiereInconnue() {
        when(subjectRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subjectService.update(404L, demande("X", "X", 1), httpRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Matière");
    }

    // ================================================================== suppression matiere

    @Test
    @DisplayName("delete supprime et journalise, SANS aucune garde de reference")
    void deleteSupprimeSansGarde() {
        Subject existante = matiere(1L, "Mathematiques", "MATH", 4);
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(existante));

        subjectService.delete(1L, httpRequest);

        verify(subjectRepository).delete(existante);
        verify(auditService).log(eq("DELETE"), eq("Subject"), eq(1L), contains("Mathematiques"), eq(httpRequest));
        // Comportement epingle : aucune verification de notes ni d'affectations avant suppression.
        verifyNoInteractions(assignmentRepository);
    }

    @Test
    @DisplayName("delete sur une matiere inconnue leve une exception")
    void deleteSurUneMatiereInconnue() {
        when(subjectRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subjectService.delete(404L, httpRequest))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(subjectRepository, never()).delete(any(Subject.class));
    }

    // ================================================================== liste des affectations

    @Test
    @DisplayName("listAssignments filtre par enseignant quand teacherId est fourni")
    void listAssignmentsFiltreParEnseignant() {
        SubjectAssignment a = SubjectAssignment.builder()
                .id(1L).teacher(enseignant(1L)).subject(matiere(2L, "Mathematiques", "MATH", 4))
                .schoolClass(classe(3L, "6eme A")).build();
        when(assignmentRepository.findByTeacherId(1L)).thenReturn(List.of(a));

        List<AssignmentResponse> reponses = subjectService.listAssignments(1L, null);

        assertThat(reponses).hasSize(1);
        assertThat(reponses.get(0).getTeacherName()).isEqualTo("Jean Kamdem");
        verify(assignmentRepository).findByTeacherId(1L);
        verify(assignmentRepository, never()).findAll();
    }

    @Test
    @DisplayName("listAssignments filtre par classe quand seul classId est fourni")
    void listAssignmentsFiltreParClasse() {
        when(assignmentRepository.findBySchoolClassId(3L)).thenReturn(List.of());

        subjectService.listAssignments(null, 3L);

        verify(assignmentRepository).findBySchoolClassId(3L);
        verify(assignmentRepository, never()).findAll();
    }

    @Test
    @DisplayName("listAssignments donne la PRIORITE a teacherId quand les deux sont fournis")
    void listAssignmentsDonneLaPrioriteAuTeacher() {
        when(assignmentRepository.findByTeacherId(1L)).thenReturn(List.of());

        subjectService.listAssignments(1L, 3L);

        // La classe est ignoree : intervertir les deux branches reste invisible sans ce test.
        verify(assignmentRepository).findByTeacherId(1L);
        verify(assignmentRepository, never()).findBySchoolClassId(any());
    }

    @Test
    @DisplayName("listAssignments renvoie tout quand aucun filtre n'est fourni")
    void listAssignmentsSansFiltre() {
        when(assignmentRepository.findAll()).thenReturn(List.of());

        subjectService.listAssignments(null, null);

        verify(assignmentRepository).findAll();
        verify(assignmentRepository, never()).findByTeacherId(any());
        verify(assignmentRepository, never()).findBySchoolClassId(any());
    }

    // ================================================================== affectation

    @Test
    @DisplayName("assign refuse un doublon AVANT tout chargement")
    void assignRefuseUnDoublonSansCharger() {
        when(assignmentRepository.existsByTeacherIdAndSubjectIdAndSchoolClassId(1L, 2L, 3L))
                .thenReturn(true);

        assertThatThrownBy(() -> subjectService.assign(affectation(1L, 2L, 3L), httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("déjà affecté");

        verify(assignmentRepository, never()).save(any(SubjectAssignment.class));
        // Si le doublon etait verifie apres les chargements, l'utilisateur verrait
        // « Enseignant introuvable » au lieu du vrai motif.
        verifyNoInteractions(teacherService, classService);
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("assign resout enseignant, matiere et classe puis journalise")
    void assignResoutLesTroisRelations() {
        when(assignmentRepository.existsByTeacherIdAndSubjectIdAndSchoolClassId(1L, 2L, 3L))
                .thenReturn(false);
        when(teacherService.findById(1L)).thenReturn(enseignant(1L));
        when(subjectRepository.findById(2L)).thenReturn(Optional.of(matiere(2L, "Mathematiques", "MATH", 4)));
        when(classService.findById(3L)).thenReturn(classe(3L, "6eme A"));
        when(assignmentRepository.save(any(SubjectAssignment.class))).thenAnswer(inv -> {
            SubjectAssignment a = inv.getArgument(0);
            a.setId(9L);
            return a;
        });

        AssignmentResponse reponse = subjectService.assign(affectation(1L, 2L, 3L), httpRequest);

        assertThat(reponse.getId()).isEqualTo(9L);
        assertThat(reponse.getTeacherName()).isEqualTo("Jean Kamdem");
        assertThat(reponse.getSubjectName()).isEqualTo("Mathematiques");
        assertThat(reponse.getClassName()).isEqualTo("6eme A");
        verify(auditService).log(eq("ASSIGN"), eq("SubjectAssignment"), eq(9L),
                contains("1 -> matière 2"), eq(httpRequest));
    }

    @Test
    @DisplayName("assign signale une matiere inexistante")
    void assignSignaleUneMatiereInexistante() {
        when(assignmentRepository.existsByTeacherIdAndSubjectIdAndSchoolClassId(1L, 404L, 3L))
                .thenReturn(false);
        when(teacherService.findById(1L)).thenReturn(enseignant(1L));
        when(subjectRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subjectService.assign(affectation(1L, 404L, 3L), httpRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Matière");

        verify(assignmentRepository, never()).save(any(SubjectAssignment.class));
    }

    @Test
    @DisplayName("assign signale un enseignant inexistant")
    void assignSignaleUnEnseignantInexistant() {
        when(assignmentRepository.existsByTeacherIdAndSubjectIdAndSchoolClassId(404L, 2L, 3L))
                .thenReturn(false);
        when(teacherService.findById(404L))
                .thenThrow(ResourceNotFoundException.of("Enseignant", 404L));

        assertThatThrownBy(() -> subjectService.assign(affectation(404L, 2L, 3L), httpRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Enseignant");

        verify(assignmentRepository, never()).save(any(SubjectAssignment.class));
    }

    @Test
    @DisplayName("assign signale une classe inexistante")
    void assignSignaleUneClasseInexistante() {
        when(assignmentRepository.existsByTeacherIdAndSubjectIdAndSchoolClassId(1L, 2L, 404L))
                .thenReturn(false);
        when(teacherService.findById(1L)).thenReturn(enseignant(1L));
        when(subjectRepository.findById(2L)).thenReturn(Optional.of(matiere(2L, "Mathematiques", "MATH", 4)));
        when(classService.findById(404L)).thenThrow(ResourceNotFoundException.of("Classe", 404L));

        assertThatThrownBy(() -> subjectService.assign(affectation(1L, 2L, 404L), httpRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Classe");
    }

    // ================================================================== retrait d'affectation

    @Test
    @DisplayName("unassign supprime et journalise le nom de l'enseignant")
    void unassignSupprimeEtJournalise() {
        SubjectAssignment a = SubjectAssignment.builder()
                .id(5L).teacher(enseignant(1L)).subject(matiere(2L, "Mathematiques", "MATH", 4))
                .schoolClass(classe(3L, "6eme A")).build();
        when(assignmentRepository.findById(5L)).thenReturn(Optional.of(a));

        subjectService.unassign(5L, httpRequest);

        verify(assignmentRepository).delete(a);
        verify(auditService).log(eq("UNASSIGN"), eq("SubjectAssignment"), eq(5L),
                contains("Jean Kamdem"), eq(httpRequest));
    }

    @Test
    @DisplayName("unassign sur une affectation inconnue leve une exception")
    void unassignSurUneAffectationInconnue() {
        when(assignmentRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subjectService.unassign(404L, httpRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Affectation");

        verify(assignmentRepository, never()).delete(any(SubjectAssignment.class));
    }

    // ================================================================== expose

    @Test
    @DisplayName("findById est expose et renvoie l'entite (utilisee par les notes)")
    void findByIdEstExpose() {
        Subject existante = matiere(1L, "Mathematiques", "MATH", 4);
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(existante));

        assertThat(subjectService.findById(1L)).isSameAs(existante);
    }
}
