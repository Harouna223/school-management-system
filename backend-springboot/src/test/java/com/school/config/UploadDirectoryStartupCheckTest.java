package com.school.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests du contrôle d'écriture du répertoire des fichiers pédagogiques.
 *
 * <p>Le point important n'est pas seulement qu'il détecte le problème, mais
 * qu'il <strong>ne bloque pas le démarrage</strong> : un volume non
 * inscriptible empêche d'ajouter une photo, pas de consulter les notes.</p>
 */
class UploadDirectoryStartupCheckTest {

    private static UploadDirectoryStartupCheck checkFor(Path directory) {
        AppProperties properties = new AppProperties();
        properties.setUploadDir(directory.toString());
        return new UploadDirectoryStartupCheck(properties);
    }

    @Test
    @DisplayName("un répertoire inscriptible est accepté et ne laisse aucune sonde derrière lui")
    void writableDirectoryLeavesNoProbe(@TempDir Path tempDir) throws IOException {
        assertThatCode(() -> checkFor(tempDir).run()).doesNotThrowAnyException();

        try (Stream<Path> restants = Files.list(tempDir)) {
            assertThat(restants).as("la sonde d'écriture doit être supprimée").isEmpty();
        }
    }

    @Test
    @DisplayName("un répertoire absent est créé au démarrage")
    void missingDirectoryIsCreated(@TempDir Path tempDir) {
        Path cible = tempDir.resolve("students");

        assertThatCode(() -> checkFor(cible).run()).doesNotThrowAnyException();
        assertThat(cible).isDirectory();
    }

    @Test
    @DisplayName("un répertoire inscriptible impossible à atteindre est signalé sans faire échouer le démarrage")
    void unreachableDirectoryIsReportedButDoesNotFailStartup(@TempDir Path tempDir) throws IOException {
        // Un fichier ordinaire là où un répertoire est attendu : la création
        // échoue de façon certaine, quel que soit le système (contrairement à un
        // chmod, sans effet sur Windows).
        Path fichier = Files.createFile(tempDir.resolve("pas-un-repertoire"));
        Path cible = fichier.resolve("uploads");

        assertThatCode(() -> checkFor(cible).run())
                .as("l'application doit rester utilisable : seul le téléversement est cassé")
                .doesNotThrowAnyException();
    }
}
