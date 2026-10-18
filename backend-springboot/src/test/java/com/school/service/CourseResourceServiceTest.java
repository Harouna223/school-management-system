package com.school.service;

import com.school.dto.request.CourseResourceRequest;
import com.school.dto.response.CourseResourceResponse;
import com.school.entity.CourseResource;
import com.school.entity.Role;
import com.school.entity.SchoolClass;
import com.school.entity.Student;
import com.school.entity.Subject;
import com.school.entity.Teacher;
import com.school.entity.User;
import com.school.enums.CourseResourceType;
import com.school.enums.CourseStatus;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.CourseResourceRepository;
import com.school.repository.SchoolClassRepository;
import com.school.repository.StudentRepository;
import com.school.repository.SubjectAssignmentRepository;
import com.school.repository.SubjectRepository;
import com.school.repository.TeacherRepository;
import com.school.repository.UserRepository;
import com.school.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests des règles de permission et du cycle de vie des ressources pédagogiques.
 *
 * <p>Ce qui est réellement prouvé ici : un élève ne voit ni un brouillon ni le
 * cours d'une autre classe, un enseignant ne gère que ses propres ressources et
 * ne publie que dans ses affectations, et un jeton de lecture valide ne
 * contourne pas ces règles.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourseResourceServiceTest {

    @Mock private CourseResourceRepository repository;
    @Mock private CourseFileStorageService storage;
    @Mock private CourseAccessTokenService tokens;
    @Mock private SubjectRepository subjectRepository;
    @Mock private SchoolClassRepository classRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private TeacherRepository teacherRepository;
    @Mock private SubjectAssignmentRepository assignmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @Mock private HttpServletRequest httpRequest;

    private CourseResourceService service;

    @BeforeEach
    void setUp() {
        service = new CourseResourceService(repository, storage, tokens, subjectRepository,
                classRepository, studentRepository, teacherRepository, assignmentRepository,
                userRepository, auditService);
    }

    // ------------------------------------------------------------------
    // Fabriques
    // ------------------------------------------------------------------

    private static User user(Long id, String... roles) {
        Set<Role> set = Arrays.stream(roles)
                .map(r -> Role.builder().name(r).build())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return User.builder()
                .id(id).username("user" + id).email("u" + id + "@school.edu")
                .firstName("Ada").lastName("Lovelace")
                .roles(set).build();
    }

    private static Subject subject(Long id, String name) {
        return Subject.builder().id(id).name(name).code("S" + id).coefficient(1).build();
    }

    private static SchoolClass schoolClass(Long id, String name) {
        return SchoolClass.builder().id(id).name(name).code("C" + id).build();
    }

    private static CourseResource resource(Long id, CourseStatus status, User author,
                                           SchoolClass... classes) {
        return CourseResource.builder()
                .id(id)
                .title("Chapitre " + id)
                .description("Description")
                .type(CourseResourceType.PDF)
                .status(status)
                .downloadable(true)
                .subject(subject(1L, "Mathématiques"))
                .author(author)
                .targetClasses(new LinkedHashSet<>(Arrays.asList(classes)))
                .storageFilename("courses/" + id + "/fichier.pdf")
                .originalFilename("cours.pdf")
                .sizeBytes(1024L)
                .mimeType("application/pdf")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private static CourseResourceRequest request(Long subjectId, Long... classIds) {
        CourseResourceRequest request = new CourseResourceRequest();
        request.setTitle("Nouveau cours");
        request.setDescription("Contenu");
        request.setChapter("Chapitre 1");
        request.setType(CourseResourceType.VIDEO);
        request.setSubjectId(subjectId);
        request.setClassIds(Arrays.asList(classIds));
        return request;
    }

    private static Student studentOf(User user, SchoolClass schoolClass) {
        return Student.builder().id(99L).user(user).schoolClass(schoolClass)
                .firstName("S").lastName("T").matricule("ETU-1").build();
    }

    // ------------------------------------------------------------------
    // Écriture : périmètre de l'enseignant
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Un enseignant ne publie pas dans une classe où il n'enseigne pas la matière")
    void enseignantHorsAffectation_estRefuse() {
        User teacherUser = user(5L, "ENSEIGNANT");
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject(1L, "Mathématiques")));
        when(classRepository.findAllById(List.of(2L))).thenReturn(List.of(schoolClass(2L, "6e A")));
        when(teacherRepository.findByUserId(5L)).thenReturn(Optional.of(Teacher.builder().id(9L).build()));
        when(assignmentRepository.existsByTeacherIdAndSubjectIdAndSchoolClassId(9L, 1L, 2L))
                .thenReturn(false);

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::currentUser).thenReturn(teacherUser);

            assertThatThrownBy(() -> service.create(request(1L, 2L), httpRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("n'enseignez pas");
        }
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Un enseignant publie dans sa classe/matière affectée, en brouillon")
    void enseignantSurSonAffectation_creeUnBrouillon() {
        User teacherUser = user(5L, "ENSEIGNANT");
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(subject(1L, "Mathématiques")));
        when(classRepository.findAllById(List.of(2L))).thenReturn(List.of(schoolClass(2L, "6e A")));
        when(teacherRepository.findByUserId(5L)).thenReturn(Optional.of(Teacher.builder().id(9L).build()));
        when(assignmentRepository.existsByTeacherIdAndSubjectIdAndSchoolClassId(9L, 1L, 2L))
                .thenReturn(true);
        when(repository.save(any(CourseResource.class))).thenAnswer(inv -> {
            CourseResource r = inv.getArgument(0);
            r.setId(50L);
            return r;
        });

        CourseResourceResponse response;
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::currentUser).thenReturn(teacherUser);
            response = service.create(request(1L, 2L), httpRequest);
        }

        assertThat(response.getStatus()).isEqualTo(CourseStatus.DRAFT);
        assertThat(response.getClassIds()).containsExactly(2L);
        assertThat(response.getAuthorId()).isEqualTo(5L);
        verify(auditService).log(eq("CREATE"), eq("CourseResource"), eq(50L), any(), eq(httpRequest));
    }

    @Test
    @DisplayName("Un rôle sans permission d'écriture (comptable) ne peut pas créer de cours")
    void comptable_nePeutPasCreer() {
        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::currentUser).thenReturn(user(7L, "COMPTABLE"));

            assertThatThrownBy(() -> service.create(request(1L, 2L), httpRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Accès refusé");
        }
    }

    @Test
    @DisplayName("Publier un cours sans fichier est refusé")
    void publicationSansFichier_estRefusee() {
        User admin = user(1L, "SUPER_ADMIN");
        CourseResource draft = resource(1L, CourseStatus.DRAFT, admin, schoolClass(2L, "6e A"));
        draft.setStorageFilename(null);
        when(repository.findById(1L)).thenReturn(Optional.of(draft));

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::currentUser).thenReturn(admin);

            assertThatThrownBy(() -> service.changeStatus(1L, CourseStatus.PUBLISHED, httpRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("sans fichier");
        }
    }

    // ------------------------------------------------------------------
    // Lecture : périmètre de l'élève
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Un élève ne voit pas une ressource publiée visant une autre classe (404, pas 403)")
    void eleveAutreClasse_recoit404() {
        User eleve = user(3L, "ELEVE");
        when(repository.findById(1L)).thenReturn(Optional.of(
                resource(1L, CourseStatus.PUBLISHED, user(1L, "SUPER_ADMIN"), schoolClass(9L, "3e B"))));
        when(studentRepository.findByUserId(3L))
                .thenReturn(Optional.of(studentOf(eleve, schoolClass(7L, "6e A"))));

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::currentUser).thenReturn(eleve);

            assertThatThrownBy(() -> service.get(1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test
    @DisplayName("Un élève ne voit pas un brouillon, même destiné à sa classe")
    void eleveNeVoitPasUnBrouillon() {
        User eleve = user(3L, "ELEVE");
        when(repository.findById(1L)).thenReturn(Optional.of(
                resource(1L, CourseStatus.DRAFT, user(1L, "SUPER_ADMIN"), schoolClass(7L, "6e A"))));
        when(studentRepository.findByUserId(3L))
                .thenReturn(Optional.of(studentOf(eleve, schoolClass(7L, "6e A"))));

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::currentUser).thenReturn(eleve);

            assertThatThrownBy(() -> service.get(1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test
    @DisplayName("Un élève voit une ressource publiée de sa classe")
    void eleveVoitSaRessourcePubliee() {
        User eleve = user(3L, "ELEVE");
        when(repository.findById(1L)).thenReturn(Optional.of(
                resource(1L, CourseStatus.PUBLISHED, user(1L, "SUPER_ADMIN"), schoolClass(7L, "6e A"))));
        when(studentRepository.findByUserId(3L))
                .thenReturn(Optional.of(studentOf(eleve, schoolClass(7L, "6e A"))));

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::currentUser).thenReturn(eleve);

            CourseResourceResponse response = service.get(1L);

            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getClassIds()).containsExactly(7L);
        }
    }

    @Test
    @DisplayName("La liste d'un élève est bornée à SA classe et au statut PUBLISHED")
    void rechercheEleve_estBornee() {
        User eleve = user(3L, "ELEVE");
        when(studentRepository.findByUserId(3L))
                .thenReturn(Optional.of(studentOf(eleve, schoolClass(7L, "6e A"))));
        when(repository.searchForClasses(eq(List.of(7L)), eq(CourseStatus.PUBLISHED),
                any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::currentUser).thenReturn(eleve);

            assertThat(service.search(null, null, null, null, null, 0, 10).getContent()).isEmpty();
        }
        verify(repository).searchForClasses(eq(List.of(7L)), eq(CourseStatus.PUBLISHED),
                any(), any(), any(), any());
        verify(repository, never()).searchForStaff(any(), any(), any(), any(), any(), any(), any());
    }

    // ------------------------------------------------------------------
    // Propriété des ressources
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Un enseignant ne modifie pas la ressource d'un autre enseignant")
    void enseignant_neModifiePasLaRessourceDunAutre() {
        User teacher = user(5L, "ENSEIGNANT");
        when(repository.findById(1L)).thenReturn(Optional.of(
                resource(1L, CourseStatus.PUBLISHED, user(6L, "ENSEIGNANT"), schoolClass(2L, "6e A"))));

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::currentUser).thenReturn(teacher);

            assertThatThrownBy(() -> service.changeStatus(1L, CourseStatus.ARCHIVED, httpRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("vos propres ressources");
        }
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("L'administration peut archiver la ressource d'un enseignant")
    void admin_archiveLaRessourceDunEnseignant() {
        User admin = user(1L, "DIRECTEUR");
        CourseResource published = resource(1L, CourseStatus.PUBLISHED, user(6L, "ENSEIGNANT"),
                schoolClass(2L, "6e A"));
        when(repository.findById(1L)).thenReturn(Optional.of(published));
        when(repository.save(any(CourseResource.class))).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::currentUser).thenReturn(admin);

            CourseResourceResponse response = service.changeStatus(1L, CourseStatus.ARCHIVED, httpRequest);
            assertThat(response.getStatus()).isEqualTo(CourseStatus.ARCHIVED);
        }
    }

    // ------------------------------------------------------------------
    // Fichiers
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Remplacer le fichier supprime l'ancien après la sauvegarde")
    void upload_remplaceEtSupprimeLancien() {
        User admin = user(1L, "SUPER_ADMIN");
        CourseResource existing = resource(1L, CourseStatus.DRAFT, admin, schoolClass(2L, "6e A"));
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(CourseResource.class))).thenAnswer(inv -> inv.getArgument(0));
        when(storage.store(any(), eq(CourseResourceType.PDF), eq(1L))).thenReturn(
                new CourseFileStorageService.StoredFile(
                        "courses/1/nouveau.pdf", "nouveau.pdf", 2048L, "application/pdf", "pdf"));

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::currentUser).thenReturn(admin);
            service.uploadFile(1L, new org.springframework.mock.web.MockMultipartFile(
                    "file", "nouveau.pdf", "application/pdf", new byte[]{'%', 'P', 'D', 'F', '-'}), httpRequest);
        }

        assertThat(existing.getStorageFilename()).isEqualTo("courses/1/nouveau.pdf");
        assertThat(existing.getSizeBytes()).isEqualTo(2048L);
        verify(storage).delete("courses/1/fichier.pdf");
    }

    @Test
    @DisplayName("Supprimer une ressource supprime aussi son fichier")
    void delete_supprimeLeFichier() {
        User admin = user(1L, "SUPER_ADMIN");
        CourseResource existing = resource(1L, CourseStatus.PUBLISHED, admin, schoolClass(2L, "6e A"));
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        try (MockedStatic<SecurityUtils> mocked = mockStatic(SecurityUtils.class)) {
            mocked.when(SecurityUtils::currentUser).thenReturn(admin);
            service.delete(1L, httpRequest);
        }

        verify(repository).delete(existing);
        verify(storage).delete("courses/1/fichier.pdf");
    }

    // ------------------------------------------------------------------
    // Jeton de lecture
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Un jeton valide ne contourne pas les droits : revalidation à chaque requête")
    void jetonValide_neContournePasLesDroits() {
        User eleve = user(3L, "ELEVE");
        when(tokens.verify("jeton", CourseAccessTokenService.PURPOSE_STREAM))
                .thenReturn(new CourseAccessTokenService.Payload(
                        1L, 3L, CourseAccessTokenService.PURPOSE_STREAM, Instant.now().plusSeconds(60)));
        when(repository.findById(1L)).thenReturn(Optional.of(
                resource(1L, CourseStatus.PUBLISHED, user(1L, "SUPER_ADMIN"), schoolClass(9L, "3e B"))));
        when(userRepository.findById(3L)).thenReturn(Optional.of(eleve));
        when(studentRepository.findByUserId(3L))
                .thenReturn(Optional.of(studentOf(eleve, schoolClass(7L, "6e A"))));

        assertThatThrownBy(() -> service.resolveFile(1L, "jeton", CourseAccessTokenService.PURPOSE_STREAM))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Un jeton d'une autre ressource est refusé par l'identifiant du chemin")
    void jetonDuneAutreRessource_estRefuse() {
        when(tokens.verify("jeton", CourseAccessTokenService.PURPOSE_STREAM))
                .thenReturn(new CourseAccessTokenService.Payload(
                        2L, 3L, CourseAccessTokenService.PURPOSE_STREAM, Instant.now().plusSeconds(60)));

        assertThatThrownBy(() -> service.resolveFile(1L, "jeton", CourseAccessTokenService.PURPOSE_STREAM))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ne correspond pas");
    }

    @Test
    @DisplayName("Le téléchargement est refusé quand la ressource l'interdit")
    void telechargementInterdit_estRefuse() {
        User admin = user(1L, "SUPER_ADMIN");
        CourseResource resource = resource(1L, CourseStatus.PUBLISHED, admin, schoolClass(2L, "6e A"));
        resource.setDownloadable(false);
        when(tokens.verify("jeton", CourseAccessTokenService.PURPOSE_DOWNLOAD))
                .thenReturn(new CourseAccessTokenService.Payload(
                        1L, 1L, CourseAccessTokenService.PURPOSE_DOWNLOAD, Instant.now().plusSeconds(60)));
        when(repository.findById(1L)).thenReturn(Optional.of(resource));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.resolveFile(1L, "jeton", CourseAccessTokenService.PURPOSE_DOWNLOAD))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("téléchargement");
    }
}
