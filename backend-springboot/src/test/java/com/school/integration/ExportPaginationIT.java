package com.school.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exports paginés (enseignants, paiements, factures, dépenses).
 *
 * <p>Ces endpoints demandaient une page unique {@code size=10000}, refusée par
 * le plafond de pagination : ils renvoyaient TOUS un 400, quel que soit le
 * volume de données. Ils chargent désormais leurs lignes par pages bornées.</p>
 */
class ExportPaginationIT extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("les exports enseignants/paiements/factures/dépenses produisent un document")
    void lesExportsNeSontPlusRefusesParLaPagination() throws Exception {
        String token = jetonAdmin();

        for (String url : new String[]{
                "/api/teachers/export/excel",
                "/api/payments/export/excel",
                "/api/payments/invoices/export/excel",
                "/api/finance/expenses/export/excel",
                "/api/payments/export/pdf",
                "/api/finance/expenses/export/pdf"}) {
            byte[] document = mockMvc.perform(get(url).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray();
            assertThat(document)
                    .as("document généré par " + url)
                    .isNotEmpty();
        }
    }

    private String jetonAdmin() throws Exception {
        String corps = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(corps).path("data").path("accessToken").asText();
    }
}
