package com.school.service;

import com.school.entity.Parent;
import com.school.entity.Student;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.ParentRepository;
import com.school.repository.StudentRepository;
import com.school.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Contrôle d'accès centralisé (IDOR).
 * Vérifie que l'utilisateur connecté a le droit d'accéder à une ressource
 * (élève, facture, présence) en fonction de son rôle.
 *
 * <p>Rôles « à périmètre restreint » (PARENT, ELEVE) : un identifiant d'élève
 * fourni par le client ne peut jamais élargir le périmètre. Les listes sans
 * filtre sont donc automatiquement ramenées à l'élève (ou aux enfants) du
 * compte courant par {@link #resolveStudentScope(Long)} ; les accès par
 * identifiant passent par {@link #assertCanAccessStudent(Long)}.</p>
 */
@Service
@RequiredArgsConstructor
public class AccessControlService {

    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;

    /**
     * Vérifie que l'utilisateur connecté peut accéder aux données de l'élève.
     * - Rôles admin (SUPER_ADMIN, DIRECTEUR, SECRETAIRE) : autorisé
     * - PARENT : autorisé seulement si l'élève est l'un de ses enfants
     * - ELEVE : autorisé seulement si c'est son propre profil
     * - ENSEIGNANT, COMPTABLE et autres : autorisé
     *
     * @throws BusinessException si l'accès est refusé
     */
    public void assertCanAccessStudent(Long studentId) {
        if (isAdmin()) return; // admins: full access
        if (SecurityUtils.hasRole("PARENT")) {
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Élève", studentId));
            if (student.getParent() == null
                    || !currentParentIds().contains(student.getParent().getId())) {
                throw new BusinessException("Accès refusé : cet élève n'est pas lié à votre compte");
            }
            return;
        }
        if (SecurityUtils.hasRole("ELEVE")) {
            Student current = studentRepository.findByUserId(SecurityUtils.currentUserId())
                    .orElseThrow(() -> new BusinessException("Aucun profil élève lié à votre compte"));
            if (!current.getId().equals(studentId)) {
                throw new BusinessException("Accès refusé : vous ne pouvez consulter que votre propre profil");
            }
            return;
        }
        // Autres rôles (ENSEIGNANT, COMPTABLE, etc.) : autorisé
    }

    /**
     * Vérifie que l'utilisateur connecté peut accéder aux données financières ou
     * de présence d'un élève (mêmes règles que ci-dessus).
     */
    public void assertCanAccessStudentData(Long studentId) {
        assertCanAccessStudent(studentId);
    }

    /**
     * Ramène un identifiant d'élève demandé au périmètre réel du compte courant.
     *
     * <p>Pour un PARENT ou un ELEVE, l'identifiant fourni ne peut pas élargir le
     * périmètre : s'il est absent on renvoie le périmètre complet du compte, s'il
     * désigne un élève hors périmètre l'accès est refusé. Sans cette méthode, un
     * endpoint de liste appelé sans {@code studentId} renvoyait les données de
     * TOUS les élèves (fuite horizontale).</p>
     *
     * @param requestedId identifiant demandé par le client (peut être {@code null})
     * @return périmètre à appliquer dans la requête
     */
    public StudentScope resolveStudentScope(Long requestedId) {
        if (!hasStudentScope()) {
            return StudentScope.unrestricted(requestedId);
        }
        if (requestedId != null) {
            assertCanAccessStudentData(requestedId);
            return StudentScope.ofIds(List.of(requestedId));
        }
        if (SecurityUtils.hasRole("PARENT")) {
            // Sans filtre explicite : TOUS les enfants du compte, sur tous ses
            // dossiers parent (jamais l'établissement entier).
            List<Long> children = studentRepository.findByParentIdIn(currentParentIds())
                    .stream().map(Student::getId).toList();
            return StudentScope.ofIds(children);
        }
        return StudentScope.ofIds(List.of(currentStudentId()));
    }

    /**
     * Périmètre de lecture à appliquer à une requête portant sur des élèves.
     *
     * <p>Deux cas doivent être distingués, ce qu'un simple {@code null} ne permet
     * pas : {@code unrestricted} (personnel : aucune restriction) et une liste
     * d'identifiants (parent ou élève, éventuellement VIDE — un compte parent
     * sans enfant ne doit rien voir, surtout pas tout l'établissement).</p>
     *
     * @param unrestricted vrai pour le personnel (aucun filtre)
     * @param studentIds   identifiants autorisés ; vide = aucun résultat
     * @param requestedId  identifiant explicitement demandé, {@code null} si aucun
     */
    public record StudentScope(boolean unrestricted, List<Long> studentIds, Long requestedId) {

        static StudentScope unrestricted(Long requestedId) {
            return new StudentScope(true, List.of(), requestedId);
        }

        static StudentScope ofIds(List<Long> ids) {
            return new StudentScope(false, List.copyOf(ids), ids.size() == 1 ? ids.get(0) : null);
        }

        /** Vrai si le périmètre ne peut renvoyer aucune ligne. */
        public boolean isEmpty() {
            return !unrestricted && studentIds.isEmpty();
        }
    }

    /** Vrai si le compte courant est un parent ou un élève (périmètre restreint). */
    public boolean hasStudentScope() {
        return SecurityUtils.hasRole("PARENT") || SecurityUtils.hasRole("ELEVE");
    }

    /**
     * Réserve une donnée « établissement » (pointage d'une classe, présences des
     * enseignants, salaires des collègues…) aux membres du personnel : un parent
     * ou un élève n'a aucune raison légitime d'y accéder, même avec la
     * permission de lecture du module.
     *
     * @param resource libellé de la ressource, utilisé dans le message d'erreur
     * @throws BusinessException si le compte courant est un parent ou un élève
     */
    public void assertStaffOnly(String resource) {
        if (hasStudentScope()) {
            throw new BusinessException("Accès refusé : " + resource
                    + " n'est pas accessible depuis un compte parent ou élève");
        }
    }

    /** Identifiant de l'élève rattaché au compte courant (rôle ELEVE). */
    public Long currentStudentId() {
        return studentRepository.findByUserId(SecurityUtils.currentUserId())
                .map(Student::getId)
                .orElseThrow(() -> new BusinessException("Aucun profil élève lié à votre compte"));
    }

    /**
     * Identifiants de TOUS les dossiers parent du compte courant.
     * Un même compte peut être lié à plusieurs dossiers (un parent qui inscrit
     * ses enfants séparément) : ne retenir que le premier refusait l'accès aux
     * autres enfants, de façon incohérente selon l'endpoint.
     */
    private List<Long> currentParentIds() {
        List<Long> ids = parentRepository.findByUserIdOrderByIdAsc(SecurityUtils.currentUserId())
                .stream().map(Parent::getId).toList();
        if (ids.isEmpty()) {
            throw new BusinessException("Aucun dossier parent lié à ce compte");
        }
        return ids;
    }

    private boolean isAdmin() {
        return SecurityUtils.hasRole("SUPER_ADMIN")
                || SecurityUtils.hasRole("DIRECTEUR")
                || SecurityUtils.hasRole("SECRETAIRE");
    }
}
