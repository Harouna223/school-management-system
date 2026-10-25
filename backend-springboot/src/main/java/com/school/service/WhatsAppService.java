package com.school.service;

import com.school.config.AppProperties;
import com.school.entity.MessageLog;
import com.school.entity.Parent;
import com.school.entity.Student;
import com.school.enums.AttendanceStatus;
import com.school.enums.Term;
import com.school.repository.MessageLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;

/**
 * Envoi de notifications WhatsApp (bulletins disponibles, absences signalées,
 * convocations, relances de paiement au parent <em>et</em> à l'élève).
 * <p>
 * Fournisseurs supportés :
 * <ul>
 *   <li>meta : WhatsApp Cloud API (Meta) — nécessite un token d'accès et un Phone ID</li>
 *   <li>twilio : API Twilio WhatsApp — nécessite SID, token d'authentification et numéro From</li>
 * </ul>
 * Si le service est désactivé ou mal configuré, les messages sont journalisés
 * (mode simulation) afin de ne jamais bloquer le flux métier.
 * <p>
 * Chaque tentative d'envoi est enregistrée dans l'historique (table message_logs).
 */
@Service
@RequiredArgsConstructor
public class WhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppService.class);
    private static final String META_URL = "https://graph.facebook.com/v21.0/";
    private static final String TWILIO_URL = "https://api.twilio.com/2010-04-01/Accounts/";

    private final AppProperties appProperties;
    private final RestClient.Builder restClientBuilder;
    private final PhoneNumberService phoneNumberService;
    private final MessageLogRepository messageLogRepository;

    /**
     * Message WhatsApp : bulletin disponible.
     */
    public void sendBulletinAvailable(Parent parent, Student student, Term term,
                                      BigDecimal average, String mention) {
        String body = "Bonjour " + parent.getFirstName() + " " + parent.getLastName() + ",\n"
                + "Le bulletin de notes de votre enfant " + student.getFullName()
                + " (" + student.getMatricule() + ") pour le trimestre " + term
                + " est disponible (moyenne " + average + "/20, mention " + mention + ").\n\n"
                + appProperties.getSchool().getName();
        send(parent, student, "Bulletin disponible - " + student.getFullName(), body);
    }

    /**
     * Message WhatsApp : absence ou retard signalé.
     */
    public void sendAbsenceAlert(Parent parent, Student student, LocalDate date,
                                 AttendanceStatus status, String justification) {
        send(parent, student, "Absence signalée - " + student.getFullName(),
                absenceMessage(parent, student, date, status, justification));
    }

    /**
     * Message WhatsApp : convocation (scolaire ou universitaire).
     */
    public void sendConvocation(Parent parent, Student student, String subject,
                                LocalDate date, String time, String location) {
        String body = "Bonjour " + parent.getFirstName() + " " + parent.getLastName() + ",\n"
                + "Une convocation est adressée à votre enfant " + student.getFullName()
                + " (" + student.getMatricule() + ").\n"
                + "Motif : " + subject + "\n"
                + "Date : " + date + " à " + time + "\n"
                + "Lieu : " + location + "\n\n"
                + appProperties.getSchool().getName();
        send(parent, student, "Convocation - " + student.getFullName(), body);
    }

    /**
     * Message WhatsApp : relance de paiement adressée au parent (tuteur légal).
     *
     * @return {@code true} si le message a été remis au fournisseur (ou journalisé
     *         en mode simulation)
     */
    public boolean sendPaymentReminder(Parent parent, Student student, String subject, String body) {
        send(parent, student, subject, body);
        return true;
    }

    /**
     * Message WhatsApp : relance de paiement adressée directement à l'élève.
     *
     * <p>Le numéro utilisé est celui de l'élève, à défaut celui de son compte
     * utilisateur. Un élève sans numéro est un cas <strong>normal</strong>
     * (contrairement à un numéro invalide) : aucune trace d'échec n'est écrite,
     * et la relance au parent reste envoyée.</p>
     *
     * @return {@code true} si un message a été remis au fournisseur
     */
    public boolean sendPaymentReminderToStudent(Parent parent, Student student, String subject, String body) {
        String phone = studentPhone(student);
        if (phone == null) {
            log.info("[WhatsApp] Aucun numéro pour l'élève {} {} — relance élève ignorée",
                    student.getFirstName(), student.getLastName());
            return false;
        }
        deliver(student.getFullName(), phone, student.getId(),
                parent != null ? parent.getId() : null, subject, body);
        return true;
    }

    /** Numéro joignable de l'élève : son numéro, sinon celui de son compte utilisateur. */
    private String studentPhone(Student student) {
        String raw = student.getPhone();
        if ((raw == null || raw.isBlank()) && student.getUser() != null) {
            raw = student.getUser().getPhone();
        }
        return phoneNumberService.normalize(raw);
    }

    /**
     * Corps du message de relance de paiement. Identique pour le parent et pour
     * l'élève : le texte désigne l'élève concerné, il est donc lisible par les deux.
     *
     * @param overdue {@code true} si l'échéance est déjà dépassée (facture en retard)
     */
    public String paymentReminderMessage(Student student, String invoiceNo, BigDecimal remaining,
                                         String feeTypeName, LocalDate dueDate, boolean overdue) {
        StringBuilder sb = new StringBuilder("Bonjour,\n");
        sb.append("La facture ").append(invoiceNo);
        if (student != null) {
            sb.append(" de l'élève ").append(student.getFullName())
                    .append(" (").append(student.getMatricule()).append(")");
        }
        sb.append(" (").append(feeTypeName).append(")")
                .append(" présente un solde restant de ").append(amountText(remaining)).append(".");
        if (dueDate != null) {
            String dateStr = dueDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            sb.append(overdue
                    ? " Elle est échue depuis le " + dateStr + "."
                    : " Échéance : " + dateStr + ".");
        }
        sb.append("\nMerci de régulariser auprès de l'établissement.\n\n")
                .append(appProperties.getSchool().getName());
        return sb.toString();
    }

    /**
     * Montant lisible : échelle 2 et notation décimale, comme le reste de
     * l'application (jamais la notation scientifique de {@code stripTrailingZeros}).
     */
    private static String amountText(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value)
                .setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + " FCFA";
    }

    /**
     * Corps du message d'absence envoyé aux parents (réutilisé pour le lien wa.me).
     */
    public String absenceMessage(Parent parent, Student student, LocalDate date,
                                 AttendanceStatus status, String justification) {
        String dateStr = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String statusText = status == AttendanceStatus.ABSENT ? "absent(e)" : "en retard";
        return "Bonjour " + parent.getFirstName() + " " + parent.getLastName() + ",\n"
                + "Votre enfant " + student.getFullName() + " a été signalé(e) " + statusText
                + " le " + dateStr + "."
                + (justification != null && !justification.isBlank()
                ? " Justification : " + justification + "." : "")
                + " Merci de contacter l'établissement si besoin.\n\n"
                + appProperties.getSchool().getName();
    }

    /** Envoi au parent (comportement historique : la relance est portée par le tuteur). */
    private void send(Parent parent, Student student, String subject, String body) {
        deliver(parent.getFirstName() + " " + parent.getLastName(),
                phoneNumberService.normalize(parent.getPhone()),
                student != null ? student.getId() : null,
                parent.getId(), subject, body);
    }

    /**
     * Envoi WhatsApp générique : choisit le mode (simulation / Meta / Twilio),
     * journalise la tentative dans {@code message_logs} et ne propage jamais
     * d'exception au flux métier.
     *
     * @param studentId identifiant de l'élève rattaché (facultatif)
     * @param parentId  identifiant du parent rattaché (facultatif)
     */
    private void deliver(String recipientName, String phone, Long studentId, Long parentId,
                         String subject, String body) {
        AppProperties.WhatsApp config = appProperties.getWhatsapp();
        if (!config.isEnabled()) {
            log.info("[WhatsApp] Simulation (désactivé) pour {} -> {}\n{}", recipientName, phone, body);
            logMessage(recipientName, parentId, studentId, subject, body, phone,
                    MessageLog.Channel.WHATSAPP, MessageLog.Status.SIMULATED, null);
            return;
        }
        if (phone == null) {
            log.warn("[WhatsApp] Numéro invalide pour {} — message non envoyé", recipientName);
            logMessage(recipientName, parentId, studentId, subject, body, null,
                    MessageLog.Channel.WHATSAPP, MessageLog.Status.FAILED,
                    "Numéro de téléphone absent ou invalide");
            return;
        }
        boolean success;
        if ("twilio".equalsIgnoreCase(config.getProvider())) {
            success = sendViaTwilio(config, phone, body);
        } else {
            success = sendViaMeta(config, phone, body);
        }
        logMessage(recipientName, parentId, studentId, subject, body, phone,
                MessageLog.Channel.WHATSAPP,
                success ? MessageLog.Status.SENT : MessageLog.Status.FAILED,
                success ? null : "Échec de l'envoi via " + config.getProvider());
    }

    private boolean sendViaMeta(AppProperties.WhatsApp config, String phone, String body) {
        try {
            if (config.getMetaToken() == null || config.getMetaToken().isBlank()
                    || config.getMetaPhoneId() == null || config.getMetaPhoneId().isBlank()) {
                log.warn("[WhatsApp] Meta activé mais token/phone-id manquants — message non envoyé à {}",
                        phone);
                return false;
            }
            restClientBuilder.build()
                    .post()
                    .uri(META_URL + config.getMetaPhoneId() + "/messages")
                    .header("Authorization", "Bearer " + config.getMetaToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "messaging_product", "whatsapp",
                            "to", phone,
                            "type", "text",
                            "text", Map.of("body", body)))
                    .retrieve()
                    .toBodilessEntity();
            log.info("[WhatsApp] Meta : message envoyé à {}", phone);
            return true;
        } catch (Exception e) {
            log.error("[WhatsApp] Meta : échec d'envoi à {} : {}", phone, e.getMessage());
            return false;
        }
    }

    private boolean sendViaTwilio(AppProperties.WhatsApp config, String phone, String body) {
        try {
            if (config.getTwilioSid() == null || config.getTwilioSid().isBlank()
                    || config.getTwilioToken() == null || config.getTwilioToken().isBlank()
                    || config.getTwilioFrom() == null || config.getTwilioFrom().isBlank()) {
                log.warn("[WhatsApp] Twilio activé mais SID/token/from manquants — message non envoyé à {}",
                        phone);
                return false;
            }
            String credentials = Base64.getEncoder()
                    .encodeToString((config.getTwilioSid() + ":" + config.getTwilioToken()).getBytes());
            String form = "From=" + urlEncode("whatsapp:" + config.getTwilioFrom())
                    + "&To=" + urlEncode("whatsapp:" + phone)
                    + "&Body=" + urlEncode(body);
            restClientBuilder.build()
                    .post()
                    .uri(TWILIO_URL + config.getTwilioSid() + "/Messages.json")
                    .header("Authorization", "Basic " + credentials)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            log.info("[WhatsApp] Twilio : message envoyé à {}", phone);
            return true;
        } catch (Exception e) {
            log.error("[WhatsApp] Twilio : échec d'envoi à {} : {}", phone, e.getMessage());
            return false;
        }
    }

    private void logMessage(String recipientName, Long parentId, Long studentId, String subject,
                            String body, String phone, MessageLog.Channel channel,
                            MessageLog.Status status, String errorMessage) {
        try {
            messageLogRepository.save(MessageLog.builder()
                    .channel(channel)
                    .recipientName(recipientName)
                    .phone(phone)
                    .subject(subject)
                    .message(body != null && body.length() > 2000 ? body.substring(0, 2000) : body)
                    .status(status)
                    .errorMessage(errorMessage)
                    .studentId(studentId)
                    .parentId(parentId)
                    .build());
        } catch (Exception e) {
            log.warn("[WhatsApp] Impossible d'enregistrer l'historique : {}", e.getMessage());
        }
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
