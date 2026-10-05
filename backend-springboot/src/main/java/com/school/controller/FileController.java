package com.school.controller;

import com.school.config.AppProperties;
import com.school.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * Sert les fichiers téléversés (photos d'élèves/enseignants, logo) depuis le
 * dossier d'uploads — contrairement à l'ancien ResourceHandler public, ce
 * contrôleur exige une authentification (toute requête passe par le filtre JWT
 * via anyRequest().authenticated()).
 *
 * Anti-traversal : le chemin normalisé doit rester sous le dossier d'uploads.
 * Confidentialité : Cache-Control: private et X-Content-Type-Options: nosniff.
 */
@RestController
@RequestMapping("/uploads")
@RequiredArgsConstructor
public class FileController {

    private static final String MIME_TYPE = "X-Content-Type-Options";

    private final AppProperties appProperties;

    /**
     * Sert /uploads/...
     *
     * <p>Spring fournit « filePath » AVEC un « / » initial
     * (« /students/photo.png »). Or {@code Path.resolve("/x")} renvoie un chemin
     * ABSOLU et ignorerait la base : sans le retrait de ce « / », tout fichier
     * légitime était refusé par le contrôle anti-traversal (404).</p>
     */
    @GetMapping("/{*filePath}")
    public ResponseEntity<Resource> serve(@PathVariable("filePath") String filePath) {
        Path base = Paths.get(appProperties.getUploadDir()).toAbsolutePath().normalize();
        String relative = filePath != null && filePath.startsWith("/")
                ? filePath.substring(1) : filePath;
        Path file = base.resolve(relative != null ? relative : "").normalize();

        // Anti-traversal : le chemin résolu doit rester DANS le dossier d'uploads.
        if (!file.startsWith(base) || !Files.isRegularFile(file) || !Files.isReadable(file)) {
            throw new ResourceNotFoundException("Fichier introuvable");
        }

        return ResponseEntity.ok()
                .contentType(mediaTypeOf(file.getFileName().toString()))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .header(MIME_TYPE, "nosniff")
                .body(new FileSystemResource(file));
    }

    private MediaType mediaTypeOf(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (lower.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        // Type inconnu : octet-stream, jamais de MIME inféré par le navigateur.
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}