package com.school.service;

import com.school.config.AppProperties;
import com.school.enums.CourseResourceType;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests du stockage des fichiers pédagogiques.
 *
 * <p>Ce service reçoit des fichiers fournis par l'utilisateur et écrit sur le
 * disque : la signature binaire fait foi, l'extension et le MIME annoncés sont
 * CONTRÔLÉS, le nom physique est généré côté serveur, et aucun chemin ne peut
 * sortir du répertoire d'uploads.</p>
 */
class CourseFileStorageServiceTest {

    @TempDir
    Path tempDir;

    private AppProperties properties;
    private CourseFileStorageService service;

    @BeforeEach
    void setUp() {
        properties = new AppProperties();
        properties.setUploadDir(tempDir.toString());
        service = new CourseFileStorageService(properties);
    }

    // ------------------------------------------------------------------
    // Fabriques de fichiers
    // ------------------------------------------------------------------

    private static byte[] pdfBytes(String marker) {
        return ("%PDF-1.7\n% " + marker + "\n%%EOF").getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] mp4Bytes() {
        byte[] bytes = new byte[64];
        // boîte « ftyp » à l'offset 4
        bytes[4] = 'f';
        bytes[5] = 't';
        bytes[6] = 'y';
        bytes[7] = 'p';
        bytes[8] = 'm';
        bytes[9] = 'p';
        bytes[10] = '4';
        bytes[11] = '2';
        return bytes;
    }

    private static byte[] webmBytes() {
        byte[] bytes = new byte[64];
        bytes[0] = 0x1A;
        bytes[1] = 0x45;
        bytes[2] = (byte) 0xDF;
        bytes[3] = (byte) 0xA3;
        byte[] doctype = "webm".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(doctype, 0, bytes, 20, doctype.length);
        return bytes;
    }

    private static byte[] pngBytes() {
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    }

    // ------------------------------------------------------------------
    // Formats acceptés
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Un PDF valide est stocké sous courses/<id>/ avec une extension imposée")
    void pdfValide_estStocke() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "chapitre-1.pdf", "application/pdf", pdfBytes("cours"));

        CourseFileStorageService.StoredFile stored =
                service.store(file, CourseResourceType.PDF, 42L);

        assertThat(stored.relativePath()).startsWith("courses/42/").endsWith(".pdf");
        assertThat(stored.mimeType()).isEqualTo("application/pdf");
        assertThat(stored.sizeBytes()).isEqualTo(file.getSize());
        assertThat(tempDir.resolve(stored.relativePath())).exists();
    }

    @Test
    @DisplayName("Une vidéo MP4 est acceptée et stockée en .mp4")
    void mp4Valide_estStocke() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "lecon.mp4", "video/mp4", mp4Bytes());

        CourseFileStorageService.StoredFile stored =
                service.store(file, CourseResourceType.VIDEO, 7L);

        assertThat(stored.relativePath()).endsWith(".mp4");
        assertThat(stored.mimeType()).isEqualTo("video/mp4");
    }

    @Test
    @DisplayName("Une vidéo WebM est acceptée (signature EBML + doctype webm)")
    void webmValide_estStocke() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "lecon.webm", "video/webm", webmBytes());

        CourseFileStorageService.StoredFile stored =
                service.store(file, CourseResourceType.VIDEO, 8L);

        assertThat(stored.relativePath()).endsWith(".webm");
        assertThat(stored.mimeType()).isEqualTo("video/webm");
    }

    // ------------------------------------------------------------------
    // Refus
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Une image n'est pas acceptée comme vidéo, malgré son type MIME annoncé")
    void imageAnnonceeCommeVideo_estRefusee() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.mp4", "video/mp4", pngBytes());

        assertThatThrownBy(() -> service.store(file, CourseResourceType.VIDEO, 1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Un PDF annoncé comme vidéo est refusé (incohérence de nature)")
    void pdfAnnonceCommeVideo_estRefuse() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cours.mp4", "video/mp4", pdfBytes("x"));

        assertThatThrownBy(() -> service.store(file, CourseResourceType.VIDEO, 1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Une extension qui ment sur le contenu est refusée")
    void extensionMensongere_estRefusee() {
        // Contenu réel : MP4 ; nom d'origine : .avi — le contenu est valide,
        // c'est l'EXTENSION qui ne correspond pas et doit être refusée.
        MockMultipartFile file = new MockMultipartFile(
                "file", "lecon.avi", "video/mp4", mp4Bytes());

        assertThatThrownBy(() -> service.store(file, CourseResourceType.VIDEO, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("extension");
    }

    @Test
    @DisplayName("Un type MIME incohérent avec la signature est refusé")
    void mimeIncoherent_estRefuse() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "lecon.mp4", "image/png", mp4Bytes());

        assertThatThrownBy(() -> service.store(file, CourseResourceType.VIDEO, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MIME");
    }

    @Test
    @DisplayName("Un fichier au-delà de la limite PDF est refusé avec sa taille réelle")
    void fichierTropVolumineux_estRefuse() {
        properties.getCourses().setMaxPdfMb(1);
        byte[] big = new byte[1024 * 1024 + 1];
        System.arraycopy(pdfBytes("gros"), 0, big, 0, pdfBytes("gros").length);
        MockMultipartFile file = new MockMultipartFile(
                "file", "gros.pdf", "application/pdf", big);

        assertThatThrownBy(() -> service.store(file, CourseResourceType.PDF, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("volumineux");
    }

    @Test
    @DisplayName("Un fichier vide est refusé")
    void fichierVide_estRefuse() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "vide.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> service.store(file, CourseResourceType.PDF, 1L))
                .isInstanceOf(BusinessException.class);
    }

    // ------------------------------------------------------------------
    // Chemin et suppression
    // ------------------------------------------------------------------

    @Test
    @DisplayName("resolve refuse une traversée de répertoire")
    void resolve_refuseTraversee() {
        assertThatThrownBy(() -> service.resolve("../../etc/passwd"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("resolve refuse un chemin absolu injecté en base")
    void resolve_refuseCheminAbsolu() {
        assertThatThrownBy(() -> service.resolve("C:/Windows/win.ini"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("delete supprime le fichier et laisse le répertoire racine intact")
    void delete_supprimeLeFichier() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cours.pdf", "application/pdf", pdfBytes("x"));
        CourseFileStorageService.StoredFile stored =
                service.store(file, CourseResourceType.PDF, 5L);
        Path onDisk = tempDir.resolve(stored.relativePath());
        assertThat(onDisk).exists();

        service.delete(stored.relativePath());

        assertThat(onDisk).doesNotExist();
        assertThat(Files.isDirectory(tempDir)).isTrue();
    }

    @Test
    @DisplayName("delete est sans effet sur un chemin inconnu (jamais d'exception)")
    void delete_cheminInconnu_neLevePas() {
        service.delete("courses/999/absent.pdf");
        service.delete(null);
    }

    @Test
    @DisplayName("Le nom d'origine est nettoyé de son chemin Windows")
    void nomOrigine_estNettoye() {
        assertThat(service.sanitizeOriginalFilename("C:\\Users\\x\\cours.pdf", "pdf"))
                .isEqualTo("cours.pdf");
        assertThat(service.sanitizeOriginalFilename("../../etc/passwd.pdf", "pdf"))
                .isEqualTo("passwd.pdf");
    }
}
