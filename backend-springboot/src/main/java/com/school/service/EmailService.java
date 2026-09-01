package com.school.service;

import com.school.config.AppProperties;
import com.school.entity.Parent;
import com.school.entity.Student;
import com.school.enums.AttendanceStatus;
import com.school.enums.Term;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Envoi d'e-mails aux parents (bulletin disponible, absence signalée).
 * <p>
 * Nécessite la configuration SMTP (variables MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD).
 * Si l'adresse e-mail du parent est absente ou si l'envoi échoue, l'erreur est journalisée
 * sans interrompre le flux métier.
 */
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final AppProperties appProperties;

    public void sendBulletinAvailable(Parent parent, Student student, Term term,
                                      BigDecimal average, String mention) {
        if (parent.getEmail() == null || parent.getEmail().isBlank()) {
            log.info("[Email] Pas d'adresse e-mail pour {} {} — bulletin non envoyé",
                    parent.getFirstName(), parent.getLastName());
            return;
        }
        String subject = "Bulletin disponible - " + student.getFullName();
        String body = "Cher(e) " + parent.getFirstName() + ",\n\n"
                + "Le bulletin de notes de votre enfant " + student.getFullName()
                + " (" + student.getMatricule() + ") pour le trimestre " + term
                + " est désormais disponible dans l'espace parent.\n"
                + "Moyenne : " + average + "/20 - Mention : " + mention + "\n\n"
                + "Cordialement,\n" + appProperties.getSchool().getName();
        send(parent.getEmail(), subject, body);
    }

    public void sendAbsenceAlert(Parent parent, Student student, LocalDate date,
                                 AttendanceStatus status, String justification) {
        if (parent.getEmail() == null || parent.getEmail().isBlank()) {
            log.info("[Email] Pas d'adresse e-mail pour {} {} — absence non signalée",
                    parent.getFirstName(), parent.getLastName());
            return;
        }
        String subject = "Absence signalée - " + student.getFullName();
        String body = "Cher(e) " + parent.getFirstName() + ",\n\n"
                + "Votre enfant " + student.getFullName() + " (" + student.getMatricule() + ")"
                + " a été signalé " + (status == AttendanceStatus.ABSENT ? "absent" : "en retard")
                + " le " + date + "."
                + (justification != null && !justification.isBlank()
                ? "\nJustification : " + justification : "")
                + "\n\nCordialement,\n" + appProperties.getSchool().getName();
        send(parent.getEmail(), subject, body);
    }

    private void send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("[Email] Envoyé à {}", to);
        } catch (Exception e) {
            log.error("[Email] Échec d'envoi à {} : {}", to, e.getMessage());
        }
    }
}