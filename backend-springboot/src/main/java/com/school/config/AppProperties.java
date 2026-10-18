package com.school.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propriétés applicatives personnalisées (école, JWT, uploads).
 */
@Component
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Cors cors = new Cors();
    private School school = new School();
    private WhatsApp whatsapp = new WhatsApp();
    private Sms sms = new Sms();
    private Courses courses = new Courses();
    private String uploadDir = "./uploads";

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private long expirationMs;
        private long refreshExpirationMs;
    }

    @Getter
    @Setter
    public static class Cors {
        private String allowedOrigins;
    }

    @Getter
    @Setter
    public static class School {
        private String name;
        private String address;
        private String phone;
        private String email;
    }

    @Getter
    @Setter
    public static class WhatsApp {
        private boolean enabled;
        private String provider = "meta";
        private String metaToken;
        private String metaPhoneId;
        private String twilioSid;
        private String twilioToken;
        private String twilioFrom;
        private String defaultCountryCode = "237";
    }

    @Getter
    @Setter
    public static class Sms {
        private boolean enabled;
        private String twilioSid;
        private String twilioToken;
        private String twilioFrom;
    }

    /**
     * Paramètres du module « Cours et ressources ».
     *
     * <p>Les limites sont configurables pour être ajustées à la capacité de
     * stockage et au débit réel de l'établissement, sans recompilation.</p>
     */
    @Getter
    @Setter
    public static class Courses {
        /** Taille maximale d'une vidéo, en mégaoctets. */
        private int maxVideoMb = 500;
        /** Taille maximale d'un PDF, en mégaoctets. */
        private int maxPdfMb = 25;
        /**
         * Durée de vie du jeton de lecture. Doit couvrir une séance de travail :
         * un jeton trop court interrompt la lecture d'une longue vidéo.
         */
        private int streamTokenTtlMinutes = 120;
    }
}