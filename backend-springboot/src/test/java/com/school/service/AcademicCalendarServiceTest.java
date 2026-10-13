package com.school.service;

import com.school.entity.AcademicYear;
import com.school.exception.BusinessException;
import com.school.repository.AcademicYearRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Tests de la résolution de l'année scolaire.
 *
 * <p>Régression corrigée : l'année était déduite de l'année CIVILE
 * ({@code N-(N+1)}), donc fausse de janvier à août — bulletins étiquetés d'une
 * année inexistante, export de classe « Aucun bulletin trouvé », certificats et
 * en-têtes PDF erronés. La résolution doit se faire par PLAGE DE DATES sur la
 * table {@code academic_years}, avec repli sur l'année marquée courante.</p>
 */
@ExtendWith(MockitoExtension.class)
class AcademicCalendarServiceTest {

    @Mock
    private AcademicYearRepository academicYearRepository;

    private AcademicCalendarService service;

    @BeforeEach
    void setUp() {
        service = new AcademicCalendarService(academicYearRepository);
    }

    private AcademicYear annee(Long id, String label, LocalDate debut, LocalDate fin, boolean courante) {
        return AcademicYear.builder().id(id).label(label).startDate(debut).endDate(fin)
                .current(courante).build();
    }

    @Test
    @DisplayName("janvier est rattaché à l'année scolaire commencée en octobre (et non à l'année civile)")
    void janvierAppartientALAnneePrecedente() {
        AcademicYear deuxMilleVingtCinq = annee(1L, "2025-2026",
                LocalDate.of(2025, 10, 1), LocalDate.of(2026, 7, 31), false);
        when(academicYearRepository.findAll()).thenReturn(List.of(deuxMilleVingtCinq));

        // Le bug historique : au 15/01/2026, l'ancien code renvoyait « 2026-2027 ».
        assertThat(service.yearLabel(LocalDate.of(2026, 1, 15))).isEqualTo("2025-2026");
        assertThat(service.yearLabel(LocalDate.of(2026, 6, 30))).isEqualTo("2025-2026");
        assertThat(service.yearLabel(LocalDate.of(2025, 11, 2))).isEqualTo("2025-2026");
    }

    @Test
    @DisplayName("la plage de dates tranche entre deux années consécutives")
    void plageDeDatesTrancheEntreDeuxAnnees() {
        AcademicYear ancienne = annee(1L, "2024-2025",
                LocalDate.of(2024, 10, 1), LocalDate.of(2025, 7, 31), false);
        AcademicYear courante = annee(2L, "2025-2026",
                LocalDate.of(2025, 10, 1), LocalDate.of(2026, 7, 31), true);
        when(academicYearRepository.findAll()).thenReturn(List.of(ancienne, courante));

        assertThat(service.yearLabel(LocalDate.of(2025, 3, 10))).isEqualTo("2024-2025");
        assertThat(service.yearLabel(LocalDate.of(2025, 12, 10))).isEqualTo("2025-2026");
    }

    @Test
    @DisplayName("hors de toute plage, repli sur l'année marquée courante")
    void repliSurLAnneeCourante() {
        AcademicYear courante = annee(2L, "2025-2026",
                LocalDate.of(2025, 10, 1), LocalDate.of(2026, 7, 31), true);
        when(academicYearRepository.findAll()).thenReturn(List.of(courante));
        when(academicYearRepository.findByCurrentTrue()).thenReturn(Optional.of(courante));

        // 15/08 : entre deux années scolaires, on reste sur l'année courante
        // au lieu de basculer sur l'année civile suivante.
        assertThat(service.yearLabel(LocalDate.of(2026, 8, 15))).isEqualTo("2025-2026");
    }

    @Test
    @DisplayName("aucune année configurée : requireYearFor refuse au lieu d'inventer un libellé")
    void aucuneAnneeConfigureeEstRefusee() {
        when(academicYearRepository.findAll()).thenReturn(List.of());
        when(academicYearRepository.findByCurrentTrue()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireYearFor(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Aucune année scolaire");
        // Les documents PDF affichent un tiret au lieu d'une année inventée.
        assertThat(service.yearLabel(null)).isEqualTo("—");
    }

    @Test
    @DisplayName("une année sans dates est ignorée pour le calcul par plage")
    void anneeSansDatesIgnoree() {
        AcademicYear sansDates = annee(1L, "SANS-DATES", null, null, true);
        when(academicYearRepository.findAll()).thenReturn(List.of(sansDates));
        when(academicYearRepository.findByCurrentTrue()).thenReturn(Optional.of(sansDates));

        assertThat(service.yearLabel(LocalDate.of(2026, 3, 1))).isEqualTo("SANS-DATES");
    }

    @Test
    @DisplayName("resolveYear valide le libellé fourni par le client")
    void resolveYearValideLeLibelle() {
        AcademicYear courante = annee(2L, "2025-2026",
                LocalDate.of(2025, 10, 1), LocalDate.of(2026, 7, 31), true);
        when(academicYearRepository.findAll()).thenReturn(List.of(courante));

        assertThat(service.resolveYear(null, "2025-2026", null).getLabel()).isEqualTo("2025-2026");
        // Un libellé fantaisiste (ex. « 2019-2020 ») ne peut plus estampiller une
        // évaluation : il faussait ensuite toutes les moyennes de l'année.
        assertThatThrownBy(() -> service.resolveYear(null, "2019-2020", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Année scolaire inconnue");
    }

    @Test
    @DisplayName("resolveYear accepte un identifiant explicite")
    void resolveYearParIdentifiant() {
        AcademicYear annee = annee(7L, "2024-2025", LocalDate.of(2024, 10, 1),
                LocalDate.of(2025, 7, 31), false);
        when(academicYearRepository.findById(7L)).thenReturn(Optional.of(annee));

        assertThat(service.resolveYear(7L, "peu-importe", null).getLabel()).isEqualTo("2024-2025");
    }

    @Test
    @DisplayName("resolveYear refuse un identifiant inexistant")
    void resolveYearIdentifiantInconnu() {
        when(academicYearRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveYear(99L, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("introuvable");
    }
}
