package com.school.service;

import com.school.config.AppProperties;
import com.school.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Tests du stockage de fichiers.
 *
 * <p>Pourquoi ce fichier existe : `store` accepte un fichier fourni par l'utilisateur et
 * l'écrit sur le disque. Il applique une LISTE BLANCHE (MIME), valide le contenu réel par
 * les octets magiques, IMPOSE l'extension d'après le contenu, et génère lui-même le nom de
 * fichier. Aucun de ces points n'était testé : c'est exactement le profil de
 * `BackupService`, où l'absence de test avait laissé passer une régression complète.</p>
 *
 * <p>Le point le plus important n'est pas « un PNG passe » mais « un fichier dont le nom
 * annonce .php est écrit en .png », et « un sous-répertoire ne peut pas faire sortir
 * l'écriture du répertoire d'upload ».</p>
 */
class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.setUploadDir(tempDir.toString());
        service = new FileStorageService(properties);
    }

    // ------------------------------------------------------------------
    // Cas sans écriture
    // ------------------------------------------------------------------

    @Test
    @DisplayName("un fichier absent est ignoré (null, pas d'exception)")
    void fichierAbsentRetourneNull() {
        assertThat(service.store(null, "students")).isNull();
    }

    @Test
    @DisplayName("un fichier vide est ignoré (null, pas d'exception)")
    void fichierVideRetourneNull() {
        MockMultipartFile vide = new MockMultipartFile("file", "vide.png", "image/png", new byte[0]);

        assertThat(service.store(vide, "students")).isNull();
    }

    // ------------------------------------------------------------------
    // Liste blanche MIME (premier filtre, fourni par le client)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("un type MIME hors liste blanche est refusé, même avec un contenu valide")
    void typeMimeNonAutoriseRefuse() {
        MockMultipartFile pdf = new MockMultipartFile("file", "doc.pdf", "application/pdf", png());

        assertThatThrownBy(() -> service.store(pdf, "students"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("non autorisé");
    }

    // ------------------------------------------------------------------
    // Octets magiques : c'est le CONTENU qui décide, pas le nom ni le MIME
    // ------------------------------------------------------------------

    @Test
    @DisplayName("un contenu non-image est refusé même si le MIME annoncé est autorisé")
    void contenuNonImageRefuse() {
        // Le client peut mentir sur le Content-Type : ici un script PHP annoncé en image/png.
        MockMultipartFile faux = new MockMultipartFile("file", "charge.png", "image/png",
                "<?php system($_GET['c']); ?>".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.store(faux, "students"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("format image valide");
    }

    @Test
    @DisplayName("un fichier de moins de 4 octets est refusé")
    void fichierTropCourtRefuse() {
        MockMultipartFile court = new MockMultipartFile("file", "court.png", "image/png",
                new byte[]{0x01, 0x02});

        assertThatThrownBy(() -> service.store(court, "students"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("trop court");
    }

    @Test
    @DisplayName("un RIFF qui n'est pas un WebP (WAV/AVI) est refusé")
    void riffNonWebpRefuse() {
        // « RIFF » est aussi l'en-tête des WAV : sans vérifier « WEBP » à l'offset 8,
        // n'importe quel RIFF passerait pour une image.
        MockMultipartFile wav = new MockMultipartFile("file", "son.webp", "image/webp", wav());

        assertThatThrownBy(() -> service.store(wav, "students"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("format image valide");
    }

    // ------------------------------------------------------------------
    // Formats acceptés : l'extension est IMPOSÉE par le contenu
    // ------------------------------------------------------------------

    static Stream<Arguments> formatsValides() {
        return Stream.of(
                arguments("image/jpeg", "jpg", fichier("ffd8ffe0", "contenu-jpeg")),
                arguments("image/jpeg", "jpg", fichier("ffd8ffdb", "contenu-jpeg")),
                arguments("image/jpeg", "jpg", fichier("ffd8ffee", "contenu-jpeg")),
                arguments("image/png", "png", fichier("89504e47", "contenu-png")),
                arguments("image/gif", "gif", fichier("47494638", "contenu-gif")),
                arguments("image/webp", "webp", webp()));
    }

    @ParameterizedTest(name = "{0} est stocké en .{1}")
    @MethodSource("formatsValides")
    @DisplayName("chaque format accepté est stocké avec l'extension déduite du contenu")
    void formatValideStocke(String mime, String extension, byte[] contenu) throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "original.bin", mime, contenu);

        String url = service.store(file, "students");

        assertThat(url).matches("/uploads/students/[0-9a-f]{32}\\." + extension);
        Path ecrit = tempDir.resolve("students").resolve(nomDeFichier(url));
        assertThat(ecrit).exists();
        assertThat(Files.readAllBytes(ecrit)).isEqualTo(contenu);
    }

    @Test
    @DisplayName("l'extension vient du contenu, pas du nom fourni (.php → .png)")
    void extensionVientDuContenu() {
        MockMultipartFile file = new MockMultipartFile("file", "charge.php", "image/png", png());

        String url = service.store(file, "students");

        assertThat(url).endsWith(".png");
        assertThat(url).doesNotContain(".php");
    }

    @Test
    @DisplayName("le nom fourni ne sert jamais de chemin : le nom stocké est un UUID")
    void nomFourniNeSertPasDeChemin() {
        MockMultipartFile file = new MockMultipartFile("file",
                "../../../etc/passwd.png", "image/png", png());

        String url = service.store(file, "students");

        assertThat(url).matches("/uploads/students/[0-9a-f]{32}\\.png");
        assertThat(Files.exists(tempDir.resolve("students").resolve(nomDeFichier(url)))).isTrue();
    }

    // ------------------------------------------------------------------
    // Répertoire de destination : l'écriture ne doit pas sortir du dossier d'upload
    // ------------------------------------------------------------------

    @Test
    @DisplayName("un sous-répertoire qui remonte l'arborescence est refusé")
    void sousRepertoireHorsUploadRefuse() {
        // Nom unique pour ne pas confondre avec un reliquat d'un exécution précédente.
        String evasion = "evade-" + UUID.randomUUID();
        Path horsUpload = tempDir.getParent().resolve(evasion);
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", png());

        assertThatThrownBy(() -> service.store(file, "../" + evasion))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalide");

        assertThat(horsUpload).doesNotExist();
    }

    @Test
    @DisplayName("un sous-répertoire absolu est refusé")
    void sousRepertoireAbsoluRefuse() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", png());

        assertThatThrownBy(() -> service.store(file, tempDir.getParent().toAbsolutePath().toString()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalide");
    }

    @Test
    @DisplayName("un sous-répertoire vide ou absent est refusé")
    void sousRepertoireVideRefuse() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", png());

        assertThatThrownBy(() -> service.store(file, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalide");
        assertThatThrownBy(() -> service.store(file, "  "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalide");
    }

    @Test
    @DisplayName("un sous-répertoire imbriqué légitime reste accepté")
    void sousRepertoireImbriqueAccepte() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", png());

        String url = service.store(file, "students/2026");

        assertThat(url).startsWith("/uploads/students/2026/");
        assertThat(tempDir.resolve("students").resolve("2026")).isDirectory();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** En-tête binaire suivi d'un contenu ASCII lisible. */
    private static byte[] fichier(String hex, String contenu) {
        byte[] head = fromHex(hex);
        byte[] reste = contenu.getBytes(StandardCharsets.US_ASCII);
        byte[] out = new byte[head.length + reste.length];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(reste, 0, out, head.length, reste.length);
        return out;
    }

    private static byte[] png() {
        return fichier("89504e47", "contenu-png");
    }

    /** « RIFF » + taille + « WEBP » + début de bloc « VP8 ». */
    private static byte[] webp() {
        return fromHex("52494646" + "1a000000" + "57454250" + "56503820");
    }

    /** Même en-tête RIFF, mais suivi de « WAVE » : ce n'est pas une image. */
    private static byte[] wav() {
        return fromHex("52494646" + "1a000000" + "57415645" + "666d7420");
    }

    private static byte[] fromHex(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static String nomDeFichier(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }
}
