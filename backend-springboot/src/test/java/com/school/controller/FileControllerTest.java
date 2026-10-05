package com.school.controller;

import com.school.config.AppProperties;
import com.school.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests du service de fichiers téléversés (protection D9).
 *
 * <p>Un bug RÉEL a été découvert lors du déploiement : Spring transmet
 * « filePath » avec un « / » initial, or Path.resolve("/x") renvoie un chemin
 * ABSOLU et ignorait le dossier d'uploads — tous les fichiers légitimes
 * renvoyaient 404. Ces tests verrouillent ce comportement et l'anti-traversal.</p>
 */
class FileControllerTest {

    @TempDir
    Path uploadDir;

    private FileController controller;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.setUploadDir(uploadDir.toString());
        controller = new FileController(props);
    }

    @Test
    @DisplayName("sert un fichier dont le chemin commence par « / » (cas réel de Spring)")
    void servesFileWhenPathStartsWithSlash() throws IOException {
        Path photo = uploadDir.resolve("students/photo.png");
        Files.createDirectories(photo.getParent());
        Files.writeString(photo, "contenu");

        var response = controller.serve("/students/photo.png");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("accepte aussi un chemin relatif sans « / » initial")
    void servesFileWithRelativePath() throws IOException {
        Path photo = uploadDir.resolve("teachers/t.png");
        Files.createDirectories(photo.getParent());
        Files.writeString(photo, "x");

        assertThat(controller.serve("teachers/t.png").getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    @DisplayName("refuse la traversée de répertoire (« ../ »)")
    void rejectsPathTraversal() {
        assertThatThrownBy(() -> controller.serve("/../secret.txt"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("lève une exception pour un fichier absent ou un chemin vide")
    void throwsWhenFileMissing() {
        assertThatThrownBy(() -> controller.serve("/students/absent.png"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> controller.serve(null))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}