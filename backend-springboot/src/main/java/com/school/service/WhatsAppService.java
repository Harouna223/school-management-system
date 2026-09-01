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
 * Envoi de notifications WhatsApp aux parents (bulletins disponibles, absences signalées).
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

    private void send(Parent parent, Student student, String subject, String body) {
        String phone = phoneNumberService.normalize(parent.getPhone());
        AppProperties.WhatsApp config = appProperties.getWhatsapp();
        if (!config.isEnabled()) {
            log.info("[WhatsApp] Simulation (désactivé) pour {} {} -> {}\n{}",
                    parent.getFirstName(), parent.getLastName(), phone, body);
            logMessage(parent, student, subject, body, phone, MessageLog.Channel.WHATSAPP,
                    MessageLog.Status.SIMULATED, null);
            return;
        }
        if (phone == null) {
            log.warn("[WhatsApp] Numéro invalide pour {} {} — message non envoyé",
                    parent.getFirstName(), parent.getLastName());
            logMessage(parent, student, subject, body, null, MessageLog.Channel.WHATSAPP,
                    MessageLog.Status.FAILED, "Numéro de téléphone absent ou invalide");
            return;
        }
        boolean success;
        if ("twilio".equalsIgnoreCase(config.getProvider())) {
            success = sendViaTwilio(config, phone, body);
        } else {
            success = sendViaMeta(config, phone, body);
        }
        logMessage(parent, student, subject, body, phone, MessageLog.Channel.WHATSAPP,
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

    private void logMessage(Parent parent, Student student, String subject, String body, String phone,
                            MessageLog.Channel channel, MessageLog.Status status, String errorMessage) {
        try {
            messageLogRepository.save(MessageLog.builder()
                    .channel(channel)
                    .recipientName(parent.getFirstName() + " " + parent.getLastName())
                    .phone(phone)
                    .subject(subject)
                    .message(body != null && body.length() > 2000 ? body.substring(0, 2000) : body)
                    .status(status)
                    .errorMessage(errorMessage)
                    .studentId(student != null ? student.getId() : null)
                    .parentId(parent.getId())
                    .build());
        } catch (Exception e) {
            log.warn("[WhatsApp] Impossible d'enregistrer l'historique : {}", e.getMessage());
        }
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
