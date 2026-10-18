package com.school.service;

import com.school.config.AppProperties;
import com.school.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;

/**
 * Jetons de lecture à durée limitée pour les fichiers pédagogiques.
 *
 * <p>Pourquoi un jeton dédié : un élément {@code <video>} ou un {@code <iframe>}
 * ne peut pas envoyer l'en-tête {@code Authorization}. Sans jeton en paramètre,
 * il faudrait soit rendre les fichiers publics (fuite de contenu), soit
 * télécharger toute la vidéo en mémoire pour la rejouer depuis un blob (perte
 * du {@code Range}, donc de la navigation dans la vidéo).</p>
 *
 * <p>Le jeton est signé en HMAC-SHA256 avec le secret JWT de l'application et
 * ne vaut que pour UNE ressource et UN usage. Il n'est pas un jeton
 * d'authentification : l'accès est revalidé à chaque requête contre les
 * permissions réelles du compte.</p>
 */
@Service
@RequiredArgsConstructor
public class CourseAccessTokenService {

    public static final String PURPOSE_STREAM = "stream";
    public static final String PURPOSE_PREVIEW = "preview";
    public static final String PURPOSE_DOWNLOAD = "download";

    private static final Set<String> PURPOSES =
            Set.of(PURPOSE_STREAM, PURPOSE_PREVIEW, PURPOSE_DOWNLOAD);

    private static final String ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final AppProperties appProperties;

    /** Contenu vérifié d'un jeton. */
    public record Payload(Long resourceId, Long userId, String purpose, Instant expiresAt) {
    }

    /**
     * Émet un jeton pour une ressource, un compte et un usage donnés.
     *
     * @param ttlSeconds durée de vie ; doit être strictement positive
     */
    public String issue(Long resourceId, Long userId, String purpose, long ttlSeconds) {
        assertPurpose(purpose);
        if (ttlSeconds <= 0) {
            throw new BusinessException("Durée de validité du jeton invalide");
        }
        if (resourceId == null || userId == null) {
            throw new BusinessException("Jeton impossible : ressource ou compte manquant");
        }
        long expiresAt = Instant.now().getEpochSecond() + ttlSeconds;
        String payload = resourceId + "|" + userId + "|" + purpose + "|" + expiresAt;
        String signature = sign(payload);
        return ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + signature;
    }

    /**
     * Vérifie la signature et l'expiration.
     *
     * @throws BusinessException si le jeton est malformé, expiré, falsifié ou
     *                           destiné à un autre usage
     */
    public Payload verify(String token, String expectedPurpose) {
        assertPurpose(expectedPurpose);
        if (token == null || token.isBlank() || !token.contains(".")) {
            throw new BusinessException("Lien de lecture invalide ou expiré");
        }
        int dot = token.lastIndexOf('.');
        String encodedPayload = token.substring(0, dot);
        String signature = token.substring(dot + 1);

        String payload;
        try {
            payload = new String(DECODER.decode(encodedPayload), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Lien de lecture invalide ou expiré");
        }

        if (!MessageDigest.isEqual(
                sign(payload).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException("Lien de lecture invalide ou expiré");
        }

        String[] parts = payload.split("\\|");
        if (parts.length != 4) {
            throw new BusinessException("Lien de lecture invalide ou expiré");
        }
        Long resourceId;
        Long userId;
        long expiresAt;
        try {
            resourceId = Long.valueOf(parts[0]);
            userId = Long.valueOf(parts[1]);
            expiresAt = Long.parseLong(parts[3]);
        } catch (NumberFormatException ex) {
            throw new BusinessException("Lien de lecture invalide ou expiré");
        }
        String purpose = parts[2];
        assertPurpose(purpose);
        if (!purpose.equals(expectedPurpose)) {
            throw new BusinessException("Ce lien ne permet pas cet usage");
        }
        if (Instant.now().getEpochSecond() > expiresAt) {
            throw new BusinessException("Lien de lecture expiré : rechargez la page");
        }
        return new Payload(resourceId, userId, purpose, Instant.ofEpochSecond(expiresAt));
    }

    public long ttlSeconds() {
        return Math.max(1, appProperties.getCourses().getStreamTokenTtlMinutes()) * 60L;
    }

    private void assertPurpose(String purpose) {
        if (purpose == null || !PURPOSES.contains(purpose)) {
            throw new BusinessException("Usage de lecture inconnu");
        }
    }

    private String sign(String payload) {
        String secret = appProperties.getJwt() != null ? appProperties.getJwt().getSecret() : null;
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Secret JWT absent : impossible de signer un jeton de lecture");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return ENCODER.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Signature du jeton de lecture impossible", ex);
        }
    }
}
