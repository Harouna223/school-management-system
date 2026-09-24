package com.school.service;

import com.school.config.AppProperties;
import com.school.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    /** En-tête « RIFF » : partagé par WebP, mais aussi par les WAV et les AVI. */
    private static final String RIFF_HEX = "52494646";

    private static final int MAGIC_BYTES = 4;

    private static final String FORMAT_INVALIDE =
            "Le fichier ne correspond pas à un format image valide (JPEG, PNG, WebP, GIF)";

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
            String hex = toHex(head);
            ext = MAGIC_EXT.get(hex);
            if (ext == null) {
                throw new BusinessException(FORMAT_INVALIDE);
            }
            // « RIFF » seul ne prouve pas un WebP : l'en-tête est partagé avec les WAV/AVI.
            // Un vrai WebP porte « WEBP » juste après la taille du bloc (offset 8).
            if (RIFF_HEX.equals(hex) && !contientWebp(in)) {
                throw new BusinessException(FORMAT_INVALIDE);
            }
        } catch (IOException e) {
            throw new BusinessException("Impossible de lire le fichier");
        }

        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;

        try {
            Path base = Paths.get(appProperties.getUploadDir()).toAbsolutePath().normalize();
            Path dir = resolveSousRepertoire(base, subDir);
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            file.getInputStream().transferTo(Files.newOutputStream(target));
            return "/uploads/" + subDir + "/" + filename;
        } catch (IOException ex) {
            throw new BusinessException("Impossible de stocker le fichier : " + ex.getMessage());
        }
    }

    /**
     * Résout {@code subDir} sous le répertoire d'upload et refuse tout ce qui en sort.
     *
     * <p>Aujourd'hui les trois appelants passent des constantes (« school », « students »,
     * « teachers »), donc rien d'exploitable — mais rien ne l'imposerait demain. Sans ce
     * contrôle, un {@code subDir} valant « ../../etc » (ou un chemin absolu) ferait écrire
     * le fichier n'importe où : {@code normalize()} seul ne protège pas, il faut vérifier
     * le préfixe après normalisation.</p>
     */
    private Path resolveSousRepertoire(Path base, String subDir) {
        if (subDir == null || subDir.isBlank()) {
            throw new BusinessException("Répertoire de destination invalide");
        }
        Path dir = base.resolve(subDir).normalize();
        // ⚠️ `resolve` renvoie le chemin absolu tel quel si `subDir` est absolu : le
        // `startsWith` couvre donc aussi ce cas, en plus des « .. ».
        if (!dir.startsWith(base)) {
            throw new BusinessException("Répertoire de destination invalide");
        }
        return dir;
    }

    /** Lit les octets 8 à 11 du flux (déjà positionné à l'offset 4) et teste « WEBP ». */
    private boolean contientWebp(InputStream in) throws IOException {
        in.skipNBytes(MAGIC_BYTES);
        byte[] tag = new byte[MAGIC_BYTES];
        return in.read(tag, 0, MAGIC_BYTES) == MAGIC_BYTES
                && "WEBP".equals(new String(tag, StandardCharsets.US_ASCII));
    }

    private String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
