package com.school.service;

import com.school.entity.AcademicYear;
import com.school.exception.BusinessException;
import com.school.repository.AcademicYearRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Résolution de l'année scolaire de référence.
 *
 * <p>Pourquoi ce service existe : l'année scolaire était déduite de l'année
 * <b>civile</b> ({@code CodeGenerator.currentAcademicYear()} renvoyait
 * {@code N-(N+1)}). Or une année scolaire va d'environ octobre à juillet : de
 * janvier à août, tous les bulletins, procès-verbaux, certificats et en-têtes de
 * PDF portaient l'année SUIVANTE. Conséquence visible : un bulletin T2 généré en
 * février recevait un libellé d'année qui ne correspondait à aucun bulletin
 * existant, et {@code exportClassBulletinsPdf} levait « Aucun bulletin trouvé ».
 *
 * <p>La source de vérité est désormais la table {@code academic_years} : on
 * cherche l'année dont la plage de dates contient le jour demandé, puis on
 * retombe sur l'année marquée « courante ». Le module paie utilisait déjà cette
 * approche ({@code TeacherHoursService.resolveYearFor}) ; elle est ici
 * centralisée pour que le module notes, les rapports et les PDF la partagent.</p>
 */
@Service
@RequiredArgsConstructor
public class AcademicCalendarService {

    private final AcademicYearRepository academicYearRepository;

    /**
     * Année scolaire couvrant la date donnée, sinon l'année marquée courante.
     *
     * @param date date de référence ({@code null} = aujourd'hui)
     * @return l'année trouvée, ou {@link Optional#empty()} si aucune n'est configurée
     */
    @Transactional(readOnly = true)
    public Optional<AcademicYear> findYearFor(LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        Optional<AcademicYear> covering = academicYearRepository.findAll().stream()
                .filter(y -> y.getStartDate() != null && y.getEndDate() != null
                        && !y.getStartDate().isAfter(target) && !y.getEndDate().isBefore(target))
                .findFirst();
        return covering.isPresent() ? covering : academicYearRepository.findByCurrentTrue();
    }

    /**
     * Année scolaire de référence pour une opération métier (génération de
     * bulletins, rapport…). Refuse d'inventer une année : si l'établissement n'a
     * configuré aucune année scolaire, l'utilisateur reçoit un message explicite
     * au lieu de documents étiquetés d'une année fausse.
     *
     * @param date date de référence ({@code null} = aujourd'hui)
     * @throws BusinessException si aucune année scolaire n'est configurée
     */
    @Transactional(readOnly = true)
    public AcademicYear requireYearFor(LocalDate date) {
        return findYearFor(date).orElseThrow(() -> new BusinessException(
                "Aucune année scolaire n'est configurée (ou aucune ne couvre cette date). "
                        + "Créez-la dans Paramètres → Années scolaires avant de générer des bulletins."));
    }

    /**
     * Libellé de l'année scolaire à afficher sur un document.
     *
     * @param date date de référence ({@code null} = aujourd'hui)
     * @return le libellé, ou « — » si l'établissement n'a rien configuré
     */
    @Transactional(readOnly = true)
    public String yearLabel(LocalDate date) {
        return findYearFor(date).map(AcademicYear::getLabel).orElse("—");
    }

    /**
     * Résout une année scolaire par son libellé, par identifiant, ou par date.
     *
     * <p>Utilisé à la création d'une évaluation : le libellé ne peut plus venir
     * « en texte libre » du client (il pouvait estampiller une évaluation
     * « 2019-2020 » et fausser ensuite toutes les moyennes).</p>
     *
     * @param yearId  identifiant explicite, prioritaire s'il est fourni
     * @param label   libellé à valider, utilisé si l'identifiant est absent
     * @param date    date de repli (typiquement la date de l'évaluation)
     * @throws BusinessException si l'année fournie n'existe pas
     */
    @Transactional(readOnly = true)
    public AcademicYear resolveYear(Long yearId, String label, LocalDate date) {
        if (yearId != null) {
            return academicYearRepository.findById(yearId)
                    .orElseThrow(() -> new BusinessException("Année scolaire introuvable : " + yearId));
        }
        if (label != null && !label.isBlank()) {
            String wanted = label.trim();
            Optional<AcademicYear> byLabel = academicYearRepository.findAll().stream()
                    .filter(y -> wanted.equalsIgnoreCase(y.getLabel()))
                    .findFirst();
            if (byLabel.isEmpty()) {
                throw new BusinessException("Année scolaire inconnue : " + wanted
                        + ". Utilisez une année configurée dans Paramètres → Années scolaires.");
            }
            return byLabel.get();
        }
        return requireYearFor(date);
    }
}
