package com.school.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration Web.
 *
 * <p>Note : les fichiers téléversés (/uploads) sont désormais servis par
 * {@code com.school.controller.FileController} (authentification requise +
 * vérification anti-traversal), plus par un {@code ResourceHandler} public —
 * voir {@code com.school.security.SecurityConfig}.</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
}