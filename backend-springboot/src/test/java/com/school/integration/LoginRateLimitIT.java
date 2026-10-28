package com.school.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Limitation progressive par adresse IP, de bout en bout.
 *
 * <p>Le verrouillage par compte protège un compte ciblé mais laisse un
 * attaquant essayer un mot de passe sur des milliers de comptes. Ce test
 * abaisse le seuil par IP pour observer la réponse HTTP réelle : 429 avec
 * {@code Retry-After}, et uniquement pour l'adresse fautive.</p>
 *
 * <p>Le seuil est fixé bas ({@code 3}) via une propriété dédiée : les autres
 * classes de tests partagent un contexte où la limite reste à 200 pour ne pas
 * se bloquer mutuellement.</p>
 */
@TestPropertySource(properties = "app.security.login.max-failures-per-ip=3")
class LoginRateLimitIT extends AbstractIntegrationTest {

    private static final String IP_ATTAQUANTE = "203.0.113.9";

    @Test
    @DisplayName("au-delà du quota, l'IP fautive reçoit un 429 avec Retry-After, pas les autres")
    void limitationParIpRenvoieUn429AvecRetryAfter() throws Exception {
        for (int i = 0; i < 3; i++) {
            echecDepuis(IP_ATTAQUANTE, "compte-" + i);
        }

        mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", IP_ATTAQUANTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message", containsString("Trop de tentatives")));

        // Une autre origine n'hérite pas du blocage : la limite est par IP.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isOk());
    }

    private void echecDepuis(String ip, String username) throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"faux\"}"))
                .andExpect(status().isUnauthorized());
    }
}
