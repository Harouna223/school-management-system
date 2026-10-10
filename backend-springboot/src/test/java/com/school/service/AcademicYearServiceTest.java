package com.school.service;

import com.school.dto.request.AcademicYearRequest;
import com.school.entity.AcademicYear;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.AcademicYearRepository;
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
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Annees scolaires.
 *
 * <p>Service court, mais le drapeau {@code isCurrent} est le <b>point d'ancrage de tout le reste</b> :
 * c'est lui qui decide sur quelle annee un nouvel eleve, une nouvelle note ou un nouvel emploi du temps
 * est rattache (voir {@code resolveYear()} / {@code resolveYearFor()} ailleurs). Deux annees courantes,
 * ou aucune, faussent silencieusement tous les nouveaux enregistrements.
 *
 * <p><b>Trois invariants epingles :</b>
 * <ol>
 *   <li><b>Une seule annee courante a la fois</b> : {@code clearCurrentFlag()} est appele <b>avant</b>
 *       de lever le drapeau, dans {@code create}, dans {@code update} et dans {@code setCurrent}.</li>
 *   <li><b>L'asymetrie du triplet {@code isCurrent}</b> — c'est le piege principal :
 *       <ul>
 *         <li>{@code create} : {@code Boolean.TRUE.equals(...)} ⇒ une valeur <b>absente</b> ou
 *             {@code FALSE} laisse l'annee non courante. Pas de promotion par defaut.</li>
 *         <li>{@code update} : {@code TRUE} <b>et</b> l'annee ne l'est pas deja ⇒ bascule ;
 *             {@code FALSE} <b>explicite</b> ⇒ retrograde ; <b>absent</b> ⇒ <b>ne touche a rien</b>.
 *             Un {@code if (request.getIsCurrent() != null)} « simplifie » en
 *             {@code Boolean.TRUE.equals(...)} supprimerait la possibilite de <b>retrograder</b> une
 *             annee, sans aucune erreur de compilation.</li>
 *       </ul>
 *   </li>
 *   <li><b>Une annee courante n'est pas supprimable</b> : il faut d'abord en designer une autre, sinon
 *       l'etablissement se retrouverait sans annee de reference.</li>
 * </ol>
 *
 * <p>⚠️ {@code setCurrent} appelle {@code clearCurrentFlag()} <b>puis</b> lève le drapeau, sans verifier
 * si l'annee cible l'est deja : le repository de l'annee courante est donc celui de la cible elle-meme
 * quand elle est deja active, ce qui declenche un {@code save} redondant mais correct. Comportement
 * epingle tel quel.
 *
 * <p><b>Controles negatifs effectues</b> (copie de travail, jamais le depot) :
 * {@code clearCurrentFlag()} neutralise dans {@code create} =&gt; 1 echec ; la branche {@code FALSE}
 * de {@code update} neutralisee =&gt; 1 echec ; garde de suppression de l'annee active retiree
 * =&gt; 1 echec.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AcademicYearServiceTest {

    @Mock
    private AcademicYearRepository academicYearRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private AcademicYearService academicYearService;

    @Mock
    private HttpServletRequest httpRequest;

    // ------------------------------------------------------------------ helpers

    private AcademicYear annee(Long id, String label, boolean courante) {
        return AcademicYear.builder()
                .id(id).label(label)
                .startDate(LocalDate.of(2025, 10, 1))
                .endDate(LocalDate.of(2026, 7, 31))
                .current(courante)
                .build();
    }

    private AcademicYearRequest demande(String label, Boolean isCurrent) {
        return AcademicYearRequest.builder()
                .label(label)
                .startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2027, 7, 31))
                .isCurrent(isCurrent)
                .build();
    }

    // ================================================================== lecture

    @Test
    @DisplayName("findAll trie par date de debut DECROISSANTE")
    void findAllTrieParDateDescendante() {
        when(academicYearRepository.findAll(Sort.by(Sort.Direction.DESC, "startDate")))
                .thenReturn(List.of(annee(2L, "2026-2027", true), annee(1L, "2025-2026", false)));

        List<AcademicYear> annees = academicYearService.findAll();

        assertThat(annees).hasSize(2);
        // L'annee la plus recente doit arriver en tete : c'est celle que l'UI selectionne par defaut.
        verify(academicYearRepository).findAll(Sort.by(Sort.Direction.DESC, "startDate"));
    }

    @Test
    @DisplayName("getById renvoie l'annee")
    void getByIdRenvoieLAnnee() {
        when(academicYearRepository.findById(1L)).thenReturn(Optional.of(annee(1L, "2025-2026", false)));

        assertThat(academicYearService.getById(1L).getLabel()).isEqualTo("2025-2026");
    }

    @Test
    @DisplayName("getById sur une annee inconnue nomme la ressource")
    void getByIdSurUneAnneeInconnue() {
        when(academicYearRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> academicYearService.getById(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Année scolaire")
                .hasMessageContaining("404");
    }

    // ================================================================== creation

    @Test
    @DisplayName("create refuse un libelle deja utilise")
    void createRefuseUnLibelleDejaUtilise() {
        when(academicYearRepository.existsByLabel("2026-2027")).thenReturn(true);

        assertThatThrownBy(() -> academicYearService.create(demande("2026-2027", false), httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2026-2027");

        verify(academicYearRepository, never()).save(any(AcademicYear.class));
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("create sans drapeau cree une annee NON courante")
    void createSansDrapeauCreeUneAnneeNonCourante() {
        when(academicYearRepository.existsByLabel("2026-2027")).thenReturn(false);
        when(academicYearRepository.save(any(AcademicYear.class))).thenAnswer(inv -> inv.getArgument(0));

        AcademicYear creee = academicYearService.create(demande("2026-2027", null), httpRequest);

        assertThat(creee.isCurrent()).isFalse();
        // Aucune promotion silencieuse : rien a effacer puisqu'aucune annee courante n'est designee.
        verify(academicYearRepository, never()).findByCurrentTrue();
    }

    @Test
    @DisplayName("create avec FALSE explicite cree une annee NON courante")
    void createAvecFalseCreeUneAnneeNonCourante() {
        when(academicYearRepository.existsByLabel("2026-2027")).thenReturn(false);
        when(academicYearRepository.save(any(AcademicYear.class))).thenAnswer(inv -> inv.getArgument(0));

        AcademicYear creee = academicYearService.create(demande("2026-2027", false), httpRequest);

        assertThat(creee.isCurrent()).isFalse();
        verify(academicYearRepository, never()).findByCurrentTrue();
    }

    @Test
    @DisplayName("create avec TRUE efface le drapeau de l'annee precedente AVANT de sauvegarder")
    void createAvecTrueEffaceLeDrapeauPrecedent() {
        when(academicYearRepository.existsByLabel("2026-2027")).thenReturn(false);
        AcademicYear precedente = annee(1L, "2025-2026", true);
        when(academicYearRepository.findByCurrentTrue()).thenReturn(Optional.of(precedente));
        when(academicYearRepository.save(any(AcademicYear.class))).thenAnswer(inv -> inv.getArgument(0));

        AcademicYear creee = academicYearService.create(demande("2026-2027", true), httpRequest);

        assertThat(creee.isCurrent()).isTrue();
        // L'ancienne annee doit être retrogradee et persistee : sans cet effacement, la base
        // contiendrait DEUX annees courantes et resolveYearFor() deviendrait non deterministe.
        assertThat(precedente.isCurrent()).isFalse();
        verify(academicYearRepository).save(precedente);
    }

    @Test
    @DisplayName("create avec TRUE quand aucune annee n'est courante fonctionne")
    void createAvecTrueSansAnneeCouranteExistante() {
        when(academicYearRepository.existsByLabel("2026-2027")).thenReturn(false);
        when(academicYearRepository.findByCurrentTrue()).thenReturn(Optional.empty());
        when(academicYearRepository.save(any(AcademicYear.class))).thenAnswer(inv -> inv.getArgument(0));

        AcademicYear creee = academicYearService.create(demande("2026-2027", true), httpRequest);

        assertThat(creee.isCurrent()).isTrue();
        // Une seule sauvegarde : celle de la nouvelle annee. L'absence d'ancienne n'est pas une erreur.
        verify(academicYearRepository).save(creee);
    }

    @Test
    @DisplayName("create AVEC drapeau efface l'ancienne AVANT d'enregistrer la nouvelle")
    void createEffaceAvantDeSauvegarder() {
        when(academicYearRepository.existsByLabel("2026-2027")).thenReturn(false);
        AcademicYear precedente = annee(1L, "2025-2026", true);
        when(academicYearRepository.findByCurrentTrue()).thenReturn(Optional.of(precedente));
        when(academicYearRepository.save(any(AcademicYear.class))).thenAnswer(inv -> inv.getArgument(0));

        academicYearService.create(demande("2026-2027", true), httpRequest);

        // Ordre exige par la contrainte « une seule courante » : enregistrer la nouvelle d'abord
        // violerait l'unicite. InOrder est ici la seule assertion qui distingue les deux ordres.
        org.mockito.InOrder ordre = org.mockito.Mockito.inOrder(academicYearRepository);
        ordre.verify(academicYearRepository).save(precedente);
        ordre.verify(academicYearRepository).save(any(AcademicYear.class));
    }

    @Test
    @DisplayName("create journalise le libelle")
    void createJournalise() {
        when(academicYearRepository.existsByLabel("2026-2027")).thenReturn(false);
        when(academicYearRepository.save(any(AcademicYear.class))).thenAnswer(inv -> {
            AcademicYear y = inv.getArgument(0);
            y.setId(12L);
            return y;
        });

        academicYearService.create(demande("2026-2027", false), httpRequest);

        verify(auditService).log(eq("CREATE"), eq("AcademicYear"), eq(12L),
                contains("2026-2027"), eq(httpRequest));
    }

    // ================================================================== modification

    @Test
    @DisplayName("update accepte de CONSERVER son propre libelle")
    void updateAccepteSonPropreLibelle() {
        AcademicYear existante = annee(1L, "2026-2027", false);
        when(academicYearRepository.findById(1L)).thenReturn(Optional.of(existante));
        when(academicYearRepository.save(any(AcademicYear.class))).thenAnswer(inv -> inv.getArgument(0));

        academicYearService.update(1L, demande("2026-2027", null), httpRequest);

        // Le libelle appartient deja a CETTE annee : aucune recherche de conflit n'est lancee.
        verify(academicYearRepository, never()).existsByLabel(any());
        assertThat(existante.getStartDate()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    @DisplayName("update refuse le libelle d'une AUTRE annee")
    void updateRefuseLeLibelleDUneAutreAnnee() {
        AcademicYear existante = annee(1L, "2025-2026", false);
        when(academicYearRepository.findById(1L)).thenReturn(Optional.of(existante));
        when(academicYearRepository.existsByLabel("2026-2027")).thenReturn(true);

        assertThatThrownBy(() -> academicYearService.update(1L, demande("2026-2027", null), httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2026-2027");

        verify(academicYearRepository, never()).save(any(AcademicYear.class));
    }

    @Test
    @DisplayName("update avec drapeau ABSENT ne touche pas au statut courant")
    void updateAvecDrapeauAbsentNeTouchePasAuStatut() {
        AcademicYear existante = annee(1L, "2025-2026", true);
        when(academicYearRepository.findById(1L)).thenReturn(Optional.of(existante));
        when(academicYearRepository.save(any(AcademicYear.class))).thenAnswer(inv -> inv.getArgument(0));

        academicYearService.update(1L, demande("2025-2026", null), httpRequest);

        // Reste courante : une modification de dates ne doit pas retrograder l'annee active.
        assertThat(existante.isCurrent()).isTrue();
        verify(academicYearRepository, never()).findByCurrentTrue();
    }

    @Test
    @DisplayName("update avec FALSE EXPLICITE retrograde une annee courante")
    void updateAvecFalseExpliciteRetrograde() {
        AcademicYear existante = annee(1L, "2025-2026", true);
        when(academicYearRepository.findById(1L)).thenReturn(Optional.of(existante));
        when(academicYearRepository.save(any(AcademicYear.class))).thenAnswer(inv -> inv.getArgument(0));

        academicYearService.update(1L, demande("2025-2026", false), httpRequest);

        assertThat(existante.isCurrent()).isFalse();
        // C'est LA branche qu'un `if (isCurrent != null) setCurrent(Boolean.TRUE.equals(...))`
        // supprimerait : retrograder deviendrait impossible sans erreur de compilation.
        verify(academicYearRepository, never()).findByCurrentTrue();
    }

    @Test
    @DisplayName("update avec TRUE Promeut une annee non courante et efface la precedente")
    void updateAvecTruePromeut() {
        AcademicYear existante = annee(1L, "2026-2027", false);
        AcademicYear precedente = annee(2L, "2025-2026", true);
        when(academicYearRepository.findById(1L)).thenReturn(Optional.of(existante));
        when(academicYearRepository.findByCurrentTrue()).thenReturn(Optional.of(precedente));
        when(academicYearRepository.save(any(AcademicYear.class))).thenAnswer(inv -> inv.getArgument(0));

        academicYearService.update(1L, demande("2026-2027", true), httpRequest);

        assertThat(existante.isCurrent()).isTrue();
        assertThat(precedente.isCurrent()).isFalse();
    }

    @Test
    @DisplayName("update avec TRUE sur une annee DEJA courante n'efface rien")
    void updateAvecTrueSurUneAnneeDejaCourante() {
        AcademicYear existante = annee(1L, "2025-2026", true);
        when(academicYearRepository.findById(1L)).thenReturn(Optional.of(existante));
        when(academicYearRepository.save(any(AcademicYear.class))).thenAnswer(inv -> inv.getArgument(0));

        academicYearService.update(1L, demande("2025-2026", true), httpRequest);

        assertThat(existante.isCurrent()).isTrue();
        // Deja courante : `clearCurrentFlag()` serait un aller-retour inutile, et l'aurait
        // desactivee une fraction de seconde. La garde `&& !year.isCurrent()` l'evite.
        verify(academicYearRepository, never()).findByCurrentTrue();
    }

    @Test
    @DisplayName("update sur une annee inconnue leve une exception")
    void updateSurUneAnneeInconnue() {
        when(academicYearRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> academicYearService.update(404L, demande("X", null), httpRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("update journalise le libelle")
    void updateJournalise() {
        AcademicYear existante = annee(1L, "2025-2026", false);
        when(academicYearRepository.findById(1L)).thenReturn(Optional.of(existante));
        when(academicYearRepository.findByCurrentTrue()).thenReturn(Optional.empty());
        when(academicYearRepository.save(any(AcademicYear.class))).thenAnswer(inv -> inv.getArgument(0));

        academicYearService.update(1L, demande("2025-2026", true), httpRequest);

        verify(auditService).log(eq("UPDATE"), eq("AcademicYear"), eq(1L),
                contains("2025-2026"), eq(httpRequest));
    }

    // ================================================================== designation de l'annee courante

    @Test
    @DisplayName("setCurrent efface l'ancienne courante puis leve le drapeau")
    void setCurrentEffacePuisLeve() {
        AcademicYear cible = annee(2L, "2026-2027", false);
        AcademicYear precedente = annee(1L, "2025-2026", true);
        when(academicYearRepository.findById(2L)).thenReturn(Optional.of(cible));
        when(academicYearRepository.findByCurrentTrue()).thenReturn(Optional.of(precedente));
        when(academicYearRepository.save(any(AcademicYear.class))).thenAnswer(inv -> inv.getArgument(0));

        AcademicYear resultat = academicYearService.setCurrent(2L, httpRequest);

        assertThat(resultat.isCurrent()).isTrue();
        assertThat(precedente.isCurrent()).isFalse();
        verify(auditService).log(eq("SET_CURRENT"), eq("AcademicYear"), eq(2L),
                contains("2026-2027"), eq(httpRequest));
    }

    @Test
    @DisplayName("setCurrent fonctionne quand aucune annee n'est encore courante")
    void setCurrentSansCouranteExistante() {
        AcademicYear cible = annee(1L, "2026-2027", false);
        when(academicYearRepository.findById(1L)).thenReturn(Optional.of(cible));
        when(academicYearRepository.findByCurrentTrue()).thenReturn(Optional.empty());
        when(academicYearRepository.save(any(AcademicYear.class))).thenAnswer(inv -> inv.getArgument(0));

        academicYearService.setCurrent(1L, httpRequest);

        assertThat(cible.isCurrent()).isTrue();
    }

    @Test
    @DisplayName("setCurrent sur une annee inconnue leve une exception sans rien effacer")
    void setCurrentSurUneAnneeInconnue() {
        when(academicYearRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> academicYearService.setCurrent(404L, httpRequest))
                .isInstanceOf(ResourceNotFoundException.class);

        // L'effacement ne doit pas avoir lieu : sinon l'etablissement perdrait son annee active
        // a cause d'un identifiant errone.
        verify(academicYearRepository, never()).findByCurrentTrue();
        verify(academicYearRepository, never()).save(any(AcademicYear.class));
    }

    // ================================================================== suppression

    @Test
    @DisplayName("delete refuse de supprimer l'annee courante")
    void deleteRefuseLAnneeCourante() {
        AcademicYear courante = annee(1L, "2025-2026", true);
        when(academicYearRepository.findById(1L)).thenReturn(Optional.of(courante));

        assertThatThrownBy(() -> academicYearService.delete(1L, httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("active");

        verify(academicYearRepository, never()).delete(any(AcademicYear.class));
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("delete supprime une annee non courante et journalise")
    void deleteSupprimeUneAnneeNonCourante() {
        AcademicYear ancienne = annee(1L, "2024-2025", false);
        when(academicYearRepository.findById(1L)).thenReturn(Optional.of(ancienne));

        academicYearService.delete(1L, httpRequest);

        verify(academicYearRepository).delete(ancienne);
        verify(auditService).log(eq("DELETE"), eq("AcademicYear"), eq(1L),
                contains("2024-2025"), eq(httpRequest));
    }

    @Test
    @DisplayName("delete sur une annee inconnue leve une exception")
    void deleteSurUneAnneeInconnue() {
        when(academicYearRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> academicYearService.delete(404L, httpRequest))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(academicYearRepository, never()).delete(any(AcademicYear.class));
    }

    // ================================================================== coherence globale

    @Test
    @DisplayName("apres clearCurrentFlag, l'ancienne courante est bien persistee")
    void ancienneCouranteEstPersistee() {
        when(academicYearRepository.existsByLabel("2026-2027")).thenReturn(false);
        AcademicYear precedente = annee(1L, "2025-2026", true);
        when(academicYearRepository.findByCurrentTrue()).thenReturn(Optional.of(precedente));
        when(academicYearRepository.save(any(AcademicYear.class))).thenAnswer(inv -> inv.getArgument(0));

        academicYearService.create(demande("2026-2027", true), httpRequest);

        // Une mutation sans save ne serait jamais persistee : l'entite serait un simple objet
        // detache et la base garderait deux annees courantes.
        ArgumentCaptor<AcademicYear> captor = ArgumentCaptor.forClass(AcademicYear.class);
        verify(academicYearRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
                .anyMatch(y -> y == precedente && !y.isCurrent());
    }
}
