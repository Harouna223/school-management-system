package com.school.service;

import com.school.config.AppProperties;
import com.school.entity.MessageLog;
import com.school.entity.Parent;
import com.school.entity.Student;
import com.school.entity.User;
import com.school.enums.AttendanceStatus;
import com.school.enums.Term;
import com.school.repository.MessageLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Notifications WhatsApp aux parents — deux fournisseurs (Meta Cloud API et Twilio).
 *
 * <p>Trois différences avec {@code SmsService} méritent d'être verrouillées, parce qu'elles se
 * ressemblent à s'y méprendre :</p>
 * <ol>
 *   <li><b>Les deux causes sont séparées.</b> {@code !isEnabled()} journalise {@code SIMULATED} avec
 *       un {@code errorMessage} <b>nul</b> ; un numéro invalide journalise {@code FAILED} avec
 *       « Numéro de téléphone absent ou invalide ». {@code SmsService}, lui, fusionne les deux dans une
 *       seule condition — et renseigne donc un message dans les deux cas.</li>
 *   <li><b>Le routage par fournisseur.</b> Seul {@code "twilio"} (insensible à la casse) prend la
 *       branche Twilio ; <b>tout le reste</b> — y compris une valeur inconnue — part chez Meta.</li>
 *   <li><b>Twilio WhatsApp préfixe les deux numéros</b> par {@code "whatsapp:"} (From <i>et</i> To).
 *       Oublier ce préfixe envoie un SMS au lieu d'un message WhatsApp, sans aucune erreur.</li>
 * </ol>
 *
 * <p>{@code absenceMessage(...)} est <b>public</b> : c'est une fonction pure, testée sans mock ni
 * réseau, et c'est le corps réutilisé par le lien {@code wa.me}.</p>
 */
@ExtendWith(MockitoExtension.class)
class WhatsAppServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 1, 15);

    @Mock
    private PhoneNumberService phoneNumberService;
    @Mock
    private MessageLogRepository messageLogRepository;

    private AppProperties appProperties;
    private AppProperties.WhatsApp config;
    private MockRestServiceServer server;
    private WhatsAppService service;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getSchool().setName("Sahel Edu");
        config = appProperties.getWhatsapp();

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new WhatsAppService(appProperties, builder, phoneNumberService, messageLogRepository);
    }

    private static Parent parent() {
        Parent p = new Parent();
        p.setId(4L);
        p.setFirstName("Koffi");
        p.setLastName("Ama");
        p.setPhone("76 12 34 56");
        return p;
    }

    private static Student student() {
        Student s = new Student();
        s.setId(10L);
        s.setFirstName("Aïcha");
        s.setLastName("Kamdem");
        s.setMatricule("ENS-2026-0001");
        return s;
    }

    private void metaConfigure() {
        config.setEnabled(true);
        config.setProvider("meta");
        config.setMetaToken("EAAB-token");
        config.setMetaPhoneId("123456789");
    }

    private void twilioConfigure() {
        config.setEnabled(true);
        config.setProvider("twilio");
        config.setTwilioSid("AC123");
        config.setTwilioToken("secret");
        config.setTwilioFrom("+22300000000");
    }

    private MessageLog capturedLog() {
        ArgumentCaptor<MessageLog> cap = ArgumentCaptor.forClass(MessageLog.class);
        verify(messageLogRepository).save(cap.capture());
        return cap.getValue();
    }

    // ==================================================================
    // absenceMessage : fonction pure, publique
    // ==================================================================

    @Test
    void absenceMessage_composeLeMessageComplet() {
        String body = service.absenceMessage(parent(), student(), DATE, AttendanceStatus.ABSENT, null);

        assertThat(body)
                .contains("Koffi Ama")
                .contains("Aïcha Kamdem")
                .contains("absent(e)")
                .contains("15/01/2026")
                .contains("Sahel Edu");
    }

    @Test
    void absenceMessage_utiliseLaFormulationRetardPourToutAutreStatut() {
        String body = service.absenceMessage(parent(), student(), DATE, AttendanceStatus.LATE, null);

        assertThat(body).contains("en retard").doesNotContain("absent(e)");
    }

    @Test
    void absenceMessage_ajouteLaJustificationQuandElleEstFournie() {
        String body = service.absenceMessage(parent(), student(), DATE, AttendanceStatus.ABSENT,
                "Rendez-vous medical");

        assertThat(body).contains("Justification : Rendez-vous medical.");
    }

    @Test
    void absenceMessage_ometLaJustificationVide() {
        String body = service.absenceMessage(parent(), student(), DATE, AttendanceStatus.ABSENT, "   ");

        assertThat(body).doesNotContain("Justification");
    }

    @Test
    void absenceMessage_ometLaJustificationNulle() {
        String body = service.absenceMessage(parent(), student(), DATE, AttendanceStatus.ABSENT, null);

        assertThat(body).doesNotContain("Justification");
    }

    // ==================================================================
    // Branches de repli
    // ==================================================================

    @Test
    void serviceDesactive_journaliseUneSimulationSansMessageDErreur() {
        config.setEnabled(false);
        when(phoneNumberService.normalize("76 12 34 56")).thenReturn("+22376123456");

        service.sendAbsenceAlert(parent(), student(), DATE, AttendanceStatus.ABSENT, null);

        MessageLog log = capturedLog();
        assertThat(log.getStatus()).isEqualTo(MessageLog.Status.SIMULATED);
        assertThat(log.getErrorMessage())
                .as("une simulation n'est pas une erreur : le champ reste vide")
                .isNull();
        assertThat(log.getChannel()).isEqualTo(MessageLog.Channel.WHATSAPP);
        server.verify();
    }

    @Test
    void numeroInvalide_serviceActif_journaliseUnEchec() {
        config.setEnabled(true);
        when(phoneNumberService.normalize("76 12 34 56")).thenReturn(null);

        service.sendAbsenceAlert(parent(), student(), DATE, AttendanceStatus.ABSENT, null);

        MessageLog log = capturedLog();
        assertThat(log.getStatus()).isEqualTo(MessageLog.Status.FAILED);
        assertThat(log.getErrorMessage()).isEqualTo("Numéro de téléphone absent ou invalide");
        server.verify();
    }

    @Test
    void metaIncomplet_journaliseUnEchecSansAppelerLAPI() {
        config.setEnabled(true);
        config.setProvider("meta");
        config.setMetaToken("EAAB-token");
        // phoneId manquant
        when(phoneNumberService.normalize("76 12 34 56")).thenReturn("+22376123456");

        service.sendAbsenceAlert(parent(), student(), DATE, AttendanceStatus.ABSENT, null);

        MessageLog log = capturedLog();
        assertThat(log.getStatus()).isEqualTo(MessageLog.Status.FAILED);
        assertThat(log.getErrorMessage()).isEqualTo("Échec de l'envoi via meta");
        server.verify();
    }

    @Test
    void twilioIncomplet_journaliseUnEchecSansAppelerLAPI() {
        config.setEnabled(true);
        config.setProvider("twilio");
        config.setTwilioSid("AC123");
        // token et from manquants
        when(phoneNumberService.normalize("76 12 34 56")).thenReturn("+22376123456");

        service.sendAbsenceAlert(parent(), student(), DATE, AttendanceStatus.ABSENT, null);

        MessageLog log = capturedLog();
        assertThat(log.getStatus()).isEqualTo(MessageLog.Status.FAILED);
        assertThat(log.getErrorMessage()).isEqualTo("Échec de l'envoi via twilio");
        server.verify();
    }

    // ==================================================================
    // Routage par fournisseur
    // ==================================================================

    @Test
    void providerMeta_envoieSurLaGraphApiAvecUnJetonBearer() {
        metaConfigure();
        when(phoneNumberService.normalize("76 12 34 56")).thenReturn("+22376123456");

        server.expect(requestTo("https://graph.facebook.com/v21.0/123456789/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer EAAB-token"))
                .andExpect(content().string(containsString("\"messaging_product\":\"whatsapp\"")))
                .andExpect(content().string(containsString("\"to\":\"+22376123456\"")))
                .andExpect(content().string(containsString("\"type\":\"text\"")))
                .andRespond(withSuccess());

        service.sendAbsenceAlert(parent(), student(), DATE, AttendanceStatus.ABSENT, null);

        server.verify();
        assertThat(capturedLog().getStatus()).isEqualTo(MessageLog.Status.SENT);
    }

    @Test
    void providerTwilio_prefixeLesDeuxNumerosParWhatsapp() {
        twilioConfigure();
        when(phoneNumberService.normalize("76 12 34 56")).thenReturn("+22376123456");
        String credentials = Base64.getEncoder().encodeToString("AC123:secret".getBytes());

        server.expect(requestTo("https://api.twilio.com/2010-04-01/Accounts/AC123/Messages.json"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic " + credentials))
                .andExpect(content().string(containsString("From=whatsapp%3A%2B22300000000")))
                .andExpect(content().string(containsString("To=whatsapp%3A%2B22376123456")))
                .andRespond(withSuccess());

        service.sendAbsenceAlert(parent(), student(), DATE, AttendanceStatus.ABSENT, null);

        server.verify();
        assertThat(capturedLog().getStatus()).isEqualTo(MessageLog.Status.SENT);
    }

    @Test
    void providerInconnu_basculeSurMeta() {
        config.setEnabled(true);
        config.setProvider("autre-fournisseur");
        config.setMetaToken("EAAB-token");
        config.setMetaPhoneId("123456789");
        when(phoneNumberService.normalize("76 12 34 56")).thenReturn("+22376123456");

        server.expect(requestTo("https://graph.facebook.com/v21.0/123456789/messages"))
                .andRespond(withSuccess());

        service.sendAbsenceAlert(parent(), student(), DATE, AttendanceStatus.ABSENT, null);

        server.verify();
    }

    @Test
    void erreurDuFournisseur_journaliseUnEchecSansPropager() {
        metaConfigure();
        when(phoneNumberService.normalize("76 12 34 56")).thenReturn("+22376123456");

        server.expect(requestTo("https://graph.facebook.com/v21.0/123456789/messages"))
                .andRespond(withServerError());

        assertThatCode(() -> service.sendAbsenceAlert(parent(), student(), DATE,
                AttendanceStatus.ABSENT, null)).doesNotThrowAnyException();

        server.verify();
        MessageLog log = capturedLog();
        assertThat(log.getStatus()).isEqualTo(MessageLog.Status.FAILED);
        assertThat(log.getErrorMessage()).isEqualTo("Échec de l'envoi via meta");
    }

    // ==================================================================
    // Robustesse de l'historique
    // ==================================================================

    @Test
    void uneErreurDeJournalisationNeFaitPasEchouerLEnvoi() {
        config.setEnabled(false);
        when(phoneNumberService.normalize("76 12 34 56")).thenReturn("+22376123456");
        when(messageLogRepository.save(any(MessageLog.class)))
                .thenThrow(new RuntimeException("base indisponible"));

        assertThatCode(() -> service.sendAbsenceAlert(parent(), student(), DATE,
                AttendanceStatus.ABSENT, null)).doesNotThrowAnyException();
    }

    @Test
    void tronqueUnMessageTropLongA2000Caracteres() {
        config.setEnabled(false);
        when(phoneNumberService.normalize("76 12 34 56")).thenReturn("+22376123456");

        service.sendAbsenceAlert(parent(), student(), DATE, AttendanceStatus.ABSENT, "x".repeat(2500));

        assertThat(capturedLog().getMessage()).hasSize(2000);
    }

    @Test
    void journaliseLeDestinataireLeSujetEtLesIdentifiants() {
        config.setEnabled(false);
        when(phoneNumberService.normalize("76 12 34 56")).thenReturn("+22376123456");

        service.sendAbsenceAlert(parent(), student(), DATE, AttendanceStatus.ABSENT, null);

        MessageLog log = capturedLog();
        assertThat(log.getRecipientName()).isEqualTo("Koffi Ama");
        assertThat(log.getPhone()).isEqualTo("+22376123456");
        assertThat(log.getSubject()).isEqualTo("Absence signalée - Aïcha Kamdem");
        assertThat(log.getStudentId()).isEqualTo(10L);
        assertThat(log.getParentId()).isEqualTo(4L);
    }

    // ==================================================================
    // Contenu des autres messages
    // ==================================================================

    @Test
    void bulletinDisponible_composeLeMessageAvecLaMoyenneEtLaMention() {
        config.setEnabled(false);
        when(phoneNumberService.normalize("76 12 34 56")).thenReturn("+22376123456");

        service.sendBulletinAvailable(parent(), student(), Term.T2, new BigDecimal("12.75"), "Assez bien");

        MessageLog log = capturedLog();
        assertThat(log.getSubject()).isEqualTo("Bulletin disponible - Aïcha Kamdem");
        assertThat(log.getMessage())
                .contains("Aïcha Kamdem")
                .contains("ENS-2026-0001")
                .contains("T2")
                .contains("12.75")
                .contains("Assez bien");
    }

    @Test
    void convocation_composeLeMessageAvecMotifDateLieu() {
        config.setEnabled(false);
        when(phoneNumberService.normalize("76 12 34 56")).thenReturn("+22376123456");

        service.sendConvocation(parent(), student(), "Absences repetees", DATE,
                "08:30", "Bureau du directeur");

        MessageLog log = capturedLog();
        assertThat(log.getSubject()).isEqualTo("Convocation - Aïcha Kamdem");
        assertThat(log.getMessage())
                .contains("Absences repetees")
                .contains("2026-01-15")
                .contains("08:30")
                .contains("Bureau du directeur");
    }

    // ==================================================================
    // Relances de paiement (frais scolaires impayés ou en retard)
    // ==================================================================

    @Test
    void paymentReminderMessage_nommeLEleveLeSoldeEtLEcheance() {
        String body = service.paymentReminderMessage(student(), "FAC-2026-0001",
                new BigDecimal("30000"), "Scolarité", DATE, false);

        assertThat(body)
                .contains("FAC-2026-0001")
                .contains("Aïcha Kamdem")
                .contains("30000.00 FCFA")
                .contains("Scolarité")
                .contains("Échéance : 15/01/2026")
                .contains("Sahel Edu")
                .doesNotContain("échue");
    }

    @Test
    void paymentReminderMessage_annonceLeRetardQuandLEcheanceEstDepassee() {
        String body = service.paymentReminderMessage(student(), "FAC-2026-0001",
                new BigDecimal("30000"), "Scolarité", DATE, true);

        assertThat(body).contains("échue depuis le 15/01/2026");
    }

    /**
     * Un élève sans numéro est un cas normal : la relance élève est ignorée,
     * sans écrire de trace d'échec dans l'historique.
     */
    @Test
    void relanceEleve_sansNumeroNiEnvoiNiTraceDEchec() {
        config.setEnabled(true);

        boolean envoye = service.sendPaymentReminderToStudent(parent(), student(), "sujet", "corps");

        assertThat(envoye).isFalse();
        verifyNoInteractions(messageLogRepository);
        server.verify();
    }

    /** À défaut de numéro propre, l'élève est joint sur celui de son compte utilisateur. */
    @Test
    void relanceEleve_utiliseLeNumeroDeSonCompteUtilisateur() {
        config.setEnabled(false);
        Student student = student();
        User account = new User();
        account.setPhone("69 00 00 00");
        student.setUser(account);
        when(phoneNumberService.normalize("69 00 00 00")).thenReturn("+22369000000");

        boolean envoye = service.sendPaymentReminderToStudent(parent(), student, "sujet", "corps");

        assertThat(envoye).isTrue();
        MessageLog log = capturedLog();
        assertThat(log.getStatus()).isEqualTo(MessageLog.Status.SIMULATED);
        assertThat(log.getPhone()).isEqualTo("+22369000000");
        assertThat(log.getRecipientName()).isEqualTo("Aïcha Kamdem");
        assertThat(log.getStudentId()).isEqualTo(10L);
        assertThat(log.getParentId()).isEqualTo(4L);
    }
}
