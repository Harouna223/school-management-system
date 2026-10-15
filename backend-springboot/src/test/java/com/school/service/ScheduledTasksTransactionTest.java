package com.school.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garde-fou : toute tache planifiee ({@code @Scheduled}) doit porter
 * {@code @Transactional} <b>sur elle-meme</b>.
 *
 * <p><b>Pourquoi cette regle.</b> Le planificateur de Spring invoque la methode a
 * travers le proxy transactionnel : c'est le seul chemin qui ouvre une transaction.
 * Des qu'une tache planifiee delegue a une methode interne par auto-invocation
 * ({@code this.maMethode()}), le proxy est contourne et l'annotation posee sur la
 * methode appelee devient <em>inerte</em>. Trois taches nocturnes sur quatre
 * etaient cassees de cette facon, chacune differemment :</p>
 * <ul>
 *   <li>{@code AuthService} — aucune transaction : la requete {@code @Modifying}
 *       levait {@code TransactionRequiredException} ;</li>
 *   <li>{@code LibraryService} / {@code HrService} — la classe etant annotee
 *       {@code @Transactional(readOnly = true)}, la tache tournait en lecture
 *       seule : soit les changements n'etaient jamais flushes (silencieux), soit
 *       l'ecriture du journal d'audit echouait (« Connection is read-only »).</li>
 * </ul>
 *
 * <p>Le comportement est verifie sur une vraie base par {@code ScheduledTasksIT}.
 * Ce test-ci, lui, est instantane et attrape le probleme des l'ajout d'une
 * nouvelle tache. Si une tache planifiee ne doit legitimement rien ecrire en base,
 * il faut l'assumer explicitement ici plutot que de laisser passer le cas.</p>
 */
class ScheduledTasksTransactionTest {

    @Test
    @DisplayName("chaque methode @Scheduled porte un @Transactional en ecriture")
    void touteTachePlanifieeEstTransactionnelleEnEcriture() throws Exception {
        List<String> problemes = new ArrayList<>();
        int tachesVues = 0;

        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));

        for (BeanDefinition definition : scanner.findCandidateComponents("com.school")) {
            Class<?> type = Class.forName(definition.getBeanClassName());
            for (Method method : type.getDeclaredMethods()) {
                if (method.getAnnotation(Scheduled.class) == null) {
                    continue;
                }
                tachesVues++;
                Transactional transactional = method.getAnnotation(Transactional.class);
                if (transactional == null) {
                    problemes.add(type.getSimpleName() + "#" + method.getName()
                            + " : aucun @Transactional sur le point d'entree "
                            + "(l'auto-invocation rendrait celui de la methode appelee inerte)");
                } else if (transactional.readOnly()) {
                    problemes.add(type.getSimpleName() + "#" + method.getName()
                            + " : @Transactional(readOnly = true) — les ecritures seront perdues "
                            + "ou refusees (Connection is read-only)");
                }
            }
        }

        // Controle de sanite : si le scan ne trouve plus rien, le test ne prouve rien.
        assertThat(tachesVues)
                .as("taches planifiees detectees par le scan")
                .isGreaterThanOrEqualTo(4);
        assertThat(problemes)
                .as("taches planifiees sans transaction en ecriture : %s", problemes)
                .isEmpty();
    }
}
