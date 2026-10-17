package com.school.service;

import com.school.config.AppProperties;
import com.school.entity.MessageLog;
import com.school.entity.Parent;
import com.school.entity.Student;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Envoi de SMS aux parents (Twilio), avec repli en simulation.
 *
 * <p>Ce service ne doit <b>jamais</b> faire échouer le flux métier : absence d'élève, bulletin
 * disponible. Toutes les sorties d'erreur sont donc <b>silencieuses</b> du point de vue de l'appelant,
 * et seule la ligne {@link MessageLog} dit ce qui s'est réellement passé. C'est elle que les tests
 * inspectent.</p>
 *
 * <p>Invariant le plus subtil : la <b>même</b> branche produit deux statuts différents.
 * {@code if (!config.isEnabled() || phone == null)} couvre à la fois « service coupé » et « numéro
 * invalide », mais journalise {@code SIMULATED} + « Service SMS désactivé » dans le premier cas et
 * {@code FAILED} + « Numéro absent ou invalide » dans le second. Un numéro invalide ne doit pas être
 * présenté comme une simulation.</p>
 *
 * <p>Le transport est vérifié pour de vrai : {@link MockRestServiceServer} est branché sur le
 * {@link RestClient.Builder} injecté, donc l'URL Twilio, l'en-tête {@code Basic} et le corps
 * {@code application/x-www-form-urlencoded} sont assertés sans aucun appel réseau.</p>
 */
@ExtendWith(MockitoExtension.class)
class SmsServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 1, 15);

    @Mock
    private PhoneNumberService phoneNumberService;
    @Mock
    private MessageLogRepository messageLogRepository;

    private AppProperties appProperties;
    private AppProperties.Sms smsConfig;
    private MockRestServiceServer server;
    private SmsService service;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getSchool().setName("Sahel Edu");
        smsConfig = appProperties.getSms();

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new SmsService(appProperties, builder, phoneNumberService, messageLogRepository);
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

    /** Configure Twilio de façon complète et valide. */
    private void twilioConfigure() {
        smsConfig.setEnabled(true);
        smsConfig.setTwilioSid("AC123");
        smsConfig.setTwilioToken("secret");
        smsConfig.setTwilioFrom("+22300000000");
    }

    private MessageLog capturedLog() {
        ArgumentCaptor<MessageLog> cap = ArgumentCaptor.forClass(MessageLog.class);
        verify(messageLogRepository).save(cap.capture());
        return cap.getValue();
    }

    // ------------------------------------------------------------------
    // Repli : service desactive / numero invalide
    // ------------------------------------------------------------------

    @Test
    void serviceDesactive_journaliseUneSimulation() {
        smsConfig.setEnabled(false);
        when(phoneNumberService.normalize("76 12 34 56")).thenReturn("+22376123456");

        service.sendAbsenceAlert(parent(), student(), DATE, AttendanceStatus.ABSENT, null);

        MessageLog log = capturedLog();
        assertThat(log.getStatus()).isEqualTo(MessageLog.Status.SIMULATED);
        assertThat(log.getErrorMessage()).isEqualTo("Service SMS désactivé");
        assertThat(log.getChannel()).isEqualTo(MessageLog.Channel.SMS);
        server.verify();
    }

    @Test
    void numeroInvalide_serviceActif_journaliseUnEchec() {
        smsConfig.setEnabled(true);
        when(phoneNumberService.normalize("76 12 34 56")).thenReturn(null);

        service.sendAbsenceAlert(parent(), student(), DATE, AttendanceStatus.ABSENT, null);

        MessageLog log = capturedLog();
        assertThat(log.getStatus()).isEqualTo(MessageLog.Status.FAILED);
        assertThat(log.getErrorMessage()).isEqualTo("Numéro absent ou invalide");
        server.verify();
    }

    @Test
    void leStatutDependDeLaConfigurationEtPasSeulementDuNumero() {
        // Meme numero invalide dans les deux cas : seul l'etat du service change le statut.
        when(phoneNumberService.normalize("76 12 34 56")).thenReturn(null);

        smsConfig.setEnabled(false);
        service.sendAbsenceAlert(parent(), student(), DATE, AttendanceStatus.ABSENT, null);
        smsConfig.setEnabled(true);
        service.sendAbsenceAlert(parent(), student(), DATE, AttendanceStatus.ABSENT, null);

        ArgumentCaptor<MessageLog> cap = ArgumentCaptor.forClass(MessageLog.class);
        verify(messageLogRepository, times(2)).save(cap.capture());
        List<MessageLog> logs = cap.getAllValues();

        assertThat(logs.get(0).getStatus())
                .as("service coupe : simulation, pas un echec")
                .isEqualTo(MessageLog.Status.SIMULATED);
        assertThat(logs.get(1).getStatus())
                .as("service actif mais numero invalide : echec, pas une simulation")
                .isEqualTo(MessageLog.Status.FAILED);
    }

    // ------------------------------------------------------------------
    // Twilio : configuration incomplete, succes, erreur serveur
    // ------------------------------------------------------------------

    @Test
    void twilioIncomplet_journaliseUnEchecSansAppelerLAPI() {
        smsConfig.setEnabled(true);
        smsConfig.setTwilioSid("AC123");
        // token et from manquants
        when(phoneNumberService.normalize("76 12 34 56")).thenReturn("+22376123456");

        service.sendAbsenceAlert(parent(), student(), DATE, AttendanceStatus.ABSENT, null);

        MessageLog log = capturedLog();
        assertThat(log.getStatus()).isEqualTo(MessageLog.Status.FAILED);
        assertThat(log.getErrorMessage()).isEqualTo("Échec de l'envoi via Twilio SMS");
        server.verify();
    }

    @Test
    void twilioConfigure_envoieLaRequeteAttendueEtJournaliseLeSucces() {
        twilioConfigure();
        when(phoneNumberService.normalize("76 12 34 56")).thenReturn("+22376123456");
        String credentials = Base64.getEncoder().encodeToString("AC123:secret".getBytes());

        server.expect(requestTo("https://api.twilio.com/2010-04-01/Accounts/AC123/Messages.json"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic " + credentials))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("From=%2B22300000000")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("To=%2B22376123456")))
                .andRespond(withSuccess());

        service.sendAbsenceAlert(parent(), student(), DATE, AttendanceStatus.ABSENT, null);

        server.verify();
        MessageLog log = capturedLog();
        assertThat(log.getStatus()).isEqualTo(MessageLog.Status.SENT);
        assertThat(log.getErrorMessage()).isNull();
    }

    @Test
    void twilioEnErreur_journaliseUnEchecSansPropagerLException() {
        twilioConfigure();
        when(phoneNumberService.normalize("76 12 34 56")).thenReturn("+22376123456");

        server.expect(requestTo("https://api.twilio.com/2010-04-01/Accounts/AC123/Messages.json"))
                .andRespond(withServerError());

        assertThatCode(() -> service.sendAbsenceAlert(parent(), student(), DATE,
                AttendanceStatus.ABSENT, null)).doesNotThrowAnyException();

        server.verify();
        MessageLog log = capturedLog();
        assertThat(log.getStatus()).isEqualTo(MessageLog.Status.FAILED);
        assertThat(log.getErrorMessage()).isEqualTo("Échec de l'envoi via Twilio SMS");
    }

    @Test
    void uneErreurDeJournalisationNeFaitPasEchouerLEnvoi() {
        smsConfig.setEnabled(false);
        when(phoneNumberService.normalize("76 12 34 56")).thenReturn("+22376123456");
        when(messageLogRepository.save(any(MessageLog.class)))
                .thenThrow(new RuntimeException("base indisponible"));

        assertThatCode(() -> service.sendAbsenceAlert(parent(), student(), DATE,
                AttendanceStatus.ABSENT, null)).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------
    // Contenu du message
    // ------------------------------------------------------------------

    @Test
    void alerteAbsence_distinguelesStatuts() {
        smsConfig.setEnabled(false);
        when(phoneNumberService.normalize("76 12 34 56")).thenReturn("+22376123456");

        service.sendAbsenceAlert(parent(), student(), DATE, AttendanceStatus.ABSENT, null);

        assertThat(capturedLog().getMessage())
                .contains("Aïcha Kamdem")
                .contains("absent(e)")
                .contains("15/01/2026");
    }

    @Test
    void alerteRetard_utiliseLaFormulationRetard() {
        smsConfig.setEnabled(false);
        when(phoneNumberService.normalize("76 12 34 56")).thenReturn("+22376123456");

        service.sendAbsenceAlert(parent(), student(), DATE, AttendanceStatus.LATE, null);

        assertThat(capturedLog().getMessage()).contains("en retard").doesNotContain("absent(e)");
    }

    @Test
    void alerteAbsence_ajouteLaJustificationQuandElleEstFournie() {
        smsConfig.setEnabled(false);
        when(phoneNumberService.normalize("76 12 34 56")).thenReturn("+22376123456");

        service.sendAbsenceAlert(parent(), student(), DATE, AttendanceStatus.ABSENT,
                "Rendez-vous medical");

        assertThat(capturedLog().getMessage()).contains("Justification : Rendez-vous medical.");
    }

    @Test
    void alerteAbsence_ometLaJustificationQuandElleEstVide() {
        smsConfig.setEnabled(false);
        when(phoneNumberService.normalize("76 12 34 56")).thenReturn("+22376123456");

        service.sendAbsenceAlert(parent(), student(), DATE, AttendanceStatus.ABSENT, "   ");

        assertThat(capturedLog().getMessage()).doesNotContain("Justification");
    }

    @Test
    void alerteAbsence_tronqueUnMessageTropLongA2000Caracteres() {
        smsConfig.setEnabled(false);
        when(phoneNumberService.normalize("76 12 34 56")).thenReturn("+22376123456");

        service.sendAbsenceAlert(parent(), student(), DATE, AttendanceStatus.ABSENT, "x".repeat(2500));

        assertThat(capturedLog().getMessage())
                .as("la colonne message est un TEXT, mais l'historique reste borne a 2000")
                .hasSize(2000);
    }

    @Test
    void bulletinDisponible_composeLeMessageAvecLaMoyenneEtLaMention() {
        smsConfig.setEnabled(false);
        when(phoneNumberService.normalize("76 12 34 56")).thenReturn("+22376123456");

        service.sendBulletinAvailable(parent(), student(), Term.T1, new BigDecimal("14.50"), "Bien");

        assertThat(capturedLog().getMessage())
                .contains("Aïcha Kamdem")
                .contains("ENS-2026-0001")
                .contains("T1")
                .contains("14.50")
                .contains("Bien");
    }

    @Test
    void journaliseLeDestinataireEtLesIdentifiants() {
        smsConfig.setEnabled(false);
        when(phoneNumberService.normalize("76 12 34 56")).thenReturn("+22376123456");

        service.sendAbsenceAlert(parent(), student(), DATE, AttendanceStatus.ABSENT, null);

        MessageLog log = capturedLog();
        assertThat(log.getRecipientName()).isEqualTo("Koffi Ama");
        assertThat(log.getPhone()).isEqualTo("+22376123456");
        assertThat(log.getSubject()).isEqualTo("Absence signalée - Aïcha Kamdem");
        assertThat(log.getStudentId()).isEqualTo(10L);
        assertThat(log.getParentId()).isEqualTo(4L);
    }
}
