package com.school.service;

import com.school.dto.request.AppreciationBandRequest;
import com.school.dto.request.AppreciationScaleRequest;
import com.school.entity.AppreciationBand;
import com.school.entity.AppreciationScale;
import com.school.enums.EducationCycle;
import com.school.exception.BusinessException;
import com.school.repository.AppreciationScaleRepository;
import com.school.repository.BulletinRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests du service de barèmes : résolution des appréciations et validation.
 *
 * <p>Ce qui est prouvé : les bornes sont incluses/exclues comme annoncé
 * (18,00 → Excellent, 17,99 → Très bien), les décimales sont gérées, et un
 * barème troué, chevauchant ou incomplet est refusé.</p>
 */
@ExtendWith(MockitoExtension.class)
class AppreciationScaleServiceTest {

    @Mock private AppreciationScaleRepository scaleRepository;
    @Mock private BulletinRepository bulletinRepository;
    @Mock private AuditService auditService;

    private AppreciationScaleService service;

    @BeforeEach
    void setUp() {
        service = new AppreciationScaleService(scaleRepository, bulletinRepository, auditService);
    }

    // ------------------------------------------------------------------
    // Fabriques
    // ------------------------------------------------------------------

    private static AppreciationScale scale(String... labels) {
        AppreciationScale scale = AppreciationScale.builder()
                .id(1L).name("Barème test").cycle(EducationCycle.COLLEGE)
                .minNote(BigDecimal.ZERO).maxNote(new BigDecimal("20"))
                .version(1).active(true).build();
        BigDecimal[][] intervals = {
                {BigDecimal.ZERO, new BigDecimal("8")},
                {new BigDecimal("8"), new BigDecimal("10")},
                {new BigDecimal("10"), new BigDecimal("12")},
                {new BigDecimal("12"), new BigDecimal("14")},
                {new BigDecimal("14"), new BigDecimal("16")},
                {new BigDecimal("16"), new BigDecimal("18")},
                {new BigDecimal("18"), new BigDecimal("20")},
        };
        for (int i = 0; i < labels.length; i++) {
            scale.addBand(AppreciationBand.builder()
                    .lowerBound(intervals[i][0]).upperBound(intervals[i][1])
                    .label(labels[i]).sortOrder(i + 1).build());
        }
        return scale;
    }

    private static AppreciationScale defaultScale() {
        return scale("Très insuffisant", "Insuffisant", "Passable", "Assez bien",
                "Bien", "Très bien", "Excellent");
    }

    private static AppreciationBandRequest band(String lo, String hi, String label) {
        AppreciationBandRequest b = new AppreciationBandRequest();
        b.setLowerBound(new BigDecimal(lo));
        b.setUpperBound(new BigDecimal(hi));
        b.setLabel(label);
        return b;
    }

    private static AppreciationScaleRequest request(List<AppreciationBandRequest> bands) {
        AppreciationScaleRequest r = new AppreciationScaleRequest();
        r.setName("Barème");
        r.setCycle(EducationCycle.COLLEGE);
        r.setMinNote(BigDecimal.ZERO);
        r.setMaxNote(new BigDecimal("20"));
        r.setBands(bands);
        return r;
    }

    // ------------------------------------------------------------------
    // Résolution
    // ------------------------------------------------------------------

    @Test
    @DisplayName("18,00 est Excellent et 17,99 est Très bien (borne haute exclue)")
    void bornesExclusives() {
        when(scaleRepository.findActiveWithBands(EducationCycle.COLLEGE))
                .thenReturn(Optional.of(defaultScale()));

        assertThat(service.resolve(new BigDecimal("18.00"), EducationCycle.COLLEGE)).isEqualTo("Excellent");
        assertThat(service.resolve(new BigDecimal("17.99"), EducationCycle.COLLEGE)).isEqualTo("Très bien");
        assertThat(service.resolve(new BigDecimal("20.00"), EducationCycle.COLLEGE)).isEqualTo("Excellent");
        assertThat(service.resolve(new BigDecimal("0.00"), EducationCycle.COLLEGE)).isEqualTo("Très insuffisant");
    }

    @Test
    @DisplayName("Les notes décimales sont interprétées correctement")
    void notesDecimales() {
        when(scaleRepository.findActiveWithBands(EducationCycle.COLLEGE))
                .thenReturn(Optional.of(defaultScale()));

        assertThat(service.resolve(new BigDecimal("9.5"), EducationCycle.COLLEGE)).isEqualTo("Insuffisant");
        assertThat(service.resolve(new BigDecimal("12.75"), EducationCycle.COLLEGE)).isEqualTo("Assez bien");
        assertThat(service.resolve(new BigDecimal("17.25"), EducationCycle.COLLEGE)).isEqualTo("Très bien");
    }

    @Test
    @DisplayName("Sans barème actif, l'erreur est explicite au lieu d'une appréciation inventée")
    void sansBaremeActif_erreurExplicite() {
        when(scaleRepository.findActiveWithBands(EducationCycle.COLLEGE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve(new BigDecimal("12"), EducationCycle.COLLEGE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Aucun barème d'appréciation actif");
    }

    @Test
    @DisplayName("Un cycle inconnu affiche un tiret plutôt qu'une appréciation fausse")
    void cycleInconnu_neDevinePas() {
        assertThat(service.resolve(new BigDecimal("12"), null))
                .isEqualTo(AppreciationScaleService.NO_APPRECIATION);
    }

    @Test
    @DisplayName("Deux cycles peuvent avoir des seuils différents")
    void baremesDifferentsParCycle() {
        AppreciationScale college = defaultScale();
        AppreciationScale primaire = scale("Faible", "Moyen", "Passable", "Assez bien",
                "Bien", "Très bien", "Excellent");
        primaire.setCycle(EducationCycle.PRIMAIRE);
        when(scaleRepository.findActiveWithBands(EducationCycle.COLLEGE)).thenReturn(Optional.of(college));
        when(scaleRepository.findActiveWithBands(EducationCycle.PRIMAIRE)).thenReturn(Optional.of(primaire));

        assertThat(service.resolve(new BigDecimal("7.5"), EducationCycle.COLLEGE)).isEqualTo("Très insuffisant");
        assertThat(service.resolve(new BigDecimal("7.5"), EducationCycle.PRIMAIRE)).isEqualTo("Faible");
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Un barème complet et contigu est accepté")
    void baremeValide_estAccepte() {
        var bands = service.validate(request(List.of(
                band("0", "10", "Faible"), band("10", "20", "Fort"))));

        assertThat(bands).hasSize(2);
        assertThat(bands.get(0).getLabel()).isEqualTo("Faible");
    }

    @Test
    @DisplayName("Des intervalles qui se chevauchent sont refusés")
    void chevauchement_refuse() {
        assertThatThrownBy(() -> service.validate(request(List.of(
                band("0", "11", "A"), band("10", "20", "B")))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chevauchent");
    }

    @Test
    @DisplayName("Un trou dans la couverture est refusé")
    void trou_refuse() {
        assertThatThrownBy(() -> service.validate(request(List.of(
                band("0", "9", "A"), band("10", "20", "B")))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Trou");
    }

    @Test
    @DisplayName("Une couverture incomplète (ne finit pas à 20) est refusée")
    void couvertureIncomplete_refusee() {
        assertThatThrownBy(() -> service.validate(request(List.of(
                band("0", "10", "A"), band("10", "19", "B")))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("dernier intervalle");
    }

    @Test
    @DisplayName("Un intervalle inversé est refusé")
    void intervalleInverse_refuse() {
        assertThatThrownBy(() -> service.validate(request(List.of(
                band("0", "12", "A"), band("12", "10", "B"), band("10", "20", "C")))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inférieure");
    }

    @Test
    @DisplayName("Un libellé vide est refusé")
    void libelleVide_refuse() {
        assertThatThrownBy(() -> service.validate(request(List.of(
                band("0", "10", "  "), band("10", "20", "B")))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("libellé");
    }

    // ------------------------------------------------------------------
    // Activation
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Activer un barème désactive l'autre barème actif du même cycle")
    void activation_desactiveLesAutres() {
        AppreciationScale target = defaultScale();
        AppreciationScale other = defaultScale();
        other.setId(2L);
        other.setActive(true);
        when(scaleRepository.findWithBandsById(1L)).thenReturn(Optional.of(target));
        when(scaleRepository.findByCycleWithBands(EducationCycle.COLLEGE))
                .thenReturn(new ArrayList<>(List.of(target, other)));
        when(scaleRepository.save(any(AppreciationScale.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.activate(1L, null);

        assertThat(response.isActive()).isTrue();
        assertThat(other.isActive()).isFalse();
        verify(scaleRepository, org.mockito.Mockito.atLeastOnce()).save(other);
    }
}
