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
import java.util.Map;

/**
 * Gestion globale et centralisée des erreurs API.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

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

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex,
                                                                 HttpServletRequest request) {
        log.error("Violation d'intégrité : {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "Donnée en conflit : la ressource existe déjà ou est référencée", request);
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
