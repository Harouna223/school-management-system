package com.school.exception;

import com.school.dto.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gestion globale et centralisée des erreurs API.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** Repère la colonne citée par MySQL dans le message d'erreur d'origine. */
    private static final Pattern COLUMN_IN_MESSAGE = Pattern.compile("column '([^']+)'");

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex,
                                                            HttpServletRequest request) {
        log.warn("Ressource introuvable : {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex,
                                                            HttpServletRequest request) {
        log.warn("Erreur métier : {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex,
                                                                  HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Identifiants incorrects", request);
    }

    @ExceptionHandler(TokenRefreshException.class)
    public ResponseEntity<ApiResponse<Void>> handleTokenRefresh(TokenRefreshException ex,
                                                                HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    /**
     * Limitation progressive par IP : 429 explicite avec le délai d'attente,
     * pour que le client sache quand réessayer au lieu de boucler.
     */
    @ExceptionHandler(TooManyLoginAttemptsException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooManyLoginAttempts(TooManyLoginAttemptsException ex,
                                                                        HttpServletRequest request) {
        log.warn("Trop de tentatives de connexion depuis {}", request.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .message(ex.getMessage())
                        .path(request.getRequestURI())
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex,
                                                                HttpServletRequest request) {
        log.warn("Accès refusé sur {} : {}", request.getRequestURI(), ex.getMessage());
        String message = ex.getMessage() != null && !ex.getMessage().isBlank()
                ? ex.getMessage()
                : "Accès refusé : permissions insuffisantes";
        return build(HttpStatus.FORBIDDEN, message, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .message("Erreur de validation des champs")
                        .data(errors)
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadSize(MaxUploadSizeExceededException ex,
                                                              HttpServletRequest request) {
        // Message volontairement générique : la limite dépend du type de fichier
        // (vidéo ou PDF) et de la configuration, pas d'une valeur fixe.
        return build(HttpStatus.PAYLOAD_TOO_LARGE,
                "Fichier trop volumineux : la limite autorisée est dépassée", request);
    }

    /**
     * Violation d'une contrainte de base.
     *
     * <p>Un message unique « Donnée en conflit » ne désignait aucun champ :
     * l'administrateur qui inscrivait un élève recevait un 409 sans piste,
     * alors que la cause était le plus souvent une adresse email déjà
     * rattachée à un autre compte. MySQL nomme précisément la clé violée
     * (« Duplicate entry 'x@y.cm' for key 'users.uk_users_email' ») : on s'en
     * sert pour répondre quelque chose d'exploitable.</p>
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex,
                                                                 HttpServletRequest request) {
        String message = describeConstraint(ex);
        log.error("Violation d'intégrité sur {} : {} — cause : {}",
                request.getRequestURI(), message, ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, message, request);
    }

    /**
     * Traduit la contrainte violée en message destiné à l'utilisateur.
     *
     * <p>Le libellé d'origine est conservé pour les cas non répertoriés : mieux
     * vaut un message générique qu'une erreur masquée.</p>
     *
     * <p>Le repli final ne doit rester qu'un filet de sécurité. Il annonçait
     * « la ressource existe déjà ou est référencée » alors que la cause
     * réelle était souvent toute autre — une valeur trop longue pour sa
     * colonne, ou des caractères non pris en charge. L'utilisateur cherchait
     * donc un doublon inexistant, et le vrai champ fautif n'était jamais
     * nommé. Les deux cas les plus fréquents sur MySQL sont désormais
     * reconnus, avec le nom de la colonne en cause.</p>
     */
    private String describeConstraint(DataIntegrityViolationException ex) {
        String detail = ex.getMostSpecificCause().getMessage();
        String normalized = detail == null ? "" : detail.toLowerCase(Locale.ROOT);

        if (normalized.contains("uk_users_email")) {
            return "Cette adresse email est déjà utilisée par un autre compte : "
                    + "laissez le champ Email vide pour qu'une adresse unique soit générée.";
        }
        if (normalized.contains("uk_users_username")) {
            return "Ce nom d'utilisateur est déjà utilisé par un autre compte.";
        }
        if (normalized.contains("uk_students_matricule")) {
            return "Ce matricule est déjà attribué à un autre élève.";
        }
        if (normalized.contains("uk_teachers_employee_no")) {
            return "Ce numéro d'enseignant est déjà attribué.";
        }
        if (normalized.contains("cannot be null")) {
            return "Un champ obligatoire est vide : vérifiez le formulaire avant d'enregistrer.";
        }
        if (normalized.contains("cannot add or update a child row")
                || normalized.contains("foreign key")) {
            return "Référence introuvable : la classe, le parent ou l'année sélectionnée "
                    + "n'existe plus. Rechargez la page et réessayez.";
        }
        if (normalized.contains("data too long") || normalized.contains("data truncation")) {
            return "Une valeur saisie est trop longue pour le champ" + namedColumn(detail)
                    + " : raccourcissez-la avant d'enregistrer.";
        }
        if (normalized.contains("incorrect string value")) {
            return "Le champ" + namedColumn(detail) + " contient des caractères non pris en charge "
                    + "(emoji, symboles) : retirez-les et réessayez.";
        }
        if (normalized.contains("out of range") || normalized.contains("incorrect integer value")) {
            return "Une valeur numérique du champ" + namedColumn(detail)
                    + " est hors limites : vérifiez le montant ou la quantité saisie.";
        }
        if (normalized.contains("uk_")) {
            return "Cette donnée existe déjà : un enregistrement identique est présent.";
        }
        return "Donnée en conflit : la ressource existe déjà ou est référencée";
    }

    /**
     * Nom de colonne cité par MySQL (« Data too long for column 'first_name' »),
     * rendu sous la forme {@code « first_name »}, ou chaîne vide si le message
     * n'en cite aucun.
     *
     * <p>C'est le nom technique de la colonne, pas le libellé du formulaire :
     * il reste assez parlant pour retrouver le champ, et il correspond à ce
     * que l'exploitant voit dans le journal d'erreurs.</p>
     */
    private String namedColumn(String detail) {
        if (detail == null) {
            return "";
        }
        Matcher matcher = COLUMN_IN_MESSAGE.matcher(detail);
        return matcher.find() ? " « " + matcher.group(1) + " »" : "";
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException ex,
                                                                   HttpServletRequest request) {
        log.debug("Ressource statique introuvable : {}", request.getRequestURI());
        return build(HttpStatus.NOT_FOUND, "Ressource introuvable", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                      HttpServletRequest request) {
        log.warn("Méthode non supportée : {} {}", ex.getMethod(), request.getRequestURI());
        return build(HttpStatus.METHOD_NOT_ALLOWED, "Méthode HTTP non supportée pour cette ressource", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex,
                                                                HttpServletRequest request) {
        log.warn("Paramètre requis manquant : {} sur {}", ex.getParameterName(), request.getRequestURI());
        return build(HttpStatus.BAD_REQUEST,
                "Paramètre requis manquant : " + ex.getParameterName(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                                HttpServletRequest request) {
        log.warn("Paramètre invalide : {} = {}", ex.getName(), ex.getValue());
        return build(HttpStatus.BAD_REQUEST,
                "Paramètre invalide : " + ex.getName(), request);
    }

    /**
     * Paramètre de requête invalide (ex. {@code page} négatif ou {@code size}
     * hors bornes, contrôlés par {@code @Min}/{@code @Max} sur les contrôleurs).
     * Sans ce gestionnaire, la violation remontait en 500.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException ex,
                                                                    HttpServletRequest request) {
        String detail = ex.getAllValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream())
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse("Paramètres de requête invalides");
        log.warn("Paramètres invalides sur {} : {}", request.getRequestURI(), detail);
        return build(HttpStatus.BAD_REQUEST, detail, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex,
                                                                       HttpServletRequest request) {
        String detail = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElse("Paramètres de requête invalides");
        log.warn("Contrainte violée sur {} : {}", request.getRequestURI(), detail);
        return build(HttpStatus.BAD_REQUEST, detail, request);
    }

    /**
     * Argument illégal : typiquement {@code PageRequest.of(page, size)} appelé
     * avec une page négative ou une taille nulle par un service qui n'est pas
     * encore borné. Réponse 400 explicite plutôt qu'un 500 opaque.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex,
                                                                   HttpServletRequest request) {
        log.warn("Argument invalide sur {} : {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Paramètre invalide : " + ex.getMessage(), request);
    }

    /**
     * Corps de requête illisible : JSON mal formé, vide, ou de forme inattendue
     * (tableau au lieu d'objet). Sans ce gestionnaire, l'exception tombait dans
     * le catch-all et le client recevait un 500 alors que la faute est dans la
     * requête — cas observé sur {@code POST /api/auth/login}.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                                  HttpServletRequest request) {
        log.warn("Corps de requête illisible sur {} : {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Corps de requête illisible ou mal formé", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex,
                                                                       HttpServletRequest request) {
        log.warn("Type de média non supporté sur {} : {}", request.getRequestURI(), ex.getContentType());
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Type de contenu non supporté : utilisez application/json", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Erreur non gérée : ", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne du serveur", request);
    }

    private ResponseEntity<ApiResponse<Void>> build(HttpStatus status, String message,
                                                    HttpServletRequest request) {
        return ResponseEntity.status(status).body(
                ApiResponse.<Void>builder()
                        .success(false)
                        .message(message)
                        .path(request.getRequestURI())
                        .timestamp(LocalDateTime.now())
                        .build());
    }
}
