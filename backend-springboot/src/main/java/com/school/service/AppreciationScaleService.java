package com.school.service;

import com.school.dto.request.AppreciationBandRequest;
import com.school.dto.request.AppreciationScaleRequest;
import com.school.dto.response.AppreciationPreviewResponse;
import com.school.dto.response.AppreciationScaleResponse;
import com.school.entity.AppreciationBand;
import com.school.entity.AppreciationScale;
import com.school.entity.User;
import com.school.enums.EducationCycle;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.AppreciationScaleRepository;
import com.school.repository.BulletinRepository;
import com.school.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Barèmes d'appréciation configurables par cycle scolaire.
 *
 * <p>Ce service est la SEULE source des appréciations automatiques : le
 * bulletin PDF, les écrans de consultation et la mention stockée passent tous
 * par {@link #resolve(BigDecimal, EducationCycle)}. Les seuils ne sont plus
 * codés en dur dans le code source.</p>
 *
 * <p><b>Versionnement.</b> Modifier un barème déjà actif, ou déjà utilisé par
 * un bulletin, crée une nouvelle version au lieu d'écraser l'ancienne. Les
 * bulletins conservent {@code appreciation_scale_id}, donc une reconfiguration
 * ne réécrit jamais une appréciation déjà délivrée.</p>
 *
 * <p><b>Intervalles.</b> {@code [borne basse incluse, borne haute exclue)},
 * sauf le dernier intervalle qui inclut la borne haute de l'échelle.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class AppreciationScaleService {

    /** Valeur renvoyée quand aucune note n'est disponible. */
    public static final String NO_APPRECIATION = "—";

    private static final BigDecimal MAX_ALLOWED = new BigDecimal("1000");

    private final AppreciationScaleRepository scaleRepository;
    private final BulletinRepository bulletinRepository;
    private final AuditService auditService;

    // ==================================================================
    // Résolution (utilisée par les bulletins)
    // ==================================================================

    /**
     * Appréciation d'une note pour un cycle, selon le barème ACTIF.
     *
     * @throws BusinessException si aucun barème actif n'existe pour ce cycle —
     *                           mieux vaut une erreur explicite qu'une
     *                           appréciation inventée
     */
    public String resolve(BigDecimal note, EducationCycle cycle) {
        if (note == null) {
            return NO_APPRECIATION;
        }
        if (cycle == null) {
            // Cycle non renseigné sur l'élève : on n'invente pas d'appréciation,
            // mais on ne bloque pas non plus la génération du bulletin.
            log.warn("Appréciation non résolue : cycle scolaire inconnu pour la note {}", note);
            return NO_APPRECIATION;
        }
        return labelFor(scaleFor(null, cycle), note);
    }

    /**
     * Appréciation d'une note selon une VERSION précise (bulletin déjà généré).
     *
     * @param scaleId version épinglée sur le bulletin ; {@code null} = barème actif
     */
    public String resolveForScale(Long scaleId, BigDecimal note, EducationCycle cycle) {
        if (note == null) {
            return NO_APPRECIATION;
        }
        if (scaleId == null) {
            return resolve(note, cycle);
        }
        return labelFor(scaleFor(scaleId, cycle), note);
    }

    /**
     * Barème à appliquer à un calcul de masse (bulletins d'une classe, lignes
     * d'un bulletin) : à résoudre UNE fois par cycle, puis à passer à
     * {@link #labelFor(AppreciationScale, BigDecimal)}.
     *
     * <p>Sans cette séparation, chaque ligne de bulletin relançait la requête du
     * barème (N+1 sur les moyennes).</p>
     *
     * @param scaleId version épinglée ; {@code null} = barème actif du cycle
     * @return le barème, ou {@code null} uniquement si le cycle est inconnu
     */
    public AppreciationScale scaleFor(Long scaleId, EducationCycle cycle) {
        if (scaleId != null) {
            Optional<AppreciationScale> pinned = scaleRepository.findWithBandsById(scaleId);
            if (pinned.isPresent()) {
                return pinned.get();
            }
        }
        if (cycle == null) {
            return null;
        }
        return scaleRepository.findActiveWithBands(cycle)
                .orElseThrow(() -> new BusinessException(scaleId != null
                        ? "Barème d'appréciation introuvable (id " + scaleId + ")"
                        : "Aucun barème d'appréciation actif pour le cycle " + cycle
                        + ". Configurez-le dans « Barèmes d'appréciation »."));
    }

    /** Libellé d'une note pour un barème déjà résolu ({@code null} → « — »). */
    public String labelFor(AppreciationScale scale, BigDecimal note) {
        if (note == null || scale == null) {
            return NO_APPRECIATION;
        }
        return labelOf(scale, note);
    }

    /** Vrai si le cycle possède un barème actif (message clair côté administration). */
    public boolean hasActiveScale(EducationCycle cycle) {
        return cycle != null && scaleRepository.existsByCycleAndActiveTrue(cycle);
    }

    private String labelOf(AppreciationScale scale, BigDecimal note) {
        if (scale.getBands() == null || scale.getBands().isEmpty()) {
            throw new BusinessException("Le barème « " + scale.getName() + " » ne contient aucun intervalle");
        }
        if (note.compareTo(scale.getMinNote()) < 0 || note.compareTo(scale.getMaxNote()) > 0) {
            // Une note hors échelle ne doit pas recevoir d'appréciation arbitraire.
            return NO_APPRECIATION;
        }
        List<AppreciationBand> bands = new ArrayList<>(scale.getBands());
        bands.sort(Comparator.comparing(AppreciationBand::getLowerBound));
        for (int i = 0; i < bands.size(); i++) {
            AppreciationBand band = bands.get(i);
            boolean last = i == bands.size() - 1;
            boolean aboveLower = note.compareTo(band.getLowerBound()) >= 0;
            boolean belowUpper = last
                    ? note.compareTo(band.getUpperBound()) <= 0
                    : note.compareTo(band.getUpperBound()) < 0;
            if (aboveLower && belowUpper) {
                return band.getLabel();
            }
        }
        return NO_APPRECIATION;
    }

    // ==================================================================
    // Lecture
    // ==================================================================

    public List<AppreciationScaleResponse> list(EducationCycle cycle, Boolean active) {
        List<AppreciationScale> scales = cycle != null
                ? scaleRepository.findByCycleWithBands(cycle)
                : scaleRepository.findAllWithBands();
        return scales.stream()
                .filter(scale -> active == null || scale.isActive() == active)
                .sorted(Comparator.comparing(AppreciationScale::getCycle)
                        .thenComparing(AppreciationScale::getVersion).reversed())
                .map(AppreciationScaleResponse::from)
                .toList();
    }

    public AppreciationScaleResponse get(Long id) {
        return AppreciationScaleResponse.from(find(id));
    }

    // ==================================================================
    // Écriture
    // ==================================================================

    @Transactional
    public AppreciationScaleResponse create(AppreciationScaleRequest request, HttpServletRequest httpRequest) {
        List<AppreciationBandRequest> bands = validate(request);
        if (scaleRepository.existsByCycleAndActiveTrue(request.getCycle())) {
            throw new BusinessException("Un barème actif existe déjà pour le cycle "
                    + request.getCycle() + " : désactivez-le avant d'en activer un autre");
        }
        AppreciationScale scale = AppreciationScale.builder()
                .name(request.getName().trim())
                .cycle(request.getCycle())
                .minNote(request.getMinNote())
                .maxNote(request.getMaxNote())
                .version(nextVersion(request.getCycle()))
                .active(false)
                .createdBy(SecurityUtils.currentUser())
                .build();
        applyBands(scale, bands);
        AppreciationScale saved = scaleRepository.save(scale);
        auditService.log("CREATE", "AppreciationScale", saved.getId(),
                "Création du barème « " + saved.getName() + " » (" + saved.getCycle() + ", v"
                        + saved.getVersion() + ")", httpRequest);
        return AppreciationScaleResponse.from(saved);
    }

    /**
     * Modifie un barème.
     *
     * <p>Si le barème est actif ou déjà référencé par un bulletin, une nouvelle
     * version est créée : les bulletins existants gardent l'ancienne.</p>
     */
    @Transactional
    public AppreciationScaleResponse update(Long id, AppreciationScaleRequest request,
                                            HttpServletRequest httpRequest) {
        AppreciationScale scale = find(id);
        if (request.getCycle() != null && request.getCycle() != scale.getCycle()) {
            throw new BusinessException("Le cycle d'un barème ne peut pas être changé : dupliquez-le");
        }
        List<AppreciationBandRequest> bands = validate(request);

        boolean pinned = scale.isActive() || bulletinRepository.existsByAppreciationScaleId(id);
        AppreciationScale saved;
        if (pinned) {
            AppreciationScale next = AppreciationScale.builder()
                    .name(request.getName().trim())
                    .cycle(scale.getCycle())
                    .minNote(request.getMinNote())
                    .maxNote(request.getMaxNote())
                    .version(nextVersion(scale.getCycle()))
                    .active(scale.isActive())
                    .createdBy(SecurityUtils.currentUser())
                    .build();
            applyBands(next, bands);
            scale.setActive(false);
            scaleRepository.save(scale);
            saved = scaleRepository.save(next);
        } else {
            scale.setName(request.getName().trim());
            scale.setMinNote(request.getMinNote());
            scale.setMaxNote(request.getMaxNote());
            scale.clearBands();
            applyBands(scale, bands);
            saved = scaleRepository.save(scale);
        }
        auditService.log("UPDATE", "AppreciationScale", saved.getId(),
                "Modification du barème « " + saved.getName() + " » (" + saved.getCycle()
                        + ", v" + saved.getVersion() + ")", httpRequest);
        return AppreciationScaleResponse.from(saved);
    }

    @Transactional
    public AppreciationScaleResponse activate(Long id, HttpServletRequest httpRequest) {
        AppreciationScale scale = find(id);
        validateScaleEntity(scale);
        // Un seul actif par cycle : on désactive les autres versions.
        for (AppreciationScale other : scaleRepository.findByCycleWithBands(scale.getCycle())) {
            if (!other.getId().equals(scale.getId()) && other.isActive()) {
                other.setActive(false);
                scaleRepository.save(other);
            }
        }
        scale.setActive(true);
        AppreciationScale saved = scaleRepository.save(scale);
        auditService.log("ACTIVATE", "AppreciationScale", id,
                "Activation du barème « " + saved.getName() + " » (" + saved.getCycle()
                        + ", v" + saved.getVersion() + ")", httpRequest);
        return AppreciationScaleResponse.from(saved);
    }

    @Transactional
    public AppreciationScaleResponse deactivate(Long id, HttpServletRequest httpRequest) {
        AppreciationScale scale = find(id);
        if (!scale.isActive()) {
            return AppreciationScaleResponse.from(scale);
        }
        scale.setActive(false);
        AppreciationScale saved = scaleRepository.save(scale);
        auditService.log("DEACTIVATE", "AppreciationScale", id,
                "Désactivation du barème « " + saved.getName() + " » (" + saved.getCycle() + ")", httpRequest);
        return AppreciationScaleResponse.from(saved);
    }

    /** Duplique un barème en brouillon (inactif) pour un autre cycle ou une variante. */
    @Transactional
    public AppreciationScaleResponse duplicate(Long id, HttpServletRequest httpRequest) {
        AppreciationScale source = find(id);
        AppreciationScale copy = AppreciationScale.builder()
                .name(source.getName() + " (copie)")
                .cycle(source.getCycle())
                .minNote(source.getMinNote())
                .maxNote(source.getMaxNote())
                .version(nextVersion(source.getCycle()))
                .active(false)
                .createdBy(SecurityUtils.currentUser())
                .build();
        for (AppreciationBand band : source.getBands()) {
            copy.addBand(AppreciationBand.builder()
                    .lowerBound(band.getLowerBound())
                    .upperBound(band.getUpperBound())
                    .label(band.getLabel())
                    .observation(band.getObservation())
                    .sortOrder(band.getSortOrder())
                    .build());
        }
        AppreciationScale saved = scaleRepository.save(copy);
        auditService.log("CREATE", "AppreciationScale", saved.getId(),
                "Duplication de « " + source.getName() + " » → « " + saved.getName() + " »", httpRequest);
        return AppreciationScaleResponse.from(saved);
    }

    /** Prévisualisation : libellé obtenu pour des notes d'exemple, sans rien enregistrer. */
    public AppreciationPreviewResponse preview(AppreciationScaleRequest request) {
        List<AppreciationBandRequest> bands = validate(request);
        List<BigDecimal> samples = List.of(
                request.getMinNote(), new BigDecimal("7.99"), new BigDecimal("8.00"),
                new BigDecimal("9.50"), new BigDecimal("11.99"), new BigDecimal("12.00"),
                new BigDecimal("12.75"), new BigDecimal("15.99"), new BigDecimal("16.00"),
                new BigDecimal("17.25"), new BigDecimal("17.99"), new BigDecimal("18.00"),
                request.getMaxNote());
        List<AppreciationPreviewResponse.Sample> resolved = new ArrayList<>();
        for (BigDecimal note : samples) {
            if (note.compareTo(request.getMinNote()) < 0 || note.compareTo(request.getMaxNote()) > 0) {
                continue;
            }
            if (resolved.stream().anyMatch(s -> s.getNote().compareTo(note) == 0)) {
                continue;
            }
            AppreciationBandRequest band = bandFor(bands, note, request.getMaxNote());
            resolved.add(AppreciationPreviewResponse.Sample.builder()
                    .note(note)
                    .label(band != null ? band.getLabel() : NO_APPRECIATION)
                    .observation(band != null ? band.getObservation() : null)
                    .build());
        }
        return AppreciationPreviewResponse.builder()
                .scaleName(request.getName())
                .cycle(request.getCycle() != null ? request.getCycle().name() : null)
                .minNote(request.getMinNote())
                .maxNote(request.getMaxNote())
                .samples(resolved)
                .build();
    }

    // ==================================================================
    // Validation
    // ==================================================================

    /**
     * Vérifie un barème complet et renvoie ses intervalles triés.
     *
     * <p>Refuse : bornes inversées, chevauchement, trou, intervalle hors
     * échelle, libellé vide, couverture incomplète.</p>
     */
    List<AppreciationBandRequest> validate(AppreciationScaleRequest request) {
        if (request.getMinNote() == null || request.getMaxNote() == null) {
            throw new BusinessException("Les bornes de l'échelle sont obligatoires");
        }
        if (request.getMinNote().compareTo(request.getMaxNote()) >= 0) {
            throw new BusinessException("La note minimale doit être strictement inférieure à la note maximale");
        }
        if (request.getMinNote().abs().compareTo(MAX_ALLOWED) > 0
                || request.getMaxNote().abs().compareTo(MAX_ALLOWED) > 0) {
            throw new BusinessException("Les bornes de l'échelle sont hors limites (max " + MAX_ALLOWED + ")");
        }
        if (request.getBands() == null || request.getBands().isEmpty()) {
            throw new BusinessException("Au moins un intervalle est obligatoire");
        }

        List<AppreciationBandRequest> sorted = new ArrayList<>(request.getBands());
        for (AppreciationBandRequest band : sorted) {
            if (band.getLowerBound() == null || band.getUpperBound() == null) {
                throw new BusinessException("Chaque intervalle doit avoir une borne basse et une borne haute");
            }
            if (band.getLowerBound().compareTo(band.getUpperBound()) >= 0) {
                throw new BusinessException("Intervalle invalide : la borne basse ("
                        + band.getLowerBound() + ") doit être inférieure à la borne haute ("
                        + band.getUpperBound() + ")");
            }
            if (band.getLabel() == null || band.getLabel().isBlank()) {
                throw new BusinessException("Chaque intervalle doit avoir un libellé");
            }
            if (band.getLowerBound().compareTo(request.getMinNote()) < 0
                    || band.getUpperBound().compareTo(request.getMaxNote()) > 0) {
                throw new BusinessException("L'intervalle « " + band.getLabel() + " » sort de l'échelle ["
                        + request.getMinNote() + " ; " + request.getMaxNote() + "]");
            }
        }
        sorted.sort(Comparator.comparing(AppreciationBandRequest::getLowerBound));

        if (sorted.get(0).getLowerBound().compareTo(request.getMinNote()) != 0) {
            throw new BusinessException("Le barème doit couvrir toute l'échelle : le premier intervalle doit"
                    + " commencer à " + request.getMinNote());
        }
        BigDecimal expected = request.getMinNote();
        for (AppreciationBandRequest band : sorted) {
            int cmp = band.getLowerBound().compareTo(expected);
            if (cmp > 0) {
                throw new BusinessException("Trou dans la couverture : aucune appréciation entre "
                        + expected + " et " + band.getLowerBound());
            }
            if (cmp < 0) {
                throw new BusinessException("Intervalles qui se chevauchent autour de " + expected);
            }
            expected = band.getUpperBound();
        }
        if (expected.compareTo(request.getMaxNote()) != 0) {
            throw new BusinessException("Le barème doit couvrir toute l'échelle : le dernier intervalle doit"
                    + " se terminer à " + request.getMaxNote());
        }
        return sorted;
    }

    private void validateScaleEntity(AppreciationScale scale) {
        AppreciationScaleRequest request = new AppreciationScaleRequest();
        request.setName(scale.getName());
        request.setCycle(scale.getCycle());
        request.setMinNote(scale.getMinNote());
        request.setMaxNote(scale.getMaxNote());
        request.setBands(scale.getBands().stream()
                .map(band -> {
                    AppreciationBandRequest b = new AppreciationBandRequest();
                    b.setLowerBound(band.getLowerBound());
                    b.setUpperBound(band.getUpperBound());
                    b.setLabel(band.getLabel());
                    b.setObservation(band.getObservation());
                    return b;
                })
                .toList());
        validate(request);
    }

    private void applyBands(AppreciationScale scale, List<AppreciationBandRequest> bands) {
        int order = 1;
        for (AppreciationBandRequest request : bands) {
            scale.addBand(AppreciationBand.builder()
                    .lowerBound(request.getLowerBound())
                    .upperBound(request.getUpperBound())
                    .label(request.getLabel().trim())
                    .observation(request.getObservation() != null && !request.getObservation().isBlank()
                            ? request.getObservation().trim() : null)
                    .sortOrder(order++)
                    .build());
        }
    }

    private AppreciationBandRequest bandFor(List<AppreciationBandRequest> bands,
                                            BigDecimal note, BigDecimal maxNote) {
        for (int i = 0; i < bands.size(); i++) {
            AppreciationBandRequest band = bands.get(i);
            boolean last = i == bands.size() - 1;
            boolean above = note.compareTo(band.getLowerBound()) >= 0;
            boolean below = last
                    ? note.compareTo(band.getUpperBound()) <= 0
                    : note.compareTo(band.getUpperBound()) < 0;
            if (above && below) {
                return band;
            }
        }
        return null;
    }

    private int nextVersion(EducationCycle cycle) {
        return scaleRepository.findByCycleWithBands(cycle).stream()
                .map(AppreciationScale::getVersion)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private AppreciationScale find(Long id) {
        return scaleRepository.findWithBandsById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Barème d'appréciation", id));
    }

    /** Utilisateur courant, toléré absent (tâches système). */
    private User currentUser() {
        return SecurityUtils.currentUser();
    }
}
