package com.school.service;

import com.school.dto.request.CourseResourceRequest;
import com.school.dto.response.CourseAccessTokenResponse;
import com.school.dto.response.CourseOptionsResponse;
import com.school.dto.response.CourseResourceResponse;
import com.school.dto.response.PageResponse;
import com.school.entity.CourseResource;
import com.school.entity.SchoolClass;
import com.school.entity.Student;
import com.school.entity.Subject;
import com.school.entity.SubjectAssignment;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Module « Cours et ressources » : publication et diffusion de vidéos et de PDF.
 *
 * <p>Trois périmètres, vérifiés À CHAQUE APPEL côté serveur :</p>
 * <ul>
 *   <li><b>Administration</b> (SUPER_ADMIN, DIRECTEUR, SECRETAIRE) : gère toutes
 *       les ressources.</li>
 *   <li><b>Enseignant</b> : crée et gère SES ressources, mais uniquement pour
 *       les paires (classe, matière) où il a une affectation réelle
 *       ({@code subject_assignments}). Il voit en plus le catalogue publié.</li>
 *   <li><b>Élève</b> : uniquement les ressources PUBLIÉES visant sa classe.
 *       Toute autre ressource répond 404 (et non 403) pour ne pas révéler son
 *       existence.</li>
 * </ul>
 *
 * <p>Les autres rôles (comptable, parent) n'ont pas accès à ce module.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class CourseResourceService {

    private static final Set<String> STAFF_ROLES =
            Set.of("SUPER_ADMIN", "DIRECTEUR", "SECRETAIRE");

    private static final int MAX_PAGE_SIZE = 100;

    private final CourseResourceRepository courseResourceRepository;
    private final CourseFileStorageService storageService;
    private final CourseAccessTokenService tokenService;
    private final SubjectRepository subjectRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final SubjectAssignmentRepository subjectAssignmentRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    /** Fichier vérifié, prêt à être servi par le contrôleur. */
    public record FileAccess(CourseResource resource, Path path) {
    }

    // ==================================================================
    // Lecture
    // ==================================================================

    public PageResponse<CourseResourceResponse> search(String search, CourseResourceType type,
                                                       CourseStatus status, Long subjectId,
                                                       Long classId, int page, int size) {
        User user = currentUser();
        Pageable pageable = PageRequest.of(page, clampSize(size), Sort.by("createdAt").descending());

        if (isStaff(user)) {
            Page<CourseResource> result = courseResourceRepository.searchForStaff(
                    search, type, status, subjectId, classId, null, pageable);
            return PageResponse.from(result, CourseResourceResponse::from);
        }
        if (isTeacher(user)) {
            Page<CourseResource> result = courseResourceRepository.searchForTeacher(
                    search, type, status, subjectId, classId, user.getId(),
                    CourseStatus.PUBLISHED, pageable);
            return PageResponse.from(result, CourseResourceResponse::from);
        }
        if (isStudent(user)) {
            Long classIdOfStudent = studentClassId(user.getId());
            if (classIdOfStudent == null) {
                return PageResponse.<CourseResourceResponse>builder()
                        .content(List.of()).page(page).size(clampSize(size))
                        .totalElements(0).totalPages(0).first(true).last(true).build();
            }
            Page<CourseResource> result = courseResourceRepository.searchForClasses(
                    List.of(classIdOfStudent), CourseStatus.PUBLISHED, search, type, subjectId, pageable);
            return PageResponse.from(result, CourseResourceResponse::from);
        }
        throw new BusinessException("Le module Cours et ressources n'est pas accessible depuis ce compte");
    }

    public CourseResourceResponse get(Long id) {
        CourseResource resource = find(id);
        assertCanRead(resource, currentUser());
        return CourseResourceResponse.from(resource);
    }

    /**
     * Options du formulaire de publication : toutes les classes/matières pour
     * l'administration, uniquement les affectations réelles pour un enseignant.
     */
    public CourseOptionsResponse options() {
        User user = currentUser();
        if (isStaff(user)) {
            return CourseOptionsResponse.builder()
                    .restricted(false)
                    .classes(schoolClassRepository.findAll(Sort.by("name")).stream()
                            .map(c -> new CourseOptionsResponse.Option(c.getId(), c.getName()))
                            .toList())
                    .subjects(subjectRepository.findAll(Sort.by("name")).stream()
                            .map(s -> new CourseOptionsResponse.Option(s.getId(), s.getName()))
                            .toList())
                    .assignments(List.of())
                    .build();
        }
        if (isTeacher(user)) {
            Teacher teacher = teacherOf(user);
            List<SubjectAssignment> assignments = subjectAssignmentRepository.findByTeacherId(teacher.getId());
            List<CourseOptionsResponse.Option> classes = assignments.stream()
                    .map(SubjectAssignment::getSchoolClass)
                    .filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.toMap(
                            SchoolClass::getId, c -> c, (a, b) -> a, java.util.LinkedHashMap::new))
                    .values().stream()
                    .sorted(Comparator.comparing(SchoolClass::getName))
                    .map(c -> new CourseOptionsResponse.Option(c.getId(), c.getName()))
                    .toList();
            List<CourseOptionsResponse.Option> subjects = assignments.stream()
                    .map(SubjectAssignment::getSubject)
                    .filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.toMap(
                            Subject::getId, s -> s, (a, b) -> a, java.util.LinkedHashMap::new))
                    .values().stream()
                    .sorted(Comparator.comparing(Subject::getName))
                    .map(s -> new CourseOptionsResponse.Option(s.getId(), s.getName()))
                    .toList();
            List<CourseOptionsResponse.Assignment> pairs = assignments.stream()
                    .filter(a -> a.getSchoolClass() != null && a.getSubject() != null)
                    .map(a -> new CourseOptionsResponse.Assignment(
                            a.getSchoolClass().getId(), a.getSubject().getId()))
                    .distinct()
                    .toList();
            return CourseOptionsResponse.builder()
                    .restricted(true).classes(classes).subjects(subjects).assignments(pairs)
                    .build();
        }
        throw new BusinessException("Le module Cours et ressources n'est pas accessible depuis ce compte");
    }

    // ==================================================================
    // Écriture
    // ==================================================================

    @Transactional
    public CourseResourceResponse create(CourseResourceRequest request, HttpServletRequest httpRequest) {
        User user = currentUser();
        assertCanWrite(user);

        Subject subject = subject(request.getSubjectId());
        List<SchoolClass> classes = classes(request.getClassIds());
        assertTeacherOwnsTargets(user, subject, classes);

        CourseResource resource = CourseResource.builder()
                .title(request.getTitle().trim())
                .description(blankToNull(request.getDescription()))
                .chapter(blankToNull(request.getChapter()))
                .type(request.getType())
                .subject(subject)
                .author(user)
                .status(CourseStatus.DRAFT)
                .downloadable(request.getDownloadable() == null || request.getDownloadable())
                .targetClasses(new LinkedHashSet<>(classes))
                .build();

        CourseResource saved = courseResourceRepository.save(resource);
        auditService.log("CREATE", "CourseResource", saved.getId(),
                "Création du cours « " + saved.getTitle() + " »", httpRequest);
        return CourseResourceResponse.from(saved);
    }

    @Transactional
    public CourseResourceResponse update(Long id, CourseResourceRequest request,
                                         HttpServletRequest httpRequest) {
        CourseResource resource = find(id);
        User user = currentUser();
        assertCanManage(resource, user);

        if (resource.hasFile() && request.getType() != resource.getType()) {
            throw new BusinessException("Impossible de changer le type (VIDEO/PDF) d'un cours "
                    + "qui possède déjà un fichier : supprimez la ressource et recréez-la");
        }

        Subject subject = subject(request.getSubjectId());
        List<SchoolClass> classes = classes(request.getClassIds());
        assertTeacherOwnsTargets(user, subject, classes);

        resource.setTitle(request.getTitle().trim());
        resource.setDescription(blankToNull(request.getDescription()));
        resource.setChapter(blankToNull(request.getChapter()));
        resource.setType(request.getType());
        resource.setSubject(subject);
        resource.setTargetClasses(new LinkedHashSet<>(classes));
        if (request.getDownloadable() != null) {
            resource.setDownloadable(request.getDownloadable());
        }

        CourseResource saved = courseResourceRepository.save(resource);
        auditService.log("UPDATE", "CourseResource", id,
                "Modification du cours « " + saved.getTitle() + " »", httpRequest);
        return CourseResourceResponse.from(saved);
    }

    @Transactional
    public CourseResourceResponse uploadFile(Long id, MultipartFile file, HttpServletRequest httpRequest) {
        CourseResource resource = find(id);
        User user = currentUser();
        assertCanManage(resource, user);

        CourseFileStorageService.StoredFile stored =
                storageService.store(file, resource.getType(), resource.getId());

        String previous = resource.getStorageFilename();
        resource.setStorageFilename(stored.relativePath());
        resource.setOriginalFilename(stored.originalFilename());
        resource.setSizeBytes(stored.sizeBytes());
        resource.setMimeType(stored.mimeType());

        CourseResource saved = courseResourceRepository.save(resource);
        // L'ancien fichier n'est effacé qu'APRÈS l'écriture réussie du nouveau :
        // un échec de base laisserait sinon la ressource sans aucun fichier.
        if (previous != null && !previous.equals(stored.relativePath())) {
            storageService.delete(previous);
        }
        auditService.log("UPDATE", "CourseResource", id,
                "Fichier « " + stored.originalFilename() + " » attaché au cours « "
                        + saved.getTitle() + " »", httpRequest);
        return CourseResourceResponse.from(saved);
    }

    @Transactional
    public CourseResourceResponse changeStatus(Long id, CourseStatus status, HttpServletRequest httpRequest) {
        CourseResource resource = find(id);
        User user = currentUser();
        assertCanManage(resource, user);

        if (status == CourseStatus.PUBLISHED && !resource.hasFile()) {
            throw new BusinessException("Impossible de publier un cours sans fichier : "
                    + "téléversez d'abord la vidéo ou le PDF");
        }
        resource.setStatus(status);
        if (status == CourseStatus.PUBLISHED) {
            if (resource.getPublishedAt() == null) {
                resource.setPublishedAt(LocalDateTime.now());
            }
        } else if (status == CourseStatus.DRAFT) {
            resource.setPublishedAt(null);
        }

        CourseResource saved = courseResourceRepository.save(resource);
        auditService.log(status.name(), "CourseResource", id,
                "Statut du cours « " + saved.getTitle() + " » : " + status.name(), httpRequest);
        return CourseResourceResponse.from(saved);
    }

    @Transactional
    public void delete(Long id, HttpServletRequest httpRequest) {
        CourseResource resource = find(id);
        User user = currentUser();
        assertCanManage(resource, user);

        String storagePath = resource.getStorageFilename();
        String title = resource.getTitle();
        courseResourceRepository.delete(resource);
        storageService.delete(storagePath);
        auditService.log("DELETE", "CourseResource", id,
                "Suppression du cours « " + title + " »", httpRequest);
    }

    // ==================================================================
    // Jetons et lecture du fichier
    // ==================================================================

    /** Délivre un jeton court pour lire le fichier depuis le navigateur. */
    @Transactional(readOnly = true)
    public CourseAccessTokenResponse issueToken(Long id, String purpose) {
        CourseResource resource = find(id);
        User user = currentUser();
        assertCanRead(resource, user);
        requireFileForPurpose(resource, purpose);

        long ttl = tokenService.ttlSeconds();
        String token = tokenService.issue(resource.getId(), user.getId(), purpose, ttl);
        return CourseAccessTokenResponse.builder()
                .resourceId(resource.getId())
                .purpose(purpose)
                .token(token)
                .expiresInSeconds(ttl)
                .build();
    }

    /**
     * Valide le jeton puis REVALIDE les droits réels du compte.
     *
     * <p>Le jeton n'est qu'un moyen de transport : si l'élève a changé de classe,
     * si l'auteur a été rétrogradé ou si la ressource a été dépubliée après
     * l'émission du jeton, l'accès est refusé ici.</p>
     */
    @Transactional(readOnly = true)
    public FileAccess resolveFile(Long id, String token, String purpose) {
        CourseAccessTokenService.Payload payload = tokenService.verify(token, purpose);
        if (!Objects.equals(payload.resourceId(), id)) {
            throw new BusinessException("Ce lien de lecture ne correspond pas à cette ressource");
        }
        CourseResource resource = find(id);
        User user = userRepository.findById(payload.userId())
                .orElseThrow(() -> new BusinessException("Compte associé au lien introuvable"));
        assertCanRead(resource, user);
        requireFileForPurpose(resource, purpose);
        if (CourseAccessTokenService.PURPOSE_DOWNLOAD.equals(purpose) && !resource.isDownloadable()) {
            throw new BusinessException("Le téléchargement de cette ressource n'est pas autorisé");
        }
        return new FileAccess(resource, storageService.resolve(resource.getStorageFilename()));
    }

    private void requireFileForPurpose(CourseResource resource, String purpose) {
        if (!resource.hasFile()) {
            throw new ResourceNotFoundException("Aucun fichier n'est attaché à cette ressource");
        }
        if (CourseAccessTokenService.PURPOSE_STREAM.equals(purpose)
                && resource.getType() != CourseResourceType.VIDEO) {
            throw new BusinessException("Cette ressource n'est pas une vidéo");
        }
        if (CourseAccessTokenService.PURPOSE_PREVIEW.equals(purpose)
                && resource.getType() != CourseResourceType.PDF) {
            throw new BusinessException("Cette ressource n'est pas un document PDF");
        }
    }

    // ==================================================================
    // Permissions
    // ==================================================================

    private User currentUser() {
        User user = SecurityUtils.currentUser();
        if (user == null) {
            throw new BusinessException("Utilisateur non authentifié");
        }
        return user;
    }

    private boolean isStaff(User user) {
        return hasAnyRole(user, STAFF_ROLES);
    }

    private boolean isTeacher(User user) {
        return hasAnyRole(user, Set.of("ENSEIGNANT")) && !isStaff(user);
    }

    private boolean isStudent(User user) {
        return hasAnyRole(user, Set.of("ELEVE")) && !isStaff(user);
    }

    private boolean hasAnyRole(User user, Set<String> roles) {
        return user.getRoles() != null && user.getRoles().stream()
                .map(r -> r.getName().toUpperCase(java.util.Locale.ROOT))
                .anyMatch(roles::contains);
    }

    private void assertCanWrite(User user) {
        if (!isStaff(user) && !hasAnyRole(user, Set.of("ENSEIGNANT"))) {
            throw new BusinessException("Accès refusé : seuls l'administration et les enseignants "
                    + "peuvent publier des cours");
        }
    }

    private void assertCanManage(CourseResource resource, User user) {
        if (isStaff(user)) {
            return;
        }
        if (hasAnyRole(user, Set.of("ENSEIGNANT"))
                && resource.getAuthor() != null
                && Objects.equals(resource.getAuthor().getId(), user.getId())) {
            return;
        }
        throw new BusinessException("Accès refusé : vous ne pouvez gérer que vos propres ressources");
    }

    private void assertCanRead(CourseResource resource, User user) {
        if (isStaff(user)) {
            return;
        }
        if (hasAnyRole(user, Set.of("ENSEIGNANT"))) {
            if (resource.getStatus() == CourseStatus.PUBLISHED) {
                return;
            }
            if (resource.getAuthor() != null
                    && Objects.equals(resource.getAuthor().getId(), user.getId())) {
                return;
            }
            throw new ResourceNotFoundException("Ressource introuvable avec l'identifiant : " + resource.getId());
        }
        if (hasAnyRole(user, Set.of("ELEVE"))) {
            if (resource.getStatus() != CourseStatus.PUBLISHED) {
                // 404 volontaire : un élève ne doit pas déduire l'existence d'un brouillon.
                throw new ResourceNotFoundException("Ressource introuvable avec l'identifiant : " + resource.getId());
            }
            Long classId = studentClassId(user.getId());
            boolean targeted = classId != null && resource.getTargetClasses().stream()
                    .anyMatch(c -> Objects.equals(c.getId(), classId));
            if (!targeted) {
                throw new ResourceNotFoundException("Ressource introuvable avec l'identifiant : " + resource.getId());
            }
            return;
        }
        throw new BusinessException("Le module Cours et ressources n'est pas accessible depuis ce compte");
    }

    /**
     * Un enseignant ne publie que dans les classes où il enseigne réellement la
     * matière choisie. L'administration n'est pas contrainte.
     */
    private void assertTeacherOwnsTargets(User user, Subject subject, List<SchoolClass> classes) {
        if (!isTeacher(user)) {
            return; // staff (ou rôle inconnu, déjà refusé plus haut)
        }
        Teacher teacher = teacherOf(user);
        for (SchoolClass schoolClass : classes) {
            boolean assigned = subjectAssignmentRepository
                    .existsByTeacherIdAndSubjectIdAndSchoolClassId(
                            teacher.getId(), subject.getId(), schoolClass.getId());
            if (!assigned) {
                throw new BusinessException("Vous n'enseignez pas « " + subject.getName()
                        + " » dans la classe « " + schoolClass.getName()
                        + " » : choisissez une classe et une matière qui vous sont affectées");
            }
        }
    }

    private Teacher teacherOf(User user) {
        return teacherRepository.findByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(
                        "Aucun profil enseignant n'est lié à ce compte"));
    }

    private Long studentClassId(Long userId) {
        Student student = studentRepository.findByUserId(userId).orElse(null);
        if (student == null || student.getSchoolClass() == null) {
            return null;
        }
        return student.getSchoolClass().getId();
    }

    // ==================================================================
    // Résolution des entités
    // ==================================================================

    private CourseResource find(Long id) {
        return courseResourceRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Cours", id));
    }

    private Subject subject(Long subjectId) {
        if (subjectId == null) {
            throw new BusinessException("La matière est obligatoire");
        }
        return subjectRepository.findById(subjectId)
                .orElseThrow(() -> ResourceNotFoundException.of("Matière", subjectId));
    }

    /**
     * Charge les classes demandées en conservant l'ordre, sans doublon, et
     * refuse tout identifiant inconnu (sinon la ressource viserait silencieusement
     * moins de classes que demandé).
     */
    private List<SchoolClass> classes(List<Long> classIds) {
        if (classIds == null || classIds.isEmpty()) {
            throw new BusinessException("Au moins une classe destinataire est obligatoire");
        }
        List<Long> distinct = new ArrayList<>(new LinkedHashSet<>(classIds));
        List<SchoolClass> found = schoolClassRepository.findAllById(distinct);
        if (found.size() != distinct.size()) {
            Set<Long> foundIds = new LinkedHashSet<>(found.stream().map(SchoolClass::getId).toList());
            List<Long> unknown = distinct.stream().filter(id -> !foundIds.contains(id)).toList();
            throw new BusinessException("Classe(s) introuvable(s) : " + unknown);
        }
        return found;
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return 10;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
