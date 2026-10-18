package com.school.controller;

import com.school.dto.request.CourseResourceRequest;
import com.school.dto.response.ApiResponse;
import com.school.dto.response.CourseAccessTokenResponse;
import com.school.dto.response.CourseOptionsResponse;
import com.school.dto.response.CourseResourceResponse;
import com.school.dto.response.PageResponse;
import com.school.entity.CourseResource;
import com.school.enums.CourseResourceType;
import com.school.enums.CourseStatus;
import com.school.service.CourseAccessTokenService;
import com.school.service.CourseResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * Module « Cours et ressources » : publication de vidéos et de PDF, catalogue
 * élève, lecture et téléchargement contrôlés.
 *
 * <p>Les trois endpoints binaires ({@code stream}, {@code preview},
 * {@code download}) sont les SEULS à accepter un jeton en paramètre, parce qu'un
 * {@code <video>} ou un {@code <iframe>} ne peut pas porter l'en-tête
 * {@code Authorization}. Le jeton est court, signé et limité à une ressource et
 * à un usage ; les droits sont revalidés côté service à chaque requête.</p>
 */
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
@Validated
@Tag(name = "Cours et ressources", description = "Cours vidéo et documents PDF")
public class CourseResourceController {

    private static final String NOSNIFF = "X-Content-Type-Options";
    private static final String ACCEPT_RANGES = "Accept-Ranges";
    private static final int BUFFER_SIZE = 64 * 1024;

    private final CourseResourceService courseResourceService;

    // ------------------------------------------------------------------
    // Métadonnées
    // ------------------------------------------------------------------

    @GetMapping
    @Operation(summary = "Lister les cours accessibles au compte connecté")
    public ResponseEntity<ApiResponse<PageResponse<CourseResourceResponse>>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CourseResourceType type,
            @RequestParam(required = false) CourseStatus status,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long classId,
            @Min(value = 0, message = "Le numéro de page ne peut pas être négatif")
            @RequestParam(defaultValue = "0") int page,
            @Min(value = 1, message = "La taille de page doit être au moins 1")
            @Max(value = 100, message = "La taille de page ne peut pas dépasser 100")
            @RequestParam(defaultValue = "10") int size) {
        return ok("Cours", courseResourceService.search(search, type, status, subjectId, classId, page, size));
    }

    @GetMapping("/options")
    @Operation(summary = "Classes et matières que le compte peut cibler")
    public ResponseEntity<ApiResponse<CourseOptionsResponse>> options() {
        return ok("Options de publication", courseResourceService.options());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détails d'un cours")
    public ResponseEntity<ApiResponse<CourseResourceResponse>> get(@PathVariable Long id) {
        return ok("Cours", courseResourceService.get(id));
    }

    @PostMapping
    @Operation(summary = "Créer un cours (brouillon, sans fichier)")
    public ResponseEntity<ApiResponse<CourseResourceResponse>> create(
            @Valid @RequestBody CourseResourceRequest request, HttpServletRequest httpRequest) {
        return ok("Cours créé", courseResourceService.create(request, httpRequest));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier les métadonnées d'un cours")
    public ResponseEntity<ApiResponse<CourseResourceResponse>> update(
            @PathVariable Long id, @Valid @RequestBody CourseResourceRequest request,
            HttpServletRequest httpRequest) {
        return ok("Cours modifié", courseResourceService.update(id, request, httpRequest));
    }

    @PostMapping(value = "/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Téléverser ou remplacer le fichier d'un cours")
    public ResponseEntity<ApiResponse<CourseResourceResponse>> uploadFile(
            @PathVariable Long id, @RequestPart("file") org.springframework.web.multipart.MultipartFile file,
            HttpServletRequest httpRequest) {
        return ok("Fichier téléversé", courseResourceService.uploadFile(id, file, httpRequest));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Publier, dépublier ou archiver un cours")
    public ResponseEntity<ApiResponse<CourseResourceResponse>> changeStatus(
            @PathVariable Long id, @RequestParam CourseStatus status, HttpServletRequest httpRequest) {
        return ok("Statut mis à jour", courseResourceService.changeStatus(id, status, httpRequest));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un cours et son fichier")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        courseResourceService.delete(id, httpRequest);
        return ok("Cours supprimé", null);
    }

    // ------------------------------------------------------------------
    // Jeton de lecture
    // ------------------------------------------------------------------

    @GetMapping("/{id}/token")
    @Operation(summary = "Obtenir un jeton de lecture à durée limitée")
    public ResponseEntity<ApiResponse<CourseAccessTokenResponse>> token(
            @PathVariable Long id, @RequestParam String purpose) {
        return ok("Jeton de lecture", courseResourceService.issueToken(id, purpose));
    }

    // ------------------------------------------------------------------
    // Binaires : stream (Range), preview (inline), download (attachment)
    // ------------------------------------------------------------------

    @GetMapping("/{id}/stream")
    @Operation(summary = "Lire une vidéo (HTTP Range pour la navigation)")
    public void stream(@PathVariable Long id, @RequestParam String token,
                       @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
                       HttpServletResponse response) throws IOException {
        serveVideo(id, token, range, response);
    }

    @GetMapping("/{id}/preview")
    @Operation(summary = "Consulter un PDF dans le navigateur")
    public void preview(@PathVariable Long id, @RequestParam String token,
                        @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
                        HttpServletResponse response) throws IOException {
        serve(CourseAccessTokenService.PURPOSE_PREVIEW, id, token, range, false, response);
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Télécharger une ressource autorisée")
    public void download(@PathVariable Long id, @RequestParam String token,
                         @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
                         HttpServletResponse response) throws IOException {
        serve(CourseAccessTokenService.PURPOSE_DOWNLOAD, id, token, range, true, response);
    }

    private void serveVideo(Long id, String token, String range, HttpServletResponse response)
            throws IOException {
        serve(CourseAccessTokenService.PURPOSE_STREAM, id, token, range, false, response);
    }

    /**
     * Écrit le fichier dans la réponse, avec gestion explicite de {@code Range}.
     *
     * <p>Écriture directe dans le {@code HttpServletResponse} plutôt que renvoi
     * d'un {@code Resource} : on maîtrise alors exactement le statut 206, les
     * en-têtes {@code Content-Range}/{@code Content-Length} et la coupure du
     * flux, sans dépendre de la négociation de convertisseurs.</p>
     */
    private void serve(String purpose, Long id, String token, String rangeHeader,
                       boolean attachment, HttpServletResponse response) throws IOException {
        CourseResourceService.FileAccess access = courseResourceService.resolveFile(id, token, purpose);
        CourseResource resource = access.resource();
        Path path = access.path();
        long length = Files.size(path);

        response.setContentType(mediaTypeOf(resource).toString());
        response.setHeader(ACCEPT_RANGES, "bytes");
        response.setHeader(NOSNIFF, "nosniff");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disposition(resource, attachment));

        long start = 0;
        long end = length - 1;
        int status = HttpServletResponse.SC_OK;

        if (rangeHeader != null && !rangeHeader.isBlank()) {
            HttpRange range;
            try {
                range = HttpRange.parseRanges(rangeHeader).get(0);
            } catch (IllegalArgumentException ex) {
                rejectRange(response, length);
                return;
            }
            start = range.getRangeStart(length);
            end = range.getRangeEnd(length);
            if (start >= length || start > end) {
                rejectRange(response, length);
                return;
            }
            status = HttpServletResponse.SC_PARTIAL_CONTENT;
            response.setHeader(HttpHeaders.CONTENT_RANGE,
                    "bytes " + start + "-" + end + "/" + length);
        }

        long count = end - start + 1;
        response.setStatus(status);
        response.setContentLengthLong(count);

        OutputStream out = response.getOutputStream();
        try (InputStream in = Files.newInputStream(path)) {
            in.skipNBytes(start);
            copy(in, out, count);
        }
        out.flush();
    }

    private void rejectRange(HttpServletResponse response, long length) {
        response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
        response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes */" + length);
    }

    private void copy(InputStream in, OutputStream out, long count) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long remaining = count;
        while (remaining > 0) {
            int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read < 0) {
                break;
            }
            out.write(buffer, 0, read);
            remaining -= read;
        }
    }

    private MediaType mediaTypeOf(CourseResource resource) {
        String mime = resource.getMimeType();
        if (mime != null && !mime.isBlank()) {
            try {
                return MediaType.parseMediaType(mime);
            } catch (IllegalArgumentException ignored) {
                // Repli sur le type déclaré par la ressource.
            }
        }
        return resource.getType() == CourseResourceType.VIDEO
                ? MediaType.parseMediaType("video/mp4")
                : MediaType.APPLICATION_PDF;
    }

    private String disposition(CourseResource resource, boolean attachment) {
        String filename = resource.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            filename = "cours-" + resource.getId()
                    + (resource.getType() == CourseResourceType.PDF ? ".pdf" : ".mp4");
        }
        ContentDisposition.Builder builder = attachment
                ? ContentDisposition.attachment()
                : ContentDisposition.inline();
        return builder.filename(filename, StandardCharsets.UTF_8).build().toString();
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).message(message).data(data)
                .timestamp(LocalDateTime.now()).build());
    }
}
