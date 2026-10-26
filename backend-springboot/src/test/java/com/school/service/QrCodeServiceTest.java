package com.school.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Génération du QR Code des cartes scolaires.
 *
 * <p>Ce service n'avait aucun test. Ses deux risques sont silencieux : un
 * payload modifié casse les cartes <b>déjà imprimées</b> (aucune erreur, juste
 * des lecteurs qui ne reconnaissent plus le format), et un PNG invalide ne se
 * voit qu'à l'impression ou à l'ouverture du fichier.</p>
 *
 * <p>Les assertions portent donc sur des octets réels, pas sur un mock : le PNG
 * est relu par {@link ImageIO} et ses dimensions sont vérifiées.</p>
 */
class QrCodeServiceTest {

    private final QrCodeService service = new QrCodeService();

    @Test
    @DisplayName("le payload garde exactement le format des cartes imprimées")
    void payloadExact() {
        // Les libellés « Matricule: », « Nom: », « Classe: » sont lus par les
        // lecteurs : les renommer casse le parc de cartes existant.
        assertThat(service.studentQrPayload("EL-2026-007", "Aïssata Traoré", "6e A"))
                .isEqualTo("""
                        CARTE SCOLAIRE
                        Matricule: EL-2026-007
                        Nom: Aïssata Traoré
                        Classe: 6e A""");
    }

    @Test
    @DisplayName("un élève sans classe n'imprime pas « Classe: null »")
    void payloadSansClasse() {
        assertThat(service.studentQrPayload("EL-2026-008", "Moussa Keïta", null))
                .endsWith("Classe: Non affectée");
        assertThat(service.studentQrPayload("EL-2026-008", "Moussa Keïta", "   "))
                .endsWith("Classe: Non affectée");
    }

    @Test
    @DisplayName("produit un vrai PNG de 350 × 350")
    void pngValide() throws Exception {
        byte[] png = service.studentQrPng("EL-2026-007", "Aïssata Traoré", "6e A");

        assertThat(png).isNotEmpty();
        // Signature PNG : 89 50 4E 47 0D 0A 1A 0A
        assertThat(png[0] & 0xFF).isEqualTo(0x89);
        assertThat(new String(png, 1, 3)).isEqualTo("PNG");

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image).as("le flux doit être une image décodable").isNotNull();
        assertThat(image.getWidth()).isEqualTo(350);
        assertThat(image.getHeight()).isEqualTo(350);
    }

    @Test
    @DisplayName("écrit l'image dans la réponse HTTP avec le bon nom de fichier")
    void ecritDansLaReponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.generateStudentQr(response, "EL-2026-007", "Aïssata Traoré", "6e A");

        assertThat(response.getContentType()).isEqualTo("image/png");
        assertThat(response.getHeader("Content-Disposition"))
                .isEqualTo("attachment; filename=\"carte-EL-2026-007.png\"");
        assertThat(response.getContentAsByteArray()).isNotEmpty();
        assertThat(ImageIO.read(new ByteArrayInputStream(response.getContentAsByteArray())))
                .isNotNull();
    }

    @Test
    @DisplayName("un contenu trop long remonte une erreur explicite, pas un WriterException brut")
    void contenuTropLong() {
        // Au-delà de ~3 Ko, la capacité maximale d'un QR Code est dépassée :
        // zxing lève WriterException, que le service doit traduire.
        String enorme = "A".repeat(5000);

        assertThatThrownBy(() -> service.studentQrPng("EL-2026-007", enorme, "6e A"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("QR Code")
                .hasCauseInstanceOf(com.google.zxing.WriterException.class);
    }
}
