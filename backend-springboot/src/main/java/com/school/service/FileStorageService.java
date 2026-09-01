package com.school.service;

import com.school.config.AppProperties;
import com.school.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Stockage sécurisé des fichiers uploadés (photos, logo…).
 * Valide le contenu réel via les octets magiques (magic bytes)
 * et force l'extension en fonction du type détecté.
 */
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final Set<String> ALLOWED_MIME = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private static final Map<String, String> MAGIC_EXT = Map.of(
            "ffd8ffe0", "jpg",  "ffd8ffe1", "jpg",  "ffd8ffe2", "jpg",
            "ffd8ffdb", "jpg",  "ffd8ffee", "jpg",
            "89504e47", "png",
            "52494646", "webp",
            "47494638", "gif");

    private static final int MAGIC_BYTES = 4;

    private final AppProperties appProperties;

    public String store(MultipartFile file, String subDir) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (!ALLOWED_MIME.contains(file.getContentType())) {
            throw new BusinessException("Type de fichier non autorisé (images uniquement)");
        }

        String ext;
        try (InputStream in = file.getInputStream()) {
            byte[] head = new byte[MAGIC_BYTES];
            int read = in.read(head, 0, MAGIC_BYTES);
            if (read < MAGIC_BYTES) {
                throw new BusinessException("Fichier trop court ou illisible");
            }
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < MAGIC_BYTES; i++) {
                hex.append(String.format("%02x", head[i]));
            }
            ext = MAGIC_EXT.get(hex.toString());
            if (ext == null) {
                throw new BusinessException("Le fichier ne correspond pas à un format image valide (JPEG, PNG, WebP, GIF)");
            }
        } catch (IOException e) {
            throw new BusinessException("Impossible de lire le fichier");
        }

        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;

        try {
            Path dir = Paths.get(appProperties.getUploadDir(), subDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            file.getInputStream().transferTo(Files.newOutputStream(target));
            return "/uploads/" + subDir + "/" + filename;
        } catch (IOException ex) {
            throw new BusinessException("Impossible de stocker le fichier : " + ex.getMessage());
        }
    }
}