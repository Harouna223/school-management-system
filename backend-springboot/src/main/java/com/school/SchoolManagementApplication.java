package com.school;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * KALANKO - Application principale.
 *
 * <p>Le nom technique reste {@code SchoolManagementApplication} (classe, artefact
 * Maven, base de données) : seul le nom affiché de l'application est KALANKO.</p>
 */
@SpringBootApplication
@EnableScheduling
public class SchoolManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchoolManagementApplication.class, args);
    }
}
