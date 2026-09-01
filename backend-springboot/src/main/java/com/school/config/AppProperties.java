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
}