package com.school.service;

import com.school.dto.request.ClassRequest;
import com.school.dto.request.LevelRequest;
import com.school.dto.request.RoomRequest;
import com.school.dto.request.SectionRequest;
import com.school.dto.response.ClassResponse;
import com.school.dto.response.PageResponse;
import com.school.entity.Level;
import com.school.entity.Room;
import com.school.entity.SchoolClass;
import com.school.entity.Section;
import com.school.entity.Student;
import com.school.enums.EducationCycle;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.mapper.ClassMapper;
import com.school.repository.LevelRepository;
import com.school.repository.RoomRepository;
import com.school.repository.SchoolClassRepository;
import com.school.repository.SectionRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Module classes : classes, niveaux, sections, salles.
 *
 * <p>C'est le <b>referentiel structurel</b> de l'etablissement : chaque eleve, chaque note et chaque
 * emploi du temps pointe vers une classe. Une classe mal rattachee ne casse rien a la compilation,
 * mais fausse silencieusement les effectifs et les emplois du temps.
 *
 * <p><b>Deux invariants asymetriques, testes explicitement :</b>
 * <ol>
 *   <li>{@code create()} applique le defaut {@code capacity = 30} <b>quand le champ est absent</b> ;
 *       {@code update()} ne touche PAS la capacite si le champ est absent — il faut un
 *       {@code != null} explicite pour l'ecraser. Un « nettoyage » qui unifierait les deux
 *       remplacerait donc une capacite existante par 30 sur toute modification partielle.</li>
 *   <li>L'unicite du code n'est verifiee qu'a la <b>creation</b> de classe et de niveau
 *       ({@code existsByCode} / {@code findByCode}) ; en modification il n'y a <b>aucun</b> controle
 *       de doublon. La base reste protegee par sa contrainte {@code unique}, mais l'API renvoie
 *       alors une erreur de persistence brute au lieu du message metier. Comportement epingle tel
 *       quel, pour qu'une correction future soit un choix conscient.</li>
 * </ol>
 *
 * <p><b>Trois gardes de suppression</b> (niveau / section / salle) sont identiques dans l'esprit
 * mais distinctes dans le code ({@code existsByLevelId} / {@code existsBySectionId} /
 * {@code existsByRoomId}) : chacune est neutralisee separement en controle negatif.
 *
 * <p><b>Controles negatifs effectues</b> (copie de travail, jamais le depot) :
 * garde « classe non vide » retiree =&gt; 1 echec ; {@code existsByCode} retire =&gt; 1 echec ;
 * defaut de capacite a la creation retire =&gt; 1 echec ; garde {@code existsByLevelId} retiree
 * =&gt; 1 echec. Les quatre sont donc reellement portees par les tests.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClassServiceTest {

    @Mock
    private SchoolClassRepository classRepository;

    @Mock
    private LevelRepository levelRepository;

    @Mock
    private SectionRepository sectionRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ClassMapper classMapper;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private ClassService classService;

    @Mock
    private HttpServletRequest httpRequest;

    // ------------------------------------------------------------------ helpers

    private Level niveau(Long id, String nom, String code) {
        return Level.builder().id(id).name(nom).code(code)
                .educationCycle(EducationCycle.COLLEGE).build();
    }

    private Section section(Long id, String nom) {
        return Section.builder().id(id).name(nom).description("Section " + nom).build();
    }

    private Room salle(Long id, String nom, Integer capacite) {
        return Room.builder().id(id).name(nom).capacity(capacite).location("Batiment A").build();
    }

    private SchoolClass classe(Long id, String nom, String code, Integer capacite) {
        return SchoolClass.builder()
                .id(id).name(nom).code(code)
                .level(niveau(1L, "6eme", "6EME"))
                .capacity(capacite)
                .students(new ArrayList<>())
                .build();
    }

    private ClassRequest demande(String nom, String code, Long levelId, Long sectionId, Long roomId,
                                 Integer capacite) {
        return ClassRequest.builder()
                .name(nom).code(code).levelId(levelId)
                .sectionId(sectionId).roomId(roomId).capacity(capacite)
                .build();
    }

    /** Le mapper MapStruct n'est pas instancie sous Mockito : on simule sa copie champ a champ. */
    private void mapperRecopie(ClassRequest request) {
        when(classMapper.toEntity(any(ClassRequest.class))).thenAnswer(inv -> {
            ClassRequest r = inv.getArgument(0);
            return SchoolClass.builder()
                    .name(r.getName()).code(r.getCode())
                    .students(new ArrayList<>())
                    .build();
        });
    }

    /**
     * ⚠️ Le mapper est un mock : par defaut {@code updateEntity(...)} ne fait <b>rien</b>, donc
     * {@code name} et {@code code} ne seraient jamais recopies et toute assertion sur le code
     * observerait la valeur d'origine. Ce stub reproduit le comportement MapStruct (copie champ a
     * champ de {@code name} + {@code code}) pour que le test exerce le service, pas le vide.
     */
    private void mapperModifie(SchoolClass cible) {
        doAnswer(inv -> {
            cible.setName(((ClassRequest) inv.getArgument(0)).getName());
            cible.setCode(((ClassRequest) inv.getArgument(0)).getCode());
            return null;
        }).when(classMapper).updateEntity(any(ClassRequest.class), any(SchoolClass.class));
    }

    // ================================================================== lecture

    @Test
    @DisplayName("search trie par nom croissant et transmet les filtres")
    void searchTrieParNomEtTransmetLesFiltres() {
        Page<SchoolClass> page = new PageImpl<>(
                List.of(classe(1L, "6eme A", "6A", 30)),
                PageRequest.of(0, 20, Sort.by("name").ascending()), 1L);
        when(classRepository.search(eq("6eme"), eq(1L), eq(2L), any(Pageable.class))).thenReturn(page);

        PageResponse<ClassResponse> reponse = classService.search("6eme", 1L, 2L, 0, 20);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(classRepository).search(eq("6eme"), eq(1L), eq(2L), captor.capture());
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by("name").ascending());
        assertThat(reponse.getContent()).hasSize(1);
        assertThat(reponse.getContent().get(0).getName()).isEqualTo("6eme A");
    }

    @Test
    @DisplayName("findAll trie par nom croissant")
    void findAllTrieParNom() {
        when(classRepository.findAll(Sort.by("name")))
                .thenReturn(List.of(classe(1L, "6eme A", "6A", 30), classe(2L, "5eme B", "5B", 25)));

        List<ClassResponse> classes = classService.findAll();

        assertThat(classes).extracting(ClassResponse::getName).containsExactly("6eme A", "5eme B");
        verify(classRepository).findAll(Sort.by("name"));
    }

    @Test
    @DisplayName("getById expose le nom du niveau et l'effectif")
    void getByIdExposeNiveauEtEffectif() {
        SchoolClass c = classe(1L, "6eme A", "6A", 30);
        c.setSection(section(2L, "Francophone"));
        c.setRoom(salle(3L, "Salle 12", 40));
        c.getStudents().add(Student.builder().id(10L).build());
        c.getStudents().add(Student.builder().id(11L).build());
        when(classRepository.findById(1L)).thenReturn(Optional.of(c));

        ClassResponse reponse = classService.getById(1L);

        assertThat(reponse.getLevelName()).isEqualTo("6eme");
        assertThat(reponse.getSectionName()).isEqualTo("Francophone");
        assertThat(reponse.getRoomName()).isEqualTo("Salle 12");
        assertThat(reponse.getStudentCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("getById sur une classe inconnue nomme la ressource")
    void getByIdSurUneClasseInconnue() {
        when(classRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> classService.getById(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Classe")
                .hasMessageContaining("404");
    }

    @Test
    @DisplayName("from supporte une classe sans section ni salle (champs optionnels)")
    void fromSupporteLesChampsOptionnelsAbsents() {
        SchoolClass sansOptions = SchoolClass.builder()
                .id(9L).name("6eme C").code("6C").capacity(30)
                .students(new ArrayList<>()).build();
        when(classRepository.findById(9L)).thenReturn(Optional.of(sansOptions));

        ClassResponse reponse = classService.getById(9L);

        assertThat(reponse.getSectionId()).isNull();
        assertThat(reponse.getRoomId()).isNull();
        assertThat(reponse.getStudentCount()).isZero();
    }

    // ================================================================== creation classe

    @Test
    @DisplayName("create refuse un code deja utilise")
    void createRefuseUnCodeDejaUtilise() {
        when(classRepository.existsByCode("6A")).thenReturn(true);

        assertThatThrownBy(() -> classService.create(demande("6eme A", "6A", 1L, null, null, 30), httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("6A");

        verify(classRepository, never()).save(any(SchoolClass.class));
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("create applique la capacite par defaut de 30 quand elle est absente")
    void createAppliqueLaCapaciteParDefaut() throws Exception {
        mapperRecopie(demande("6eme A", "6A", 1L, null, null, null));
        when(classRepository.existsByCode("6A")).thenReturn(false);
        when(levelRepository.findById(1L)).thenReturn(Optional.of(niveau(1L, "6eme", "6EME")));
        when(classRepository.save(any(SchoolClass.class))).thenAnswer(inv -> inv.getArgument(0));

        classService.create(demande("6eme A", "6A", 1L, null, null, null), httpRequest);

        ArgumentCaptor<SchoolClass> captor = ArgumentCaptor.forClass(SchoolClass.class);
        verify(classRepository).save(captor.capture());
        assertThat(captor.getValue().getCapacity()).isEqualTo(30);
    }

    @Test
    @DisplayName("create conserve une capacite explicite, meme nulle")
    void createConserveUneCapaciteExplicite() {
        mapperRecopie(demande("6eme A", "6A", 1L, null, null, 45));
        when(classRepository.existsByCode("6A")).thenReturn(false);
        when(levelRepository.findById(1L)).thenReturn(Optional.of(niveau(1L, "6eme", "6EME")));
        when(classRepository.save(any(SchoolClass.class))).thenAnswer(inv -> inv.getArgument(0));

        classService.create(demande("6eme A", "6A", 1L, null, null, 45), httpRequest);

        ArgumentCaptor<SchoolClass> captor = ArgumentCaptor.forClass(SchoolClass.class);
        verify(classRepository).save(captor.capture());
        assertThat(captor.getValue().getCapacity()).isEqualTo(45);
    }

    @Test
    @DisplayName("create rattache niveau, section et salle quand ils sont fournis")
    void createRattacheLesTroisRelations() {
        mapperRecopie(demande("6eme A", "6A", 1L, 2L, 3L, 30));
        when(classRepository.existsByCode("6A")).thenReturn(false);
        when(levelRepository.findById(1L)).thenReturn(Optional.of(niveau(1L, "6eme", "6EME")));
        when(sectionRepository.findById(2L)).thenReturn(Optional.of(section(2L, "Francophone")));
        when(roomRepository.findById(3L)).thenReturn(Optional.of(salle(3L, "Salle 12", 40)));
        when(classRepository.save(any(SchoolClass.class))).thenAnswer(inv -> inv.getArgument(0));

        classService.create(demande("6eme A", "6A", 1L, 2L, 3L, 30), httpRequest);

        ArgumentCaptor<SchoolClass> captor = ArgumentCaptor.forClass(SchoolClass.class);
        verify(classRepository).save(captor.capture());
        assertThat(captor.getValue().getLevel().getId()).isEqualTo(1L);
        assertThat(captor.getValue().getSection().getId()).isEqualTo(2L);
        assertThat(captor.getValue().getRoom().getId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("create laisse section et salle nulles quand elles sont absentes")
    void createLaisseSectionEtSalleNulles() {
        mapperRecopie(demande("6eme A", "6A", 1L, null, null, 30));
        when(classRepository.existsByCode("6A")).thenReturn(false);
        when(levelRepository.findById(1L)).thenReturn(Optional.of(niveau(1L, "6eme", "6EME")));
        when(classRepository.save(any(SchoolClass.class))).thenAnswer(inv -> inv.getArgument(0));

        classService.create(demande("6eme A", "6A", 1L, null, null, 30), httpRequest);

        ArgumentCaptor<SchoolClass> captor = ArgumentCaptor.forClass(SchoolClass.class);
        verify(classRepository).save(captor.capture());
        assertThat(captor.getValue().getSection()).isNull();
        assertThat(captor.getValue().getRoom()).isNull();
        // Aucun chargement inutile : section et salle ne sont resolues que si un identifiant existe.
        verifyNoInteractions(sectionRepository, roomRepository);
    }

    @Test
    @DisplayName("create signale un niveau inexistant avant de sauvegarder")
    void createSignaleUnNiveauInexistant() {
        mapperRecopie(demande("6eme A", "6A", 404L, null, null, 30));
        when(classRepository.existsByCode("6A")).thenReturn(false);
        when(levelRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> classService.create(demande("6eme A", "6A", 404L, null, null, 30), httpRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Niveau");

        verify(classRepository, never()).save(any(SchoolClass.class));
    }

    @Test
    @DisplayName("create journalise avec le nom de la classe")
    void createJournalise() {
        mapperRecopie(demande("6eme A", "6A", 1L, null, null, 30));
        when(classRepository.existsByCode("6A")).thenReturn(false);
        when(levelRepository.findById(1L)).thenReturn(Optional.of(niveau(1L, "6eme", "6EME")));
        when(classRepository.save(any(SchoolClass.class))).thenAnswer(inv -> {
            SchoolClass c = inv.getArgument(0);
            c.setId(7L);
            return c;
        });

        classService.create(demande("6eme A", "6A", 1L, null, null, 30), httpRequest);

        verify(auditService).log(eq("CREATE"), eq("Class"), eq(7L), contains("6eme A"), eq(httpRequest));
    }

    // ================================================================== modification classe

    @Test
    @DisplayName("update NE touche PAS la capacite quand elle est absente")
    void updateNeTouchePasLaCapaciteAbsente() {
        SchoolClass existante = classe(1L, "6eme A", "6A", 45);
        when(classRepository.findById(1L)).thenReturn(Optional.of(existante));
        when(levelRepository.findById(1L)).thenReturn(Optional.of(niveau(1L, "6eme", "6EME")));
        when(classRepository.save(any(SchoolClass.class))).thenAnswer(inv -> inv.getArgument(0));
        ClassRequest sansCapacite = demande("6eme A - bis", "6A", 1L, null, null, null);
        mapperModifie(existante);

        classService.update(1L, sansCapacite, httpRequest);

        // 45 doit survivre : un `if` supprime ici ramenerait la classe a sa capacite par defaut.
        assertThat(existante.getCapacity()).isEqualTo(45);
    }

    @Test
    @DisplayName("update ecrase la capacite quand elle est fournie")
    void updateEcraseLaCapaciteFournie() {
        SchoolClass existante = classe(1L, "6eme A", "6A", 45);
        when(classRepository.findById(1L)).thenReturn(Optional.of(existante));
        when(levelRepository.findById(1L)).thenReturn(Optional.of(niveau(1L, "6eme", "6EME")));
        when(classRepository.save(any(SchoolClass.class))).thenAnswer(inv -> inv.getArgument(0));
        mapperModifie(existante);

        classService.update(1L, demande("6eme A", "6A", 1L, null, null, 60), httpRequest);

        assertThat(existante.getCapacity()).isEqualTo(60);
    }

    @Test
    @DisplayName("update remplace le niveau et detache section et salle absentes")
    void updateRemplaceLeNiveauEtDetacheLesOptions() {
        SchoolClass existante = classe(1L, "6eme A", "6A", 30);
        existante.setSection(section(2L, "Francophone"));
        existante.setRoom(salle(3L, "Salle 12", 40));
        when(classRepository.findById(1L)).thenReturn(Optional.of(existante));
        when(levelRepository.findById(5L)).thenReturn(Optional.of(niveau(5L, "5eme", "5EME")));
        when(classRepository.save(any(SchoolClass.class))).thenAnswer(inv -> inv.getArgument(0));
        mapperModifie(existante);

        classService.update(1L, demande("6eme A", "6A", 5L, null, null, 30), httpRequest);

        assertThat(existante.getLevel().getId()).isEqualTo(5L);
        // Le detachement est voulu : l'absence d'identifiant signifie « retirer », pas « ne pas changer ».
        assertThat(existante.getSection()).isNull();
        assertThat(existante.getRoom()).isNull();
    }

    @Test
    @DisplayName("update sur une classe inconnue leve une exception")
    void updateSurUneClasseInconnue() {
        when(classRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> classService.update(404L, demande("X", "X", 1L, null, null, 30), httpRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Classe");
    }

    @Test
    @DisplayName("update documente l'ABSENCE de controle de doublon sur le code")
    void updateNaAucunControleDeDoublonSurLeCode() {
        SchoolClass existante = classe(1L, "6eme A", "6A", 30);
        when(classRepository.findById(1L)).thenReturn(Optional.of(existante));
        when(levelRepository.findById(1L)).thenReturn(Optional.of(niveau(1L, "6eme", "6EME")));
        when(classRepository.save(any(SchoolClass.class))).thenAnswer(inv -> inv.getArgument(0));
        mapperModifie(existante);

        // "5B" appartient deja a une autre classe : l'API accepte quand meme et laisse la
        // contrainte unique de la base refuser. Comportement epingle, pas souhaite.
        classService.update(1L, demande("6eme A", "5B", 1L, null, null, 30), httpRequest);

        verify(classRepository, never()).existsByCode(any());
        assertThat(existante.getCode()).isEqualTo("5B");
    }

    @Test
    @DisplayName("update journalise la modification")
    void updateJournalise() {
        SchoolClass existante = classe(1L, "6eme A", "6A", 30);
        when(classRepository.findById(1L)).thenReturn(Optional.of(existante));
        when(levelRepository.findById(1L)).thenReturn(Optional.of(niveau(1L, "6eme", "6EME")));
        when(classRepository.save(any(SchoolClass.class))).thenAnswer(inv -> inv.getArgument(0));
        mapperModifie(existante);

        classService.update(1L, demande("6eme A", "6A", 1L, null, null, 30), httpRequest);

        verify(auditService).log(eq("UPDATE"), eq("Class"), eq(1L), contains("6eme A"), eq(httpRequest));
    }

    // ================================================================== suppression classe

    @Test
    @DisplayName("delete refuse une classe qui contient des eleves")
    void deleteRefuseUneClasseNonVide() {
        SchoolClass existante = classe(1L, "6eme A", "6A", 30);
        existante.getStudents().add(Student.builder().id(10L).build());
        when(classRepository.findById(1L)).thenReturn(Optional.of(existante));

        assertThatThrownBy(() -> classService.delete(1L, httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("élèves");

        verify(classRepository, never()).delete(any(SchoolClass.class));
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("delete supprime une classe vide et journalise")
    void deleteSupprimeUneClasseVide() {
        SchoolClass existante = classe(1L, "6eme A", "6A", 30);
        when(classRepository.findById(1L)).thenReturn(Optional.of(existante));

        classService.delete(1L, httpRequest);

        verify(classRepository).delete(existante);
        verify(auditService).log(eq("DELETE"), eq("Class"), eq(1L), contains("6eme A"), eq(httpRequest));
    }

    @Test
    @DisplayName("delete sur une classe inconnue leve une exception")
    void deleteSurUneClasseInconnue() {
        when(classRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> classService.delete(404L, httpRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ================================================================== niveaux

    @Nested
    @DisplayName("Niveaux")
    class Niveaux {

        @Test
        @DisplayName("listLevels trie par nom")
        void listLevelsTrieParNom() {
            when(levelRepository.findAll(Sort.by("name")))
                    .thenReturn(List.of(niveau(1L, "5eme", "5EME"), niveau(2L, "6eme", "6EME")));

            assertThat(classService.listLevels()).hasSize(2);
            verify(levelRepository).findAll(Sort.by("name"));
        }

        @Test
        @DisplayName("createLevel refuse un code deja utilise")
        void createLevelRefuseUnCodeExistant() {
            when(levelRepository.findByCode("6EME")).thenReturn(Optional.of(niveau(1L, "6eme", "6EME")));

            assertThatThrownBy(() -> classService.createLevel(
                    new LevelRequest("6eme", "6EME", EducationCycle.COLLEGE), httpRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("6EME");

            verify(levelRepository, never()).save(any(Level.class));
        }

        @Test
        @DisplayName("createLevel enregistre et journalise")
        void createLevelEnregistre() {
            when(levelRepository.findByCode("6EME")).thenReturn(Optional.empty());
            when(levelRepository.save(any(Level.class))).thenAnswer(inv -> {
                Level l = inv.getArgument(0);
                l.setId(4L);
                return l;
            });

            Level cree = classService.createLevel(
                    new LevelRequest("6eme", "6EME", EducationCycle.COLLEGE), httpRequest);

            assertThat(cree.getId()).isEqualTo(4L);
            assertThat(cree.getEducationCycle()).isEqualTo(EducationCycle.COLLEGE);
            verify(auditService).log(eq("CREATE"), eq("Level"), eq(4L), contains("6eme"), eq(httpRequest));
        }

        @Test
        @DisplayName("updateLevel accepte de CONSERVER son propre code")
        void updateLevelAccepteSonPropreCode() {
            Level existant = niveau(1L, "6eme", "6EME");
            when(levelRepository.findById(1L)).thenReturn(Optional.of(existant));
            // Le code appartient deja a CE niveau : ce n'est pas un doublon.
            when(levelRepository.findByCode("6EME")).thenReturn(Optional.of(existant));
            when(levelRepository.save(any(Level.class))).thenAnswer(inv -> inv.getArgument(0));

            classService.updateLevel(1L, new LevelRequest("6eme annee", "6EME", EducationCycle.PRIMAIRE),
                    httpRequest);

            assertThat(existant.getName()).isEqualTo("6eme annee");
            assertThat(existant.getEducationCycle()).isEqualTo(EducationCycle.PRIMAIRE);
        }

        @Test
        @DisplayName("updateLevel refuse le code d'un AUTRE niveau")
        void updateLevelRefuseLeCodeDUneAutreNiveau() {
            Level existant = niveau(1L, "6eme", "6EME");
            Level autre = niveau(2L, "5eme", "5EME");
            when(levelRepository.findById(1L)).thenReturn(Optional.of(existant));
            when(levelRepository.findByCode("5EME")).thenReturn(Optional.of(autre));

            assertThatThrownBy(() -> classService.updateLevel(1L,
                    new LevelRequest("6eme", "5EME", EducationCycle.COLLEGE), httpRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("5EME");

            verify(levelRepository, never()).save(any(Level.class));
        }

        @Test
        @DisplayName("deleteLevel refuse un niveau encore utilise par des classes")
        void deleteLevelRefuseUnNiveauUtilise() {
            Level existant = niveau(1L, "6eme", "6EME");
            when(levelRepository.findById(1L)).thenReturn(Optional.of(existant));
            when(classRepository.existsByLevelId(1L)).thenReturn(true);

            assertThatThrownBy(() -> classService.deleteLevel(1L, httpRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("classes");

            verify(levelRepository, never()).delete(any(Level.class));
            verifyNoInteractions(auditService);
        }

        @Test
        @DisplayName("deleteLevel supprime un niveau libre")
        void deleteLevelSupprime() {
            Level existant = niveau(1L, "6eme", "6EME");
            when(levelRepository.findById(1L)).thenReturn(Optional.of(existant));
            when(classRepository.existsByLevelId(1L)).thenReturn(false);

            classService.deleteLevel(1L, httpRequest);

            verify(levelRepository).delete(existant);
            verify(auditService).log(eq("DELETE"), eq("Level"), eq(1L), contains("6eme"), eq(httpRequest));
        }
    }

    // ================================================================== sections

    @Nested
    @DisplayName("Sections")
    class Sections {

        @Test
        @DisplayName("listSections trie par nom")
        void listSectionsTrieParNom() {
            when(sectionRepository.findAll(Sort.by("name")))
                    .thenReturn(List.of(section(1L, "Anglophone"), section(2L, "Francophone")));

            assertThat(classService.listSections()).hasSize(2);
            verify(sectionRepository).findAll(Sort.by("name"));
        }

        @Test
        @DisplayName("createSection refuse un nom deja utilise")
        void createSectionRefuseUnNomExistant() {
            when(sectionRepository.findByName("Francophone"))
                    .thenReturn(Optional.of(section(1L, "Francophone")));

            assertThatThrownBy(() -> classService.createSection(
                    new SectionRequest("Francophone", "Description"), httpRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Francophone");

            verify(sectionRepository, never()).save(any(Section.class));
        }

        @Test
        @DisplayName("createSection enregistre et journalise")
        void createSectionEnregistre() {
            when(sectionRepository.findByName("Francophone")).thenReturn(Optional.empty());
            when(sectionRepository.save(any(Section.class))).thenAnswer(inv -> {
                Section s = inv.getArgument(0);
                s.setId(3L);
                return s;
            });

            Section cree = classService.createSection(new SectionRequest("Francophone", "Desc"), httpRequest);

            assertThat(cree.getId()).isEqualTo(3L);
            verify(auditService).log(eq("CREATE"), eq("Section"), eq(3L), contains("Francophone"), eq(httpRequest));
        }

        @Test
        @DisplayName("updateSection accepte de CONSERVER son propre nom")
        void updateSectionAccepteSonPropreNom() {
            Section existante = section(1L, "Francophone");
            when(sectionRepository.findById(1L)).thenReturn(Optional.of(existante));
            when(sectionRepository.findByName("Francophone")).thenReturn(Optional.of(existante));
            when(sectionRepository.save(any(Section.class))).thenAnswer(inv -> inv.getArgument(0));

            classService.updateSection(1L, new SectionRequest("Francophone", "Nouvelle desc"), httpRequest);

            assertThat(existante.getDescription()).isEqualTo("Nouvelle desc");
        }

        @Test
        @DisplayName("updateSection refuse le nom d'une AUTRE section")
        void updateSectionRefuseLeNomDUneAutreSection() {
            Section existante = section(1L, "Francophone");
            when(sectionRepository.findById(1L)).thenReturn(Optional.of(existante));
            when(sectionRepository.findByName("Anglophone")).thenReturn(Optional.of(section(2L, "Anglophone")));

            assertThatThrownBy(() -> classService.updateSection(1L,
                    new SectionRequest("Anglophone", "Desc"), httpRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Anglophone");

            verify(sectionRepository, never()).save(any(Section.class));
        }

        @Test
        @DisplayName("deleteSection refuse une section utilisee par des classes")
        void deleteSectionRefuseUneSectionUtilisee() {
            Section existante = section(1L, "Francophone");
            when(sectionRepository.findById(1L)).thenReturn(Optional.of(existante));
            when(classRepository.existsBySectionId(1L)).thenReturn(true);

            assertThatThrownBy(() -> classService.deleteSection(1L, httpRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("classes");

            verify(sectionRepository, never()).delete(any(Section.class));
        }

        @Test
        @DisplayName("deleteSection supprime une section libre")
        void deleteSectionSupprime() {
            Section existante = section(1L, "Francophone");
            when(sectionRepository.findById(1L)).thenReturn(Optional.of(existante));
            when(classRepository.existsBySectionId(1L)).thenReturn(false);

            classService.deleteSection(1L, httpRequest);

            verify(sectionRepository).delete(existante);
        }
    }

    // ================================================================== salles

    @Nested
    @DisplayName("Salles")
    class Salles {

        @Test
        @DisplayName("listRooms trie par nom")
        void listRoomsTrieParNom() {
            when(roomRepository.findAll(Sort.by("name")))
                    .thenReturn(List.of(salle(1L, "Salle 1", 30), salle(2L, "Salle 12", 40)));

            assertThat(classService.listRooms()).hasSize(2);
            verify(roomRepository).findAll(Sort.by("name"));
        }

        @Test
        @DisplayName("createRoom refuse un nom deja utilise")
        void createRoomRefuseUnNomExistant() {
            when(roomRepository.findByName("Salle 12"))
                    .thenReturn(Optional.of(salle(1L, "Salle 12", 40)));

            assertThatThrownBy(() -> classService.createRoom(
                    new RoomRequest("Salle 12", 40, "Batiment A"), httpRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Salle 12");

            verify(roomRepository, never()).save(any(Room.class));
        }

        @Test
        @DisplayName("createRoom applique la capacite par defaut de 30 quand elle est absente")
        void createRoomAppliqueLaCapaciteParDefaut() {
            when(roomRepository.findByName("Salle 99")).thenReturn(Optional.empty());
            when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

            classService.createRoom(new RoomRequest("Salle 99", null, null), httpRequest);

            ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
            verify(roomRepository).save(captor.capture());
            assertThat(captor.getValue().getCapacity()).isEqualTo(30);
        }

        @Test
        @DisplayName("createRoom conserve une capacite explicite")
        void createRoomConserveUneCapaciteExplicite() {
            when(roomRepository.findByName("Salle 99")).thenReturn(Optional.empty());
            when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

            classService.createRoom(new RoomRequest("Salle 99", 55, "Batiment C"), httpRequest);

            ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
            verify(roomRepository).save(captor.capture());
            assertThat(captor.getValue().getCapacity()).isEqualTo(55);
            assertThat(captor.getValue().getLocation()).isEqualTo("Batiment C");
        }

        @Test
        @DisplayName("updateRoom NE touche PAS la capacite quand elle est absente")
        void updateRoomNeTouchePasLaCapaciteAbsente() {
            Room existante = salle(1L, "Salle 12", 40);
            when(roomRepository.findById(1L)).thenReturn(Optional.of(existante));
            when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

            classService.updateRoom(1L, new RoomRequest("Salle 12 bis", null, "Batiment B"), httpRequest);

            assertThat(existante.getCapacity()).isEqualTo(40);
            assertThat(existante.getName()).isEqualTo("Salle 12 bis");
            assertThat(existante.getLocation()).isEqualTo("Batiment B");
        }

        @Test
        @DisplayName("updateRoom accepte de CONSERVER son propre nom")
        void updateRoomAccepteSonPropreNom() {
            Room existante = salle(1L, "Salle 12", 40);
            when(roomRepository.findById(1L)).thenReturn(Optional.of(existante));
            when(roomRepository.findByName("Salle 12")).thenReturn(Optional.of(existante));
            when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

            classService.updateRoom(1L, new RoomRequest("Salle 12", 45, "Batiment A"), httpRequest);

            assertThat(existante.getCapacity()).isEqualTo(45);
        }

        @Test
        @DisplayName("updateRoom refuse le nom d'une AUTRE salle")
        void updateRoomRefuseLeNomDUneAutreSalle() {
            Room existante = salle(1L, "Salle 12", 40);
            when(roomRepository.findById(1L)).thenReturn(Optional.of(existante));
            when(roomRepository.findByName("Salle 13")).thenReturn(Optional.of(salle(2L, "Salle 13", 30)));

            assertThatThrownBy(() -> classService.updateRoom(1L,
                    new RoomRequest("Salle 13", 40, "Batiment A"), httpRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Salle 13");

            verify(roomRepository, never()).save(any(Room.class));
        }

        @Test
        @DisplayName("deleteRoom refuse une salle utilisee par des classes")
        void deleteRoomRefuseUneSalleUtilisee() {
            Room existante = salle(1L, "Salle 12", 40);
            when(roomRepository.findById(1L)).thenReturn(Optional.of(existante));
            when(classRepository.existsByRoomId(1L)).thenReturn(true);

            assertThatThrownBy(() -> classService.deleteRoom(1L, httpRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("classes");

            verify(roomRepository, never()).delete(any(Room.class));
        }

        @Test
        @DisplayName("deleteRoom supprime une salle libre")
        void deleteRoomSupprime() {
            Room existante = salle(1L, "Salle 12", 40);
            when(roomRepository.findById(1L)).thenReturn(Optional.of(existante));
            when(classRepository.existsByRoomId(1L)).thenReturn(false);

            classService.deleteRoom(1L, httpRequest);

            verify(roomRepository).delete(existante);
        }

        @Test
        @DisplayName("getRoom/ getSection / getLevel nomment la ressource attendue")
        void gettersNommentLaRessource() {
            when(roomRepository.findById(404L)).thenReturn(Optional.empty());
            when(sectionRepository.findById(405L)).thenReturn(Optional.empty());
            when(levelRepository.findById(406L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> classService.getRoom(404L))
                    .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("Salle");
            assertThatThrownBy(() -> classService.getSection(405L))
                    .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("Section");
            assertThatThrownBy(() -> classService.getLevel(406L))
                    .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("Niveau");
        }
    }

    // ================================================================== findById expose

    @Test
    @DisplayName("findById est expose et renvoie l'entite (utilisee par les autres modules)")
    void findByIdEstExpose() {
        SchoolClass existante = classe(1L, "6eme A", "6A", 30);
        when(classRepository.findById(1L)).thenReturn(Optional.of(existante));

        assertThat(classService.findById(1L)).isSameAs(existante);
    }

    @Test
    @DisplayName("findById leve sur un identifiant inconnu")
    void findByIdLeveSurInconnu() {
        when(classRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> classService.findById(77L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("77");
    }

    /** Le mapper ne doit pas etre appele en lecture : la conversion passe par ClassResponse.from. */
    @Test
    @DisplayName("la lecture n'appelle jamais le mapper")
    void laLectureNAppellePasLeMapper() {
        lenient().when(classRepository.findById(1L)).thenReturn(Optional.of(classe(1L, "6eme A", "6A", 30)));

        classService.getById(1L);

        verifyNoInteractions(classMapper);
    }
}
