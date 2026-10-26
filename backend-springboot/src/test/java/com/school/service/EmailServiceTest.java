package com.school.service;

import com.school.config.AppProperties;
import com.school.entity.Parent;
import com.school.entity.Student;
import com.school.enums.AttendanceStatus;
import com.school.enums.Term;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Envoi d'e-mails aux parents : bulletin disponible et alerte d'absence.
 *
 * <p>Ce service n'avait aucun test alors qu'il porte un contrat important et
 * silencieux : <b>un échec d'envoi ne doit jamais interrompre le flux métier</b>.
 * L'appelant (validation de bulletin, pointage d'absence) travaille en base ;
 * si une panne SMTP faisait remonter l'exception, l'opération serait annulée
 * pour une raison sans rapport. Personne ne le verrait en CI, et en production
 * cela se traduirait par « la validation du bulletin ne marche pas ».</p>
 *
 * <p>L'autre moitié du contrat est l'inverse : quand il n'y a pas d'adresse, on
 * ne doit <b>rien</b> envoyer — pas un message vide, pas une exception.</p>
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    private static final String ECOLE = "Sahel Edu";

    @Mock private JavaMailSender mailSender;

    private EmailService service;

    @BeforeEach
    void setUp() {
        /*
         * AppProperties est instanciée pour de vrai plutôt que mockée : c'est un
         * simple POJO à setters, et un mock imposerait de stubber getSchool() dans
         * chaque test. Sous les strict stubs de Mockito, un stub inutilisé (cas de
         * l'adresse absente, qui sort avant de lire le nom de l'école) ferait
         * échouer le test pour une mauvaise raison.
         */
        AppProperties properties = new AppProperties();
        properties.getSchool().setName(ECOLE);
        service = new EmailService(mailSender, properties);
    }

    private static Parent parent(String email) {
        return Parent.builder().id(4L).firstName("Harouna").lastName("Siby").email(email).build();
    }

    private static Student eleve() {
        return Student.builder()
                .id(10L)
                .matricule("ETU-2026-000008")
                .firstName("Idrissa")
                .lastName("Siby")
                .build();
    }

    /** Capture l'unique message remis au transport SMTP. */
    private SimpleMailMessage captureEnvoi() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("Bulletin disponible")
    class Bulletin {

        @Test
        @DisplayName("envoie un message au parent, avec l'élève et les résultats dans le corps")
        void envoieLeBulletin() {
            service.sendBulletinAvailable(parent("harouna@example.com"), eleve(), Term.T1,
                    new BigDecimal("14.14"), "Bien");

            SimpleMailMessage message = captureEnvoi();
            assertThat(message.getTo()).containsExactly("harouna@example.com");
            assertThat(message.getSubject()).contains("Idrissa Siby");
            assertThat(message.getText())
                    .contains("Idrissa Siby")
                    .contains("ETU-2026-000008")
                    .contains("T1")
                    .contains("14.14")
                    .contains("Bien")
                    .contains(ECOLE);
        }

        @Test
        @DisplayName("n'envoie rien quand le parent n'a pas d'adresse (null)")
        void sansAdresse() {
            service.sendBulletinAvailable(parent(null), eleve(), Term.T1,
                    new BigDecimal("12.00"), "Passable");

            verifyNoInteractions(mailSender);
        }

        @Test
        @DisplayName("n'envoie rien quand l'adresse est blanche")
        void adresseBlanche() {
            // « Blanc » et non « vide » : une chaîne d'espaces vient d'un formulaire
            // mal nettoyé et ne doit pas produire un envoi vers une adresse invalide.
            service.sendBulletinAvailable(parent("   "), eleve(), Term.T2,
                    new BigDecimal("12.00"), "Passable");

            verifyNoInteractions(mailSender);
        }

        @Test
        @DisplayName("une panne SMTP ne remonte pas à l'appelant")
        void panneSmtpAvallee() {
            doThrow(new MailSendException("SMTP indisponible")).when(mailSender).send(any(SimpleMailMessage.class));

            // Le contrat : la validation du bulletin ne doit pas échouer parce que
            // le serveur de mail est en panne. L'erreur est journalisée, point.
            assertThatCode(() -> service.sendBulletinAvailable(parent("harouna@example.com"), eleve(),
                    Term.T1, new BigDecimal("14.14"), "Bien"))
                    .doesNotThrowAnyException();

            verify(mailSender).send(any(SimpleMailMessage.class));
        }
    }

    @Nested
    @DisplayName("Alerte d'absence")
    class Absence {

        @Test
        @DisplayName("« absent » pour une absence, « en retard » pour un retard")
        void distingueAbsenceEtRetard() {
            service.sendAbsenceAlert(parent("harouna@example.com"), eleve(),
                    LocalDate.of(2026, 10, 24), AttendanceStatus.ABSENT, null);
            assertThat(captureEnvoi().getText()).contains("absent");

            // Second envoi indépendant : on réinitialise le compteur d'invocations.
            clearInvocations(mailSender);
            service.sendAbsenceAlert(parent("harouna@example.com"), eleve(),
                    LocalDate.of(2026, 10, 24), AttendanceStatus.LATE, null);
            assertThat(captureEnvoi().getText()).contains("en retard");
        }

        @Test
        @DisplayName("reprend la justification quand elle existe")
        void avecJustification() {
            service.sendAbsenceAlert(parent("harouna@example.com"), eleve(),
                    LocalDate.of(2026, 10, 24), AttendanceStatus.ABSENT, "Rendez-vous médical");

            assertThat(captureEnvoi().getText()).contains("Justification : Rendez-vous médical");
        }

        @Test
        @DisplayName("n'affiche pas de rubrique justification quand elle est absente ou blanche")
        void sansJustification() {
            service.sendAbsenceAlert(parent("harouna@example.com"), eleve(),
                    LocalDate.of(2026, 10, 24), AttendanceStatus.ABSENT, null);
            assertThat(captureEnvoi().getText()).doesNotContain("Justification");

            clearInvocations(mailSender);
            // Une justification faite d'espaces ne doit pas produire « Justification :    ».
            service.sendAbsenceAlert(parent("harouna@example.com"), eleve(),
                    LocalDate.of(2026, 10, 24), AttendanceStatus.ABSENT, "   ");
            assertThat(captureEnvoi().getText()).doesNotContain("Justification");
        }

        @Test
        @DisplayName("la date et le nom de l'établissement figurent dans le corps")
        void dateEtEtablissement() {
            service.sendAbsenceAlert(parent("harouna@example.com"), eleve(),
                    LocalDate.of(2026, 10, 24), AttendanceStatus.LATE, null);

            assertThat(captureEnvoi().getText())
                    .contains("2026-10-24")
                    .contains(ECOLE);
        }

        @Test
        @DisplayName("n'envoie rien sans adresse, et ne lève pas")
        void sansAdresse() {
            assertThatCode(() -> service.sendAbsenceAlert(parent(null), eleve(),
                    LocalDate.of(2026, 10, 24), AttendanceStatus.ABSENT, null))
                    .doesNotThrowAnyException();

            verifyNoInteractions(mailSender);
        }

        @Test
        @DisplayName("une panne SMTP ne remonte pas à l'appelant")
        void panneSmtpAvallee() {
            doThrow(new MailSendException("SMTP indisponible")).when(mailSender).send(any(SimpleMailMessage.class));

            assertThatCode(() -> service.sendAbsenceAlert(parent("harouna@example.com"), eleve(),
                    LocalDate.of(2026, 10, 24), AttendanceStatus.ABSENT, null))
                    .doesNotThrowAnyException();
        }
    }
}
