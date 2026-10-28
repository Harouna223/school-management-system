package com.school.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Erreurs d'encodage côté client sur {@code POST /api/auth/login}.
 *
 * <p>Un JSON mal formé remontait au catch-all de {@link GlobalExceptionHandler}
 * et produisait un 500 : le client recevait une erreur serveur pour une requête
 * qu'il avait lui-même mal écrite, et les logs se remplissaient de stack traces
 * Jackson. Ces tests verrouillent le 400 (corps illisible) et le 415 (mauvais
 * Content-Type).</p>
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new LoginProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("un JSON mal formé renvoie 400 et non 500")
    void malformedJsonReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\","))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Corps de requête illisible ou mal formé"))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));
    }

    @Test
    @DisplayName("un corps vide renvoie 400")
    void emptyBodyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Corps de requête illisible ou mal formé"));
    }

    @Test
    @DisplayName("un tableau JSON au lieu d'un objet renvoie 400")
    void jsonArrayReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Corps de requête illisible ou mal formé"));
    }

    @Test
    @DisplayName("un Content-Type non supporté renvoie 415")
    void unsupportedMediaTypeReturnsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("admin"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("une requête valide n'est pas altérée par la gestion d'erreurs")
    void validRequestStillSucceeds() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"));
    }

    @Test
    @DisplayName("une exception métier inattendue reste un 500")
    void unexpectedErrorStillReturnsServerError() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("fail", "true")
                        .content("{\"username\":\"admin\",\"password\":\"secret\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Erreur interne du serveur"));
    }

    @RestController
    static class LoginProbeController {

        @PostMapping("/api/auth/login")
        Map<String, String> login(@RequestBody Credentials credentials,
                                  @org.springframework.web.bind.annotation.RequestParam(
                                          value = "fail", required = false) String fail) {
            if ("true".equals(fail)) {
                throw new IllegalStateException("panne simulée");
            }
            return Map.of("username", credentials.username());
        }

        record Credentials(String username, String password) {
        }
    }
}
