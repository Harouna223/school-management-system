package com.school.service;

import com.school.entity.NumberSequence;
import com.school.repository.NumberSequenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests du compteur de numerotation (factures, recus).
 * <p>
 * Ce service a remplace des {@code count() + 1} qui reattribuaient des numeros deja
 * emis. Deux invariants sont donc critiques et <b>silencieux</b> s'ils regressent :
 * <ul>
 *   <li>la lecture doit rester <b>verrouillee</b> ({@code findWithLockingByPrefix}) —
 *       une « simplification » vers {@code findById} perdrait le verrou et
 *       reintroduirait des doublons sous concurrence ;</li>
 *   <li>la propagation doit rester {@link Propagation#REQUIRES_NEW} — sinon le numero
 *       est annule avec l'appelant et sera reattribue plus tard.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class NumberSequenceServiceTest {

    @Mock
    private NumberSequenceRepository sequenceRepository;

    private NumberSequenceService service;

    @BeforeEach
    void setUp() {
        service = new NumberSequenceService(sequenceRepository);
    }

    /** Depot en memoire : conserve la ligne entre les appels, comme la base. */
    private Map<String, NumberSequence> depotEnMemoire() {
        Map<String, NumberSequence> store = new HashMap<>();
        when(sequenceRepository.findWithLockingByPrefix(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(store.get(inv.getArgument(0, String.class))));
        when(sequenceRepository.save(any(NumberSequence.class)))
                .thenAnswer(inv -> {
                    NumberSequence s = inv.getArgument(0);
                    store.put(s.getPrefix(), s);
                    return s;
                });
        return store;
    }

    // ---------------------------------------------------------------- valeurs

    @Test
    void premierAppelSurUnPrefixeInconnu_renvoie1() {
        depotEnMemoire();

        assertThat(service.next("FAC-")).isEqualTo(1);
    }

    @Test
    void compteurExistant_renvoieLaValeurSuivante() {
        NumberSequence existant = NumberSequence.builder().prefix("RCP-").lastValue(41).build();
        when(sequenceRepository.findWithLockingByPrefix("RCP-")).thenReturn(Optional.of(existant));
        when(sequenceRepository.save(any(NumberSequence.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.next("RCP-")).isEqualTo(42);
    }

    @Test
    void lesValeursSontStrictementCroissantes() {
        depotEnMemoire();

        assertThat(service.next("FAC-")).isEqualTo(1);
        assertThat(service.next("FAC-")).isEqualTo(2);
        assertThat(service.next("FAC-")).isEqualTo(3);
    }

    @Test
    void lesPrefixesSontIndependants() {
        depotEnMemoire();

        assertThat(service.next("FAC-")).isEqualTo(1);
        assertThat(service.next("RCP-")).isEqualTo(1);
        assertThat(service.next("REC-ENS-")).isEqualTo(1);
        assertThat(service.next("FAC-")).isEqualTo(2);
    }

    @Test
    void laValeurIncrementeeEstPersistee() {
        NumberSequence existant = NumberSequence.builder().prefix("RCP-").lastValue(41).build();
        when(sequenceRepository.findWithLockingByPrefix("RCP-")).thenReturn(Optional.of(existant));
        when(sequenceRepository.save(any(NumberSequence.class))).thenAnswer(inv -> inv.getArgument(0));

        service.next("RCP-");

        ArgumentCaptor<NumberSequence> captor = ArgumentCaptor.forClass(NumberSequence.class);
        verify(sequenceRepository).save(captor.capture());
        assertThat(captor.getValue().getLastValue()).isEqualTo(42);
    }

    // ------------------------------------------------- invariants silencieux

    @Test
    void utiliseLaLectureVerrouillee_jamaisUneLectureSimple() {
        NumberSequence existant = NumberSequence.builder().prefix("FAC-").lastValue(1).build();
        when(sequenceRepository.findWithLockingByPrefix("FAC-")).thenReturn(Optional.of(existant));
        when(sequenceRepository.save(any(NumberSequence.class))).thenAnswer(inv -> inv.getArgument(0));

        service.next("FAC-");

        verify(sequenceRepository).findWithLockingByPrefix("FAC-");
        // findById est herite de JpaRepository : il ne prend AUCUN verrou.
        // L'utiliser perdrait la serialisation et produirait deux fois le meme numero.
        verify(sequenceRepository, never()).findById(anyString());
    }

    @Test
    void leNumeroSurvitAEchecDeLAppelant_propagationRequiresNew() throws Exception {
        Method next = NumberSequenceService.class.getMethod("next", String.class);

        Transactional tx = next.getAnnotation(Transactional.class);
        assertThat(tx)
                .as("@Transactional a disparu de next() : le numero serait annule avec l'appelant")
                .isNotNull();
        assertThat(tx.propagation())
                .as("REQUIRES_NEW est indispensable : le numero doit rester attribue meme si l'appelant echoue")
                .isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    void unPrefixeVide_resteUneCleValide() {
        // Le prefixe est la cle primaire : une chaine vide doit rester une cle
        // valide et ne pas provoquer de NullPointerException.
        depotEnMemoire();

        assertThat(service.next("")).isEqualTo(1);
        assertThat(service.next("")).isEqualTo(2);
    }
}
