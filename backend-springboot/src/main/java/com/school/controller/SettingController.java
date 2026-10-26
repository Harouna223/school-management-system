package com.school.controller;

import com.school.dto.response.ApiResponse;
import com.school.dto.response.SettingResponse;
import com.school.service.FileStorageService;
import com.school.service.SettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Paramètres de l'établissement (identité, reçus, WhatsApp).
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
@Tag(name = "Paramètres", description = "Configuration de l'établissement")
public class SettingController {

    private final SettingService settingService;
    private final FileStorageService fileStorageService;

    @GetMapping
    @Operation(summary = "Lister les paramètres")
    public ResponseEntity<ApiResponse<List<SettingResponse>>> all() {
        return ok("Paramètres", settingService.all());
    }

    @GetMapping("/defaults")
    @Operation(summary = "Valeurs par défaut issues de la configuration")
    public ResponseEntity<ApiResponse<Map<String, String>>> defaults() {
        return ok("Valeurs par défaut", settingService.defaults());
    }

    @PutMapping
    @Operation(summary = "Mettre à jour les paramètres (création si absents)")
    public ResponseEntity<ApiResponse<List<SettingResponse>>> update(
            @RequestBody Map<String, String> values, HttpServletRequest httpRequest) {
        return ok("Paramètres mis à jour", settingService.updateAll(values, httpRequest));
    }

    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Uploader le logo de l'établissement")
    public ResponseEntity<ApiResponse<String>> uploadLogo(@RequestPart("file") MultipartFile file,
                                                          HttpServletRequest httpRequest) {
        // L'ancien logo n'est supprimé qu'APRÈS l'enregistrement du nouveau :
        // un upload interrompu ne laisse jamais l'application sans logo.
        String previous = settingService.value(SettingService.SCHOOL_LOGO, "");
        String url = fileStorageService.store(file, "school");
        settingService.updateAll(Map.of(SettingService.SCHOOL_LOGO, url), httpRequest);
        if (previous != null && !previous.isBlank() && !previous.equals(url)) {
            settingService.deleteReplacedLogo(previous);
        }
        return ok("Logo mis à jour", url);
    }

    @DeleteMapping("/logo")
    @Operation(summary = "Supprimer le logo de l'établissement")
    public ResponseEntity<ApiResponse<Void>> deleteLogo(HttpServletRequest httpRequest) {
        settingService.deleteLogo(httpRequest);
        return ok("Logo supprimé", null);
    }

    /**
     * Télécharge le logo de l'établissement — exactement le fichier affiché en
     * tête de tous les documents PDF et dans la pastille de la carte scolaire.
     *
     * <p>Pourquoi un point d'entrée dédié alors que {@code /uploads/…} sert déjà
     * le fichier : le navigateur y accède avec le jeton dans l'en-tête
     * {@code Authorization}, qu'un simple lien {@code <a href>} ne peut pas
     * envoyer. Et surtout, {@code /uploads/…} répond en {@code inline} : ouvrir
     * le logo l'affiche au lieu de l'enregistrer, et le nom du fichier est un
     * UUID. Ici le nom est lisible et le téléchargement est forcé.</p>
     *
     * <p><b>Repli</b> : si aucun logo n'a été téléversé — ou si le fichier
     * référencé a disparu du disque — c'est le logo livré avec l'application
     * qui est renvoyé. C'est celui qui est réellement imprimé sur les
     * documents dans ce cas ; répondre 404 laisserait l'utilisateur sans moyen
     * de récupérer le logo qu'il voit sur ses bulletins.</p>
     */
    @GetMapping("/logo/download")
    @Operation(summary = "Télécharger le logo de l'établissement")
    public ResponseEntity<org.springframework.core.io.Resource> downloadLogo() {
        String stored = settingService.value(SettingService.SCHOOL_LOGO, "");
        if (!stored.isBlank()) {
            java.util.Optional<java.nio.file.Path> file = settingService.resolveUploadedFile(stored);
            if (file.isPresent()) {
                String extension = extensionOf(file.get());
                return attachment(new org.springframework.core.io.FileSystemResource(file.get()),
                        downloadName(extension), mediaTypeOf(extension));
            }
            // Référence en base mais fichier absent : on continue vers le
            // logo par défaut plutôt que d'échouer.
        }
        org.springframework.core.io.ClassPathResource fallback =
                new org.springframework.core.io.ClassPathResource("static/school-logo.png");
        if (!fallback.exists()) {
            throw new com.school.exception.ResourceNotFoundException(
                    "Aucun logo n'est disponible pour l'établissement");
        }
        return attachment(fallback, downloadName("png"), MediaType.IMAGE_PNG);
    }

    /** Réponse « pièce jointe » : téléchargement forcé, pas d'affichage en ligne. */
    private ResponseEntity<org.springframework.core.io.Resource> attachment(
            org.springframework.core.io.Resource resource, String filename,
            org.springframework.http.MediaType mediaType) {
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .header("X-Content-Type-Options", "nosniff")
                // Le logo change à chaque téléversement : aucune mise en cache
                // ne doit servir un ancien fichier à la place du nouveau.
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(resource);
    }

    /** Extension réelle du fichier (le nom sur disque est un UUID). */
    private String extensionOf(java.nio.file.Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT) : "";
    }

    private org.springframework.http.MediaType mediaTypeOf(String extension) {
        return switch (extension) {
            case "png" -> MediaType.IMAGE_PNG;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "webp" -> MediaType.parseMediaType("image/webp");
            case "gif" -> MediaType.IMAGE_GIF;
            // Type inconnu : jamais de MIME deviné par le navigateur.
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    /**
     * Nom du fichier téléchargé, lisible : « logo-<sigle ou nom>-<date>.<ext> ».
     *
     * <p>Un nom fixe ferait écraser le logo précédent à chaque téléchargement ;
     * la date permet de conserver plusieurs versions sur le poste de
     * l'utilisateur. Les accents sont retirés : certains systèmes de fichiers
     * et clients de messagerie les rejouent mal.</p>
     */
    private String downloadName(String extension) {
        String base = settingService.value(SettingService.SCHOOL_SHORT_NAME, "");
        if (base.isBlank()) {
            base = settingService.value(SettingService.SCHOOL_NAME, "");
        }
        String slug = java.text.Normalizer.normalize(base, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        if (slug.isBlank()) {
            slug = "etablissement";
        }
        String suffix = extension.isBlank() ? "" : "." + extension;
        return "logo-" + slug + "-" + java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE) + suffix;
    }

    // ------------------------------------------------------------------
    // Informations de l'établissement (module dédié)
    // ------------------------------------------------------------------

    @GetMapping("/school")
    @Operation(summary = "Informations officielles de l'établissement")
    public ResponseEntity<ApiResponse<com.school.dto.response.SchoolInfoResponse>> schoolInfo() {
        return ok("Informations de l'établissement", settingService.schoolInfo());
    }

    @PutMapping("/school")
    @Operation(summary = "Enregistrer les informations de l'établissement")
    public ResponseEntity<ApiResponse<com.school.dto.response.SchoolInfoResponse>> updateSchoolInfo(
            @jakarta.validation.Valid @RequestBody com.school.dto.request.SchoolInfoRequest request,
            HttpServletRequest httpRequest) {
        return ok("Informations enregistrées", settingService.updateSchoolInfo(request, httpRequest));
    }

    @GetMapping("/cycles")
    @Operation(summary = "Cycles d'enseignement activés (Jardin, Primaire, Collège, Lycée, Université)")
    public ResponseEntity<ApiResponse<List<String>>> cycles() {
        return ok("Cycles actifs", settingService.getActiveCycles());
    }

    @PutMapping("/cycles")
    @Operation(summary = "Activer / désactiver les cycles d'enseignement")
    public ResponseEntity<ApiResponse<List<String>>> updateCycles(
            @RequestBody List<String> cycles, HttpServletRequest httpRequest) {
        return ok("Cycles mis à jour", settingService.updateActiveCycles(cycles, httpRequest));
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).message(message).data(data)
                .timestamp(LocalDateTime.now()).build());
    }
}