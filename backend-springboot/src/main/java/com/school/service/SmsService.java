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
import java.util.Base64;

/**
 * Envoi de SMS aux parents (bulletin disponible, absence signalée).
 * <p>
 * Fournisseur supporté : Twilio SMS. Si le service est désactivé ou mal configuré,
 * les messages sont journalisés (mode simulation) sans interrompre le flux métier.
 */
@Service
@RequiredArgsConstructor
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);
    private static final String TWILIO_URL = "https://api.twilio.com/2010-04-01/Accounts/";

    private final AppProperties appProperties;
    private final RestClient.Builder restClientBuilder;
    private final PhoneNumberService phoneNumberService;
    private final MessageLogRepository messageLogRepository;

    public void sendBulletinAvailable(Parent parent, Student student, Term term,
                                      BigDecimal average, String mention) {
        String body = "Bulletin disponible - " + student.getFullName() + " ("
                + student.getMatricule() + "), trimestre " + term + " : moyenne "
                + average + "/20, mention " + mention + ". Consultez l'espace parent.";
        send(parent, student, body);
    }

    public void sendAbsenceAlert(Parent parent, Student student, LocalDate date,
                                 AttendanceStatus status, String justification) {
        String dateStr = date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String body = "Bonjour " + parent.getFirstName() + " " + parent.getLastName() + ", "
                + "votre enfant " + student.getFullName() + " a ete signale(e) "
                + (status == AttendanceStatus.ABSENT ? "absent(e)" : "en retard")
                + " le " + dateStr + "."
                + (justification != null && !justification.isBlank()
                ? " Justification : " + justification + "." : "")
                + " Merci de contacter l'etablissement si besoin. "
                + appProperties.getSchool().getName();
        send(parent, student, body);
    }

    private void send(Parent parent, Student student, String body) {
        String phone = phoneNumberService.normalize(parent.getPhone());
        AppProperties.Sms config = appProperties.getSms();
        if (!config.isEnabled() || phone == null) {
            log.info("[SMS] Simulation (désactivé) pour {} {} -> {}\n{}",
                    parent.getFirstName(), parent.getLastName(), phone, body);
            logMessage(parent, student, body, phone,
                    config.isEnabled() ? MessageLog.Status.FAILED : MessageLog.Status.SIMULATED,
                    config.isEnabled() ? "Numéro absent ou invalide" : "Service SMS désactivé");
            return;
        }
        boolean success = sendViaTwilio(config, phone, body);
        logMessage(parent, student, body, phone,
                success ? MessageLog.Status.SENT : MessageLog.Status.FAILED,
                success ? null : "Échec de l'envoi via Twilio SMS");
    }

    private void logMessage(Parent parent, Student student, String body, String phone,
                            MessageLog.Status status, String errorMessage) {
        try {
            messageLogRepository.save(MessageLog.builder()
                    .channel(MessageLog.Channel.SMS)
                    .recipientName(parent.getFirstName() + " " + parent.getLastName())
                    .phone(phone)
                    .subject("Absence signalée - " + student.getFullName())
                    .message(body != null && body.length() > 2000 ? body.substring(0, 2000) : body)
                    .status(status)
                    .errorMessage(errorMessage)
                    .studentId(student.getId())
                    .parentId(parent.getId())
                    .build());
        } catch (Exception e) {
            log.warn("[SMS] Impossible d'enregistrer l'historique : {}", e.getMessage());
        }
    }

    private boolean sendViaTwilio(AppProperties.Sms config, String phone, String body) {
        try {
            if (config.getTwilioSid() == null || config.getTwilioSid().isBlank()
                    || config.getTwilioToken() == null || config.getTwilioToken().isBlank()
                    || config.getTwilioFrom() == null || config.getTwilioFrom().isBlank()) {
                log.warn("[SMS] Twilio activé mais SID/token/from manquants — message non envoyé à {}",
                        phone);
                return false;
            }
            String credentials = Base64.getEncoder()
                    .encodeToString((config.getTwilioSid() + ":" + config.getTwilioToken()).getBytes());
            String form = "From=" + urlEncode(config.getTwilioFrom())
                    + "&To=" + urlEncode(phone)
                    + "&Body=" + urlEncode(body);
            restClientBuilder.build()
                    .post()
                    .uri(TWILIO_URL + config.getTwilioSid() + "/Messages.json")
                    .header("Authorization", "Basic " + credentials)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            log.info("[SMS] Twilio : message envoyé à {}", phone);
            return true;
        } catch (Exception e) {
            log.error("[SMS] Twilio : échec d'envoi à {} : {}", phone, e.getMessage());
            return false;
        }
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}