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

/**
 * Contrôle d'accès centralisé (IDOR).
 * Vérifie que l'utilisateur connecté a le droit d'accéder à une ressource
 * (élève, facture, présence) en fonction de son rôle.
 */
@Service
@RequiredArgsConstructor
public class AccessControlService {

    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;

    /**
     * Vérifie que l'utilisateur connecté peut accéder aux données de l'élève.
     * - Rôles admin (SUPER_ADMIN, DIRECTEUR, SECRETAIRE, COMPTABLE) : autorisé
     * - PARENT : autorisé seulement si l'élève est son enfant
     * - ELEVE / ETUDIANT : autorisé seulement si c'est son propre profil
     *
     * @throws BusinessException si l'accès est refusé
     */
    public void assertCanAccessStudent(Long studentId) {
        if (isAdmin()) return; // admins: full access
        if (SecurityUtils.hasRole("PARENT")) {
            Parent parent = currentParent();
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Élève", studentId));
            if (student.getParent() == null || !student.getParent().getId().equals(parent.getId())) {
                throw new BusinessException("Accès refusé : cet élève n'est pas lié à votre compte");
            }
            return;
        }
        if (SecurityUtils.hasRole("ELEVE") || SecurityUtils.hasRole("ETUDIANT")) {
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

    private boolean isAdmin() {
        return SecurityUtils.hasRole("SUPER_ADMIN")
                || SecurityUtils.hasRole("DIRECTEUR")
                || SecurityUtils.hasRole("SECRETAIRE");
    }

    private Parent currentParent() {
        Long userId = SecurityUtils.currentUserId();
        return parentRepository.findByUserIdOrderByIdAsc(userId).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException("Aucun dossier parent lié à ce compte"));
    }
}