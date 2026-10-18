package com.school.service;

import com.school.config.AppProperties;
import com.school.enums.CourseResourceType;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Stockage des fichiers pédagogiques (vidéos et PDF).
 *
 * <p>Règles appliquées, dans cet ordre :</p>
 * <ol>
 *   <li>taille maximale par nature de fichier ({@code app.courses.max-*-mb}) ;</li>
 *   <li>signature réelle (octets magiques) — c'est elle qui décide du type,
 *       jamais l'extension ni le {@code Content-Type} annoncés ;</li>
 *   <li>cohérence entre la signature, la nature déclarée (VIDEO/PDF),
 *       l'extension du nom d'origine et le type MIME annoncé ;</li>
 *   <li>nom physique généré côté serveur (UUID), écrit sous
 *       {@code ${UPLOAD_DIR}/courses/<resourceId>/} avec contrôle
 *       anti-traversée après normalisation.</li>
 * </ol>
 *
 * <p>Aucune image n'est acceptée : ce service ne sert que le module cours.
 * Les photos passent par {@link FileStorageService}, qui a sa propre liste
 * blanche.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CourseFileStorageService {

    private static final String COURSES_DIR = "courses";
    private static final long BYTES_PER_MB = 1024L * 1024L;
    private static final int HEADER_BYTES = 64;

    private static final String EXT_PDF = "pdf";
    private static final String EXT_MP4 = "mp4";
    private static final String EXT_WEBM = "webm";

    private static final Set<String> VIDEO_EXTENSIONS = Set.of(EXT_MP4, EXT_WEBM);
    private static final Set<String> VIDEO_MIME = Set.of("video/mp4", "video/webm");
    private static final Set<String> PDF_MIME = Set.of("application/pdf", "application/x-pdf");

    /**
     * Types MIME « génériques » : ils n'apportent AUCUNE information sur le
     * contenu et sont fréquemment envoyés par des clients qui ne connaissent pas
     * l'extension (curl, certaines moulinettes, navigateurs sur poste dont le
     * registre n'associe pas .mp4). Les refuser bloquerait un fichier pourtant
     * valide : dans ce cas, c'est la signature binaire qui fait foi.
     */
    private static final Set<String> GENERIC_MIME = Set.of(
            "application/octet-stream",
            "binary/octet-stream",
            "application/binary",
            "application/unknown");

    private static final Map<String, String> MIME_BY_EXT = Map.of(
            EXT_PDF, "application/pdf",
            EXT_MP4, "video/mp4",
            EXT_WEBM, "video/webm");

    private final AppProperties appProperties;

    /** Résultat d'un téléversement accepté. */
    public record StoredFile(String relativePath,
                             String originalFilename,
                             long sizeBytes,
                             String mimeType,
                             String extension) {
    }

    /**
     * Valide puis écrit le fichier.
     *
     * @param file       fichier reçu du client
     * @param type       nature déclarée par l'appelant (VIDEO ou PDF)
     * @param resourceId identifiant de la ressource (sous-répertoire)
     * @return le chemin relatif à enregistrer en base et les métadonnées vérifiées
     */
    public StoredFile store(MultipartFile file, CourseResourceType type, Long resourceId) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new BusinessException("Fichier manquant ou vide");
        }
        long maxBytes = maxBytesFor(type);
        if (file.getSize() > maxBytes) {
            throw new BusinessException("Fichier trop volumineux : "
                    + humanSize(file.getSize()) + " pour une limite de " + humanSize(maxBytes)
                    + (type == CourseResourceType.VIDEO ? " (vidéo)" : " (PDF)"));
        }

        byte[] header = readHeader(file);
        String extension = detectExtension(header, type);
        assertDeclaredMimeMatches(file.getContentType(), extension, type);
        assertOriginalExtensionMatches(file.getOriginalFilename(), extension, type);

        String storageName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        String relativePath = COURSES_DIR + "/" + resourceId + "/" + storageName;

        Path base = baseDir();
        Path target = base.resolve(relativePath).normalize();
        // Défense en profondeur : `relativePath` est construit ici, donc sûr,
        // mais un futur appelant pourrait passer un chemin calculé autrement.
        if (!target.startsWith(base)) {
            throw new BusinessException("Répertoire de destination invalide");
        }
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new BusinessException("Impossible de stocker le fichier : " + ex.getMessage());
        }

        return new StoredFile(relativePath,
                sanitizeOriginalFilename(file.getOriginalFilename(), extension),
                file.getSize(),
                MIME_BY_EXT.get(extension),
                extension);
    }

    /**
     * Résout un chemin relatif stocké en base en chemin absolu vérifié.
     *
     * <p>Refuse tout ce qui sortirait du répertoire d'uploads, y compris un
     * chemin absolu injecté dans la colonne {@code storage_filename}.</p>
     */
    public Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new ResourceNotFoundException("Fichier introuvable");
        }
        Path base = baseDir();
        Path file = base.resolve(relativePath).normalize();
        if (!file.startsWith(base) || !Files.isRegularFile(file) || !Files.isReadable(file)) {
            throw new ResourceNotFoundException("Fichier introuvable");
        }
        return file;
    }

    /**
     * Supprime un fichier déjà stocké. Ne fait jamais échouer l'appelant : un
     * fichier orphelin est préférable à un enregistrement impossible à
     * supprimer. Le ménage peut être refait à la main.
     */
    public void delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Path base = baseDir();
            Path file = base.resolve(relativePath).normalize();
            if (!file.startsWith(base)) {
                log.warn("Suppression refusée hors du répertoire d'uploads : {}", relativePath);
                return;
            }
            Files.deleteIfExists(file);
            Path parent = file.getParent();
            if (parent != null && !parent.equals(base)) {
                try (var entries = Files.list(parent)) {
                    if (entries.findAny().isEmpty()) {
                        Files.deleteIfExists(parent);
                    }
                }
            }
        } catch (IOException ex) {
            log.warn("Impossible de supprimer le fichier {} : {}", relativePath, ex.getMessage());
        }
    }

    /** Répertoire racine absolu des uploads. */
    public Path baseDir() {
        return Paths.get(appProperties.getUploadDir()).toAbsolutePath().normalize();
    }

    public long maxBytesFor(CourseResourceType type) {
        int mb = type == CourseResourceType.VIDEO
                ? appProperties.getCourses().getMaxVideoMb()
                : appProperties.getCourses().getMaxPdfMb();
        return mb * BYTES_PER_MB;
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    private byte[] readHeader(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] header = new byte[HEADER_BYTES];
            int read = in.readNBytes(header, 0, HEADER_BYTES);
            if (read <= 0) {
                throw new BusinessException("Fichier illisible");
            }
            return java.util.Arrays.copyOf(header, read);
        } catch (IOException ex) {
            throw new BusinessException("Impossible de lire le fichier");
        }
    }

    /**
     * Détermine l'extension réelle à partir de la signature binaire.
     *
     * @throws BusinessException si le contenu n'est ni un PDF, ni un MP4, ni un
     *                           WebM — ou s'il ne correspond pas à {@code type}
     */
    private String detectExtension(byte[] header, CourseResourceType type) {
        String detected = signatureOf(header);
        if (detected == null) {
            throw new BusinessException(FORMAT_MESSAGE);
        }
        if (type == CourseResourceType.PDF && !EXT_PDF.equals(detected)) {
            throw new BusinessException("Ce fichier n'est pas un document PDF valide");
        }
        if (type == CourseResourceType.VIDEO && !VIDEO_EXTENSIONS.contains(detected)) {
            throw new BusinessException("Ce fichier n'est pas une vidéo MP4 ou WebM valide");
        }
        return detected;
    }

    private static final String FORMAT_MESSAGE =
            "Format non supporté : seuls les PDF, MP4 et WebM sont acceptés";

    private String signatureOf(byte[] header) {
        if (header.length >= 5
                && header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F'
                && header[4] == '-') {
            return EXT_PDF;
        }
        // MP4 : la boîte « ftyp » commence à l'octet 4.
        if (header.length >= 8
                && header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p') {
            return EXT_MP4;
        }
        // WebM : signature EBML (1A 45 DF A3) ET type de document « webm ».
        // Le seul en-tête EBML ne suffit pas : il est partagé avec les .mkv.
        if (header.length >= 4
                && (header[0] & 0xFF) == 0x1A && (header[1] & 0xFF) == 0x45
                && (header[2] & 0xFF) == 0xDF && (header[3] & 0xFF) == 0xA3
                && containsWebmDoctype(header)) {
            return EXT_WEBM;
        }
        return null;
    }

    private boolean containsWebmDoctype(byte[] header) {
        String ascii = new String(header, StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
        return ascii.contains("webm");
    }

    private void assertDeclaredMimeMatches(String contentType, String extension, CourseResourceType type) {
        if (contentType == null || contentType.isBlank()) {
            return; // certains clients n'annoncent aucun type : la signature suffit
        }
        String declared = contentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
        if (GENERIC_MIME.contains(declared)) {
            return; // type générique : seule la signature binaire est significative
        }
        boolean ok = EXT_PDF.equals(extension)
                ? PDF_MIME.contains(declared)
                : VIDEO_MIME.contains(declared);
        if (!ok) {
            throw new BusinessException(
                    "Type MIME incohérent avec le contenu réel du fichier (" + declared + ")");
        }
    }

    private void assertOriginalExtensionMatches(String originalFilename, String extension,
                                                CourseResourceType type) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return;
        }
        String name = originalFilename.toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return; // pas d'extension : la signature fait foi
        }
        String declared = name.substring(dot + 1);
        boolean accepted = extension.equals(declared)
                // MP4 est aussi annoncé « m4v » par certains appareils : même conteneur.
                || (EXT_MP4.equals(extension) && "m4v".equals(declared))
                || (EXT_PDF.equals(extension) && "pdf".equals(declared));
        if (!accepted) {
            throw new BusinessException("L'extension du fichier (« ." + declared
                    + " ») ne correspond pas à son contenu réel (« ." + extension + " »)");
        }
        if (type == CourseResourceType.VIDEO && !VIDEO_EXTENSIONS.contains(extension)) {
            throw new BusinessException("Extension vidéo non supportée");
        }
    }

    /**
     * Nettoie le nom d'origine pour l'affichage : retire tout chemin (Windows
     * ou Unix), les caractères de contrôle, et borne la longueur à la colonne.
     */
    String sanitizeOriginalFilename(String originalFilename, String extension) {
        String fallback = "document." + extension;
        if (originalFilename == null || originalFilename.isBlank()) {
            return fallback;
        }
        String name = originalFilename;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isEmpty()) {
            return fallback;
        }
        if (name.length() > 255) {
            name = name.substring(name.length() - 255);
        }
        return name;
    }

    private String humanSize(long bytes) {
        if (bytes >= BYTES_PER_MB) {
            return String.format(Locale.ROOT, "%.1f Mo", bytes / (double) BYTES_PER_MB);
        }
        return bytes + " octets";
    }
}
