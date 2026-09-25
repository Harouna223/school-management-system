package com.school.service;

import com.school.dto.request.AttendanceRequest;
import com.school.dto.request.TeacherAttendanceRequest;
import com.school.dto.response.AttendanceRecordResponse;
import com.school.dto.response.AttendanceReportRow;
import com.school.dto.response.AttendanceResponse;
import com.school.dto.response.TeacherAttendanceResponse;
import com.school.dto.response.WhatsappAlert;
import com.school.entity.Attendance;
import com.school.entity.Parent;
import com.school.entity.SchoolClass;
import com.school.entity.Student;
import com.school.entity.Teacher;
import com.school.entity.TeacherAttendance;
import com.school.entity.User;
import com.school.enums.AttendanceStatus;
import com.school.enums.ContractType;
import com.school.enums.Gender;
import com.school.enums.NotificationType;
import com.school.enums.TeacherStatus;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.AttendanceRepository;
import com.school.repository.NotificationRepository;
import com.school.repository.StudentRepository;
import com.school.repository.TeacherAttendanceRepository;
import com.school.repository.TeacherRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Module presences : pointage, notifications parentales, justification, rapports.
 *
 * L'invariant qui compte n'est pas l'ecriture du pointage (un upsert banal) mais
 * <b>qui est notifie</b> : les presences valides ne doivent declencher AUCUN message, et un
 * absent doit declencher les quatre canaux (in-app, WhatsApp, email, SMS). Une erreur ici
 * envoie des dizaines de SMS a des familles non concernees — ou, dans l'autre sens, laisse
 * une absence non signalee.
 *
 * ⚠️ La garde « ne pas notifier » est ecrite <b>deux fois</b>, volontairement :
 * <ol>
 *   <li>une porte exterieure dans {@code record()} — n'appelle {@code notifyParents} que si
 *       au moins une entree est ABSENT ou LATE ;</li>
 *   <li>un filtre interieur dans {@code notifyParents()} — ignore toute entree qui n'est ni
 *       ABSENT ni LATE.</li>
 * </ol>
 * Les deux sont redondants : neutraliser <b>une seule</b> des deux ne fait echouer
 * <b>aucun</b> test (constate), parce que l'autre suffit. C'est de la defense en profondeur,
 * pas un defaut — mais cela signifie qu'un controle negatif doit casser <b>les deux</b> a la
 * fois pour valider les tests « ne rien envoyer ». Casser les deux fait echouer exactement
 * 3 tests (`aucunMessageQuandToutLeMondeEstPresent`, `uneJustificationNeDeclencheRien`,
 * `aucuneAlertePourUnPresent`).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceServiceTest {

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private TeacherAttendanceRepository teacherAttendanceRepository;

    @Mock
    private TeacherRepository teacherRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private WhatsAppService whatsappService;

    @Mock
    private EmailService emailService;

    @Mock
    private SmsService smsService;

    @Mock
    private PhoneNumberService phoneNumberService;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private AttendanceService attendanceService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------ helpers

    private final LocalDate jour = LocalDate.of(2026, 10, 5);

    private SchoolClass classe(Long id) {
        return SchoolClass.builder().id(id).name("6eme A").build();
    }

    private Student eleve(Long id, Long classId, Parent parent) {
        return Student.builder()
                .id(id)
                .matricule("ETU-2026-00000" + id)
                .firstName("Aicha")
                .lastName("Kamdem")
                .gender(Gender.FEMALE)
                .enrollmentDate(LocalDate.of(2026, 9, 1))
                .status(com.school.enums.StudentStatus.ACTIVE)
                .schoolClass(classId != null ? classe(classId) : null)
                .parent(parent)
                .build();
    }

    private Parent parent(Long id, User compte) {
        return Parent.builder().id(id).firstName("Harouna").lastName("Siby")
                .phone("66000000").email("h.siby@school.td").user(compte).build();
    }

    private Teacher enseignant(Long id) {
        return Teacher.builder()
                .id(id)
                .employeeNo("ENS-2026-0001")
                .firstName("Jean")
                .lastName("Kamdem")
                .gender(Gender.MALE)
                .hireDate(LocalDate.of(2026, 1, 1))
                .contractType(ContractType.CDI)
                .salary(new BigDecimal("250000.00"))
                .status(TeacherStatus.ACTIVE)
                .build();
    }

    private AttendanceRequest.Entry entree(Long studentId, AttendanceStatus statut) {
        return AttendanceRequest.Entry.builder().studentId(studentId).status(statut).build();
    }

    private AttendanceRequest.Entry entree(Long studentId, AttendanceStatus statut, String justification) {
        return AttendanceRequest.Entry.builder()
                .studentId(studentId).status(statut).justification(justification).build();
    }

    private AttendanceRequest requete(Long classId, AttendanceRequest.Entry... entries) {
        return AttendanceRequest.builder()
                .classId(classId).date(jour).entries(List.of(entries)).build();
    }

    private void saveAttendanceRenvoieSonArgument() {
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void connecte(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        User.builder().id(userId).username("prof1").build(), null, List.of()));
    }

    // ------------------------------------------------------------------ pointage

    @Test
    @DisplayName("record cree une presence par entree et renvoie les enregistrements")
    void recordCreeUnePresenceParEntree() {
        Student a = eleve(6L, 3L, null);
        Student b = eleve(7L, 3L, null);
        when(studentRepository.findById(6L)).thenReturn(Optional.of(a));
        when(studentRepository.findById(7L)).thenReturn(Optional.of(b));
        when(attendanceRepository.findByStudentIdAndDate(anyLong(), eq(jour))).thenReturn(Optional.empty());
        saveAttendanceRenvoieSonArgument();

        AttendanceRecordResponse reponse = attendanceService.record(
                requete(3L, entree(6L, AttendanceStatus.PRESENT), entree(7L, AttendanceStatus.PRESENT)),
                httpRequest);

        assertThat(reponse.getAttendance()).hasSize(2);
        verify(attendanceRepository, times(2)).save(any(Attendance.class));
    }

    @Test
    @DisplayName("record reutilise la presence existante du jour au lieu d'en creer une seconde")
    void recordReutiliseLaPresenceExistante() {
        Student a = eleve(6L, 3L, null);
        when(studentRepository.findById(6L)).thenReturn(Optional.of(a));
        Attendance existante = Attendance.builder()
                .id(99L).student(a).schoolClass(classe(3L)).date(jour)
                .status(AttendanceStatus.PRESENT).build();
        when(attendanceRepository.findByStudentIdAndDate(6L, jour)).thenReturn(Optional.of(existante));
        saveAttendanceRenvoieSonArgument();

        AttendanceRecordResponse reponse = attendanceService.record(
                requete(3L, entree(6L, AttendanceStatus.LATE, "Bus en retard")), httpRequest);

        // Upsert : c'est le meme enregistrement, mis a jour — pas un doublon.
        assertThat(reponse.getAttendance()).hasSize(1);
        assertThat(reponse.getAttendance().get(0).getId()).isEqualTo(99L);
        assertThat(reponse.getAttendance().get(0).getStatus()).isEqualTo(AttendanceStatus.LATE);
        assertThat(reponse.getAttendance().get(0).getJustification()).isEqualTo("Bus en retard");
        verify(attendanceRepository, times(1)).save(any(Attendance.class));
    }

    @Test
    @DisplayName("record rattache la presence a la classe de l'eleve, pas a celle de la requete")
    void recordRattacheLaClasseDeLEleve() {
        Student a = eleve(6L, 8L, null);
        when(studentRepository.findById(6L)).thenReturn(Optional.of(a));
        when(attendanceRepository.findByStudentIdAndDate(anyLong(), eq(jour))).thenReturn(Optional.empty());
        saveAttendanceRenvoieSonArgument();

        ArgumentCaptor<Attendance> captor = ArgumentCaptor.forClass(Attendance.class);
        attendanceService.record(requete(3L, entree(6L, AttendanceStatus.PRESENT)), httpRequest);
        verify(attendanceRepository).save(captor.capture());

        assertThat(captor.getValue().getSchoolClass().getId()).isEqualTo(8L);
    }

    @Test
    @DisplayName("record renseigne recordedBy avec l'utilisateur authentifie")
    void recordRenseigneRecordedBy() {
        connecte(4L);
        Student a = eleve(6L, 3L, null);
        when(studentRepository.findById(6L)).thenReturn(Optional.of(a));
        when(attendanceRepository.findByStudentIdAndDate(anyLong(), eq(jour))).thenReturn(Optional.empty());
        saveAttendanceRenvoieSonArgument();

        ArgumentCaptor<Attendance> captor = ArgumentCaptor.forClass(Attendance.class);
        attendanceService.record(requete(3L, entree(6L, AttendanceStatus.PRESENT)), httpRequest);
        verify(attendanceRepository).save(captor.capture());

        assertThat(captor.getValue().getRecordedBy()).isNotNull();
        assertThat(captor.getValue().getRecordedBy().getId()).isEqualTo(4L);
    }

    @Test
    @DisplayName("record refuse un eleve inconnu")
    void recordRefuseUnEleveInconnu() {
        when(studentRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> attendanceService.record(
                requete(3L, entree(404L, AttendanceStatus.PRESENT)), httpRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Élève")
                .hasMessageContaining("404");
    }

    @Test
    @DisplayName("record journalise le decompte d'absents et de retards")
    void recordJournaliseLeDecompte() {
        Student a = eleve(6L, 3L, null);
        Student b = eleve(7L, 3L, null);
        Student c = eleve(8L, 3L, null);
        when(studentRepository.findById(6L)).thenReturn(Optional.of(a));
        when(studentRepository.findById(7L)).thenReturn(Optional.of(b));
        when(studentRepository.findById(8L)).thenReturn(Optional.of(c));
        when(attendanceRepository.findByStudentIdAndDate(anyLong(), eq(jour))).thenReturn(Optional.empty());
        saveAttendanceRenvoieSonArgument();

        attendanceService.record(requete(3L,
                entree(6L, AttendanceStatus.PRESENT),
                entree(7L, AttendanceStatus.ABSENT),
                entree(8L, AttendanceStatus.LATE)), httpRequest);

        verify(auditService).log(eq("RECORD"), eq("Attendance"), isNull(),
                contains("1 absent(s), 1 retard(s)"), eq(httpRequest));
    }

    // ------------------------------------------------------- notifications

    @Test
    @DisplayName("Un pointage 100% PRESENT ne declenche AUCUNE notification ni alerte")
    void aucunMessageQuandToutLeMondeEstPresent() {
        Student a = eleve(6L, 3L, parent(3L, User.builder().id(70L).build()));
        when(studentRepository.findById(6L)).thenReturn(Optional.of(a));
        when(attendanceRepository.findByStudentIdAndDate(anyLong(), eq(jour))).thenReturn(Optional.empty());
        saveAttendanceRenouvele();

        AttendanceRecordResponse reponse = attendanceService.record(
                requete(3L, entree(6L, AttendanceStatus.PRESENT)), httpRequest);

        // Le cas le plus frequent : aucune famille ne doit etre derangee.
        // (Protege par la porte exterieure ET le filtre interieur — cf. l'entete de classe.)
        assertThat(reponse.getAlerts()).isEmpty();
        verifyNoInteractions(notificationRepository);
        verifyNoInteractions(whatsappService);
        verifyNoInteractions(emailService);
        verifyNoInteractions(smsService);
    }

    @Test
    @DisplayName("Une absence declenche les quatre canaux : in-app, WhatsApp, email, SMS")
    void uneAbsenceDeclencheLesQuatreCanaux() {
        Parent p = parent(3L, User.builder().id(70L).build());
        Student a = eleve(6L, 3L, p);
        when(studentRepository.findById(6L)).thenReturn(Optional.of(a));
        when(attendanceRepository.findByStudentIdAndDate(anyLong(), eq(jour))).thenReturn(Optional.empty());
        when(phoneNumberService.normalize("66000000")).thenReturn("+23766000000");
        when(whatsappService.absenceMessage(any(), any(), any(), any(), any())).thenReturn("Bonjour ...");
        saveAttendanceRenouvele();

        attendanceService.record(requete(3L, entree(6L, AttendanceStatus.ABSENT)), httpRequest);

        verify(notificationRepository).save(any());
        verify(whatsappService).sendAbsenceAlert(eq(p), eq(a), eq(jour), eq(AttendanceStatus.ABSENT), isNull());
        verify(emailService).sendAbsenceAlert(eq(p), eq(a), eq(jour), eq(AttendanceStatus.ABSENT), isNull());
        verify(smsService).sendAbsenceAlert(eq(p), eq(a), eq(jour), eq(AttendanceStatus.ABSENT), isNull());
    }

    @Test
    @DisplayName("Un retard declenche aussi les notifications")
    void unRetardDeclencheLesNotifications() {
        Parent p = parent(3L, User.builder().id(70L).build());
        Student a = eleve(6L, 3L, p);
        when(studentRepository.findById(6L)).thenReturn(Optional.of(a));
        when(attendanceRepository.findByStudentIdAndDate(anyLong(), eq(jour))).thenReturn(Optional.empty());
        saveAttendanceRenouvele();

        attendanceService.record(requete(3L, entree(6L, AttendanceStatus.LATE, "Bus")), httpRequest);

        verify(smsService).sendAbsenceAlert(eq(p), eq(a), eq(jour), eq(AttendanceStatus.LATE), eq("Bus"));
    }

    @Test
    @DisplayName("Une justification NE declenche rien (l'eleve est couvert)")
    void uneJustificationNeDeclencheRien() {
        Parent p = parent(3L, User.builder().id(70L).build());
        Student a = eleve(6L, 3L, p);
        when(studentRepository.findById(6L)).thenReturn(Optional.of(a));
        when(attendanceRepository.findByStudentIdAndDate(anyLong(), eq(jour))).thenReturn(Optional.empty());
        saveAttendanceRenouvele();

        attendanceService.record(requete(3L, entree(6L, AttendanceStatus.JUSTIFIED)), httpRequest);

        // JUSTIFIED n'est ni ABSENT ni LATE : alerter serait un faux positif.
        verifyNoInteractions(notificationRepository);
        verifyNoInteractions(smsService);
        verifyNoInteractions(emailService);
        verifyNoInteractions(whatsappService);
    }

    @Test
    @DisplayName("Une absence sans compte parent NE cree PAS de notification in-app")
    void absenceSansCompteParentNeCreePasDeNotificationInApp() {
        Parent p = parent(3L, null);
        Student a = eleve(6L, 3L, p);
        when(studentRepository.findById(6L)).thenReturn(Optional.of(a));
        when(attendanceRepository.findByStudentIdAndDate(anyLong(), eq(jour))).thenReturn(Optional.empty());
        saveAttendanceRenouvele();

        attendanceService.record(requete(3L, entree(6L, AttendanceStatus.ABSENT)), httpRequest);

        // Notification.user est non-nullable : sans compte, il faut passer par WhatsApp/email/SMS.
        verify(notificationRepository, never()).save(any());
        verify(smsService).sendAbsenceAlert(eq(p), eq(a), eq(jour), eq(AttendanceStatus.ABSENT), isNull());
    }

    @Test
    @DisplayName("Une absence d'un eleve sans parent rattache ne notifie personne")
    void absenceSansParentNeNotifiePersonne() {
        Student a = eleve(6L, 3L, null);
        when(studentRepository.findById(6L)).thenReturn(Optional.of(a));
        when(attendanceRepository.findByStudentIdAndDate(anyLong(), eq(jour))).thenReturn(Optional.empty());
        saveAttendanceRenouvele();

        attendanceService.record(requete(3L, entree(6L, AttendanceStatus.ABSENT)), httpRequest);

        verifyNoInteractions(notificationRepository);
        verifyNoInteractions(smsService);
        verifyNoInteractions(emailService);
        verifyNoInteractions(whatsappService);
    }

    @Test
    @DisplayName("La notification in-app reprend le nom de l'eleve et le motif du retard")
    void notificationInAppContientLeNomEtLeMotif() {
        Parent p = parent(3L, User.builder().id(70L).build());
        Student a = eleve(6L, 3L, p);
        when(studentRepository.findById(6L)).thenReturn(Optional.of(a));
        when(attendanceRepository.findByStudentIdAndDate(anyLong(), eq(jour))).thenReturn(Optional.empty());
        saveAttendanceRenouvele();

        ArgumentCaptor<com.school.entity.Notification> captor =
                ArgumentCaptor.forClass(com.school.entity.Notification.class);
        attendanceService.record(requete(3L, entree(6L, AttendanceStatus.ABSENT, "Maladie")), httpRequest);
        verify(notificationRepository).save(captor.capture());

        assertThat(captor.getValue().getTitle()).contains("Aicha Kamdem");
        assertThat(captor.getValue().getMessage()).contains("absent").contains("Maladie");
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.WARNING);
    }

    // -------------------------------------------------------------- alertes wa.me

    @Test
    @DisplayName("Une alerte wa.me est construite pour un absent joignable")
    void uneAlerteWameEstConstruite() {
        Parent p = parent(3L, null);
        Student a = eleve(6L, 3L, p);
        when(studentRepository.findById(6L)).thenReturn(Optional.of(a));
        when(attendanceRepository.findByStudentIdAndDate(anyLong(), eq(jour))).thenReturn(Optional.empty());
        when(phoneNumberService.normalize("66000000")).thenReturn("+23766000000");
        when(whatsappService.absenceMessage(eq(p), eq(a), eq(jour), eq(AttendanceStatus.ABSENT), isNull()))
                .thenReturn("Bonjour Harouna Siby, votre enfant ...");
        saveAttendanceRenouvele();

        AttendanceRecordResponse reponse = attendanceService.record(
                requete(3L, entree(6L, AttendanceStatus.ABSENT)), httpRequest);

        assertThat(reponse.getAlerts()).hasSize(1);
        WhatsappAlert alerte = reponse.getAlerts().get(0);
        assertThat(alerte.getPhone()).isEqualTo("+23766000000");
        assertThat(alerte.getWaLink()).startsWith("https://wa.me/23766000000?text=");
        // Le « + » doit disparaitre du chemin wa.me, sinon le lien ne fonctionne pas.
        assertThat(alerte.getWaLink()).doesNotContain("wa.me/+");
    }

    @Test
    @DisplayName("Le message est encode pour l'URL (retours a la ligne, apostrophes)")
    void leMessageEstEncodePourLUrl() {
        Parent p = parent(3L, null);
        Student a = eleve(6L, 3L, p);
        when(studentRepository.findById(6L)).thenReturn(Optional.of(a));
        when(attendanceRepository.findByStudentIdAndDate(anyLong(), eq(jour))).thenReturn(Optional.empty());
        when(phoneNumberService.normalize(anyString())).thenReturn("+23766000000");
        when(whatsappService.absenceMessage(any(), any(), any(), any(), any()))
                .thenReturn("Bonjour,\nl'enfant est absent");
        saveAttendanceRenouvele();

        AttendanceRecordResponse reponse = attendanceService.record(
                requete(3L, entree(6L, AttendanceStatus.ABSENT)), httpRequest);

        String lien = reponse.getAlerts().get(0).getWaLink();
        // Un saut de ligne brut casserait le lien : il doit etre en %0A.
        assertThat(lien).contains("%0A");
        assertThat(lien).doesNotContain("\n");
    }

    @Test
    @DisplayName("Aucune alerte si le numero du parent est inexploitable")
    void aucuneAlerteSiLeNumeroEstInvalide() {
        Parent p = parent(3L, null);
        Student a = eleve(6L, 3L, p);
        when(studentRepository.findById(6L)).thenReturn(Optional.of(a));
        when(attendanceRepository.findByStudentIdAndDate(anyLong(), eq(jour))).thenReturn(Optional.empty());
        when(phoneNumberService.normalize("66000000")).thenReturn(null);
        saveAttendanceRenouvele();

        AttendanceRecordResponse reponse = attendanceService.record(
                requete(3L, entree(6L, AttendanceStatus.ABSENT)), httpRequest);

        // Pas de numero fiable = pas de lien wa.me errone.
        assertThat(reponse.getAlerts()).isEmpty();
    }

    @Test
    @DisplayName("Aucune alerte pour un present (aucun parent charge)")
    void aucuneAlertePourUnPresent() {
        Student a = eleve(6L, 3L, parent(3L, null));
        when(studentRepository.findById(6L)).thenReturn(Optional.of(a));
        when(attendanceRepository.findByStudentIdAndDate(anyLong(), eq(jour))).thenReturn(Optional.empty());
        saveAttendanceRenouvele();

        AttendanceRecordResponse reponse = attendanceService.record(
                requete(3L, entree(6L, AttendanceStatus.PRESENT)), httpRequest);

        assertThat(reponse.getAlerts()).isEmpty();
        verify(phoneNumberService, never()).normalize(anyString());
    }

    @Test
    @DisplayName("Une entree d'alerte dont l'eleve a disparu est ignoree sans erreur")
    void uneEntreeDontLEleveADisparuEstIgnoree() {
        Parent p = parent(3L, null);
        Student a = eleve(6L, 3L, p);
        // recordSingle trouve l'eleve, puis buildWhatsappAlerts ne le trouve plus (course).
        when(studentRepository.findById(6L))
                .thenReturn(Optional.of(a))
                .thenReturn(Optional.empty());
        when(attendanceRepository.findByStudentIdAndDate(anyLong(), eq(jour))).thenReturn(Optional.empty());
        saveAttendanceRenouvele();

        AttendanceRecordResponse reponse = attendanceService.record(
                requete(3L, entree(6L, AttendanceStatus.ABSENT)), httpRequest);

        // L'alerte est simplement omise : le pointage reste enregistre.
        assertThat(reponse.getAttendance()).hasSize(1);
        assertThat(reponse.getAlerts()).isEmpty();
    }

    // ----------------------------------------------------------- justification

    @Test
    @DisplayName("justify passe une absence en JUSTIFIED et enregistre le motif")
    void justifyPasseEnJustified() {
        Attendance absent = Attendance.builder()
                .id(9L).student(eleve(6L, 3L, null)).schoolClass(classe(3L))
                .date(jour).status(AttendanceStatus.ABSENT).build();
        when(attendanceRepository.findById(9L)).thenReturn(Optional.of(absent));
        saveAttendanceRenouvele();

        AttendanceResponse reponse = attendanceService.justify(9L, "Certificat medical", httpRequest);

        assertThat(reponse.getStatus()).isEqualTo(AttendanceStatus.JUSTIFIED);
        assertThat(reponse.getJustification()).isEqualTo("Certificat medical");
        verify(auditService).log(eq("JUSTIFY"), eq("Attendance"), eq(9L), any(), eq(httpRequest));
    }

    @Test
    @DisplayName("justify accepte de justifier un retard")
    void justifyAccepteUnRetard() {
        Attendance retard = Attendance.builder()
                .id(9L).student(eleve(6L, 3L, null)).schoolClass(classe(3L))
                .date(jour).status(AttendanceStatus.LATE).build();
        when(attendanceRepository.findById(9L)).thenReturn(Optional.of(retard));
        saveAttendanceRenouvele();

        AttendanceResponse reponse = attendanceService.justify(9L, "Bus", httpRequest);

        assertThat(reponse.getStatus()).isEqualTo(AttendanceStatus.JUSTIFIED);
    }

    @Test
    @DisplayName("justify refuse une presence (rien a justifier)")
    void justifyRefuseUnePresence() {
        Attendance present = Attendance.builder()
                .id(9L).student(eleve(6L, 3L, null)).schoolClass(classe(3L))
                .date(jour).status(AttendanceStatus.PRESENT).build();
        when(attendanceRepository.findById(9L)).thenReturn(Optional.of(present));

        assertThatThrownBy(() -> attendanceService.justify(9L, "x", httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("absences et retards");

        verify(attendanceRepository, never()).save(any(Attendance.class));
    }

    @Test
    @DisplayName("justify refuse de re-justifier une absence deja justifiee")
    void justifyRefuseUneDeuxiemeJustification() {
        Attendance justifie = Attendance.builder()
                .id(9L).student(eleve(6L, 3L, null)).schoolClass(classe(3L))
                .date(jour).status(AttendanceStatus.JUSTIFIED).build();
        when(attendanceRepository.findById(9L)).thenReturn(Optional.of(justifie));

        assertThatThrownBy(() -> attendanceService.justify(9L, "x", httpRequest))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("justify sur une presence inconnue leve une exception nommee")
    void justifySurUnePresenceInconnueLeveUneException() {
        when(attendanceRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> attendanceService.justify(404L, "x", httpRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Présence")
                .hasMessageContaining("404");
    }

    // ---------------------------------------------------------------- compteurs

    @Test
    @DisplayName("absencesOf, presentsOf et latesOf interrogent chacun le bon statut")
    void lesCompteursInterrogentLeBonStatut() {
        when(attendanceRepository.countByStudentIdAndStatus(6L, AttendanceStatus.ABSENT)).thenReturn(3L);
        when(attendanceRepository.countByStudentIdAndStatus(6L, AttendanceStatus.PRESENT)).thenReturn(20L);
        when(attendanceRepository.countByStudentIdAndStatus(6L, AttendanceStatus.LATE)).thenReturn(2L);

        assertThat(attendanceService.absencesOf(6L)).isEqualTo(3L);
        assertThat(attendanceService.presentsOf(6L)).isEqualTo(20L);
        assertThat(attendanceService.latesOf(6L)).isEqualTo(2L);

        // Le piege classique : un compteur qui interroge le mauvais statut ne se voit pas.
        verify(attendanceRepository).countByStudentIdAndStatus(6L, AttendanceStatus.ABSENT);
        verify(attendanceRepository).countByStudentIdAndStatus(6L, AttendanceStatus.PRESENT);
        verify(attendanceRepository).countByStudentIdAndStatus(6L, AttendanceStatus.LATE);
    }

    // ------------------------------------------------------------------ rapports

    @Test
    @DisplayName("classReport compte chaque statut et calcule le taux sur le total")
    void classReportCompteEtCalculeLeTaux() {
        Student a = eleve(6L, 3L, null);
        when(studentRepository.findBySchoolClassId(3L)).thenReturn(List.of(a));
        when(attendanceRepository.findBySchoolClassIdAndDateBetween(eq(3L), any(), any())).thenReturn(List.of(
                presence(a, AttendanceStatus.PRESENT),
                presence(a, AttendanceStatus.PRESENT),
                presence(a, AttendanceStatus.PRESENT),
                presence(a, AttendanceStatus.ABSENT),
                presence(a, AttendanceStatus.JUSTIFIED)));

        List<AttendanceReportRow> lignes = attendanceService.classReport(3L, jour.minusDays(30), jour);

        assertThat(lignes).hasSize(1);
        AttendanceReportRow ligne = lignes.get(0);
        assertThat(ligne.getPresent()).isEqualTo(3);
        assertThat(ligne.getAbsent()).isEqualTo(1);
        assertThat(ligne.getJustified()).isEqualTo(1);
        // 3 presents / 5 total = 60,0 %
        assertThat(ligne.getRate()).isEqualByComparingTo("60.0");
    }

    @Test
    @DisplayName("classReport renvoie un taux null (et non zero) pour un eleve sans historique")
    void classReportRenvoieTauxNullSansHistorique() {
        Student a = eleve(6L, 3L, null);
        when(studentRepository.findBySchoolClassId(3L)).thenReturn(List.of(a));
        when(attendanceRepository.findBySchoolClassIdAndDateBetween(eq(3L), any(), any()))
                .thenReturn(List.of());

        List<AttendanceReportRow> lignes = attendanceService.classReport(3L, jour.minusDays(30), jour);

        // null = « aucune donnee », 0 % serait un mensonge sur un eleve jamais pointe.
        assertThat(lignes.get(0).getRate()).isNull();
        assertThat(lignes.get(0).getPresent()).isZero();
    }

    @Test
    @DisplayName("classReport liste TOUS les eleves de la classe, meme sans pointage")
    void classReportListeTousLesEleves() {
        Student a = eleve(6L, 3L, null);
        Student b = eleve(7L, 3L, null);
        when(studentRepository.findBySchoolClassId(3L)).thenReturn(List.of(a, b));
        when(attendanceRepository.findBySchoolClassIdAndDateBetween(eq(3L), any(), any()))
                .thenReturn(List.of(presence(b, AttendanceStatus.PRESENT)));

        List<AttendanceReportRow> lignes = attendanceService.classReport(3L, null, null);

        assertThat(lignes).hasSize(2);
        assertThat(lignes.stream().map(AttendanceReportRow::getStudentId)).containsExactly(6L, 7L);
    }

    @Test
    @DisplayName("classReport applique un mois glissant par defaut")
    void classReportAppliqueLesBornesParDefaut() {
        Student a = eleve(6L, 3L, null);
        when(studentRepository.findBySchoolClassId(3L)).thenReturn(List.of(a));
        when(attendanceRepository.findBySchoolClassIdAndDateBetween(eq(3L), any(), any()))
                .thenReturn(List.of());

        attendanceService.classReport(3L, null, null);

        ArgumentCaptor<LocalDate> captor = ArgumentCaptor.forClass(LocalDate.class);
        verify(attendanceRepository).findBySchoolClassIdAndDateBetween(eq(3L), captor.capture(), captor.capture());
        List<LocalDate> bornes = captor.getAllValues();
        assertThat(bornes.get(0)).isEqualTo(LocalDate.now().minusMonths(1));
        assertThat(bornes.get(1)).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("classReport ignore les pointages d'un autre eleve non inscrit dans la classe")
    void classReportIgnoreLesPointagesEtrangers() {
        Student a = eleve(6L, 3L, null);
        Student autre = eleve(99L, 3L, null);
        when(studentRepository.findBySchoolClassId(3L)).thenReturn(List.of(a));
        when(attendanceRepository.findBySchoolClassIdAndDateBetween(eq(3L), any(), any()))
                .thenReturn(List.of(presence(autre, AttendanceStatus.PRESENT)));

        List<AttendanceReportRow> lignes = attendanceService.classReport(3L, null, null);

        // Le groupement se fait par identifiant : l'eleve 99 ne doit pas apparaitre.
        assertThat(lignes).hasSize(1);
        assertThat(lignes.get(0).getStudentId()).isEqualTo(6L);
        assertThat(lignes.get(0).getRate()).isNull();
    }

    private Attendance presence(Student s, AttendanceStatus statut) {
        return Attendance.builder().id(1L).student(s).schoolClass(s.getSchoolClass())
                .date(jour).status(statut).build();
    }

    private void saveAttendanceRenouvele() {
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // -------------------------------------------------- presences enseignants

    @Test
    @DisplayName("recordTeachers cree un pointage par enseignant")
    void recordTeachersCreeUnPointageParEnseignant() {
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(enseignant(1L)));
        when(teacherRepository.findById(2L)).thenReturn(Optional.of(enseignant(2L)));
        when(teacherAttendanceRepository.findByTeacherIdAndDate(anyLong(), eq(jour)))
                .thenReturn(Optional.empty());
        when(teacherAttendanceRepository.save(any(TeacherAttendance.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        TeacherAttendanceRequest requete = TeacherAttendanceRequest.builder()
                .date(jour)
                .entries(List.of(
                        TeacherAttendanceRequest.Entry.builder()
                                .teacherId(1L).status(AttendanceStatus.PRESENT).build(),
                        TeacherAttendanceRequest.Entry.builder()
                                .teacherId(2L).status(AttendanceStatus.ABSENT).build()))
                .build();

        List<TeacherAttendanceResponse> reponses = attendanceService.recordTeachers(requete, httpRequest);

        assertThat(reponses).hasSize(2);
        verify(teacherAttendanceRepository, times(2)).save(any(TeacherAttendance.class));
        verify(auditService).log(eq("TEACHER_ATTENDANCE"), eq("TeacherAttendance"), isNull(),
                any(), eq(httpRequest));
    }

    @Test
    @DisplayName("recordTeachers met a jour le pointage existant du jour (upsert)")
    void recordTeachersMetAJourLExistant() {
        Teacher t = enseignant(1L);
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(t));
        TeacherAttendance existant = TeacherAttendance.builder()
                .id(77L).teacher(t).date(jour).status(AttendanceStatus.PRESENT).build();
        when(teacherAttendanceRepository.findByTeacherIdAndDate(1L, jour)).thenReturn(Optional.of(existant));
        when(teacherAttendanceRepository.save(any(TeacherAttendance.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        TeacherAttendanceRequest requete = TeacherAttendanceRequest.builder()
                .date(jour)
                .entries(List.of(TeacherAttendanceRequest.Entry.builder()
                        .teacherId(1L).status(AttendanceStatus.LATE).justification("Retard").build()))
                .build();

        List<TeacherAttendanceResponse> reponses = attendanceService.recordTeachers(requete, httpRequest);

        assertThat(reponses).hasSize(1);
        assertThat(reponses.get(0).getId()).isEqualTo(77L);
        assertThat(reponses.get(0).getStatus()).isEqualTo(AttendanceStatus.LATE);
        verify(teacherAttendanceRepository, times(1)).save(any(TeacherAttendance.class));
    }

    @Test
    @DisplayName("recordTeachers refuse un enseignant inconnu")
    void recordTeachersRefuseUnEnseignantInconnu() {
        when(teacherRepository.findById(404L)).thenReturn(Optional.empty());
        TeacherAttendanceRequest requete = TeacherAttendanceRequest.builder()
                .date(jour)
                .entries(List.of(TeacherAttendanceRequest.Entry.builder()
                        .teacherId(404L).status(AttendanceStatus.PRESENT).build()))
                .build();

        assertThatThrownBy(() -> attendanceService.recordTeachers(requete, httpRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Enseignant");
    }

    @Test
    @DisplayName("listTeachersByDate renvoie les pointages du jour")
    void listTeachersByDateRenvoieLesPointages() {
        Teacher t = enseignant(1L);
        when(teacherAttendanceRepository.findByDateOrderByTeacherLastNameAsc(jour))
                .thenReturn(List.of(TeacherAttendance.builder()
                        .id(77L).teacher(t).date(jour).status(AttendanceStatus.PRESENT).build()));

        List<TeacherAttendanceResponse> reponses = attendanceService.listTeachersByDate(jour);

        assertThat(reponses).hasSize(1);
        assertThat(reponses.get(0).getTeacherId()).isEqualTo(1L);
    }

    // -------------------------------------------------------------------- listes

    @Test
    @DisplayName("listByClassAndDate renvoie les presences de la classe au jour demande")
    void listByClassAndDateRenvoieLesPresences() {
        Student a = eleve(6L, 3L, null);
        when(attendanceRepository.findBySchoolClassIdAndDate(3L, jour))
                .thenReturn(List.of(presence(a, AttendanceStatus.PRESENT)));

        List<AttendanceResponse> reponses = attendanceService.listByClassAndDate(3L, jour);

        assertThat(reponses).hasSize(1);
        assertThat(reponses.get(0).getStudentId()).isEqualTo(6L);
        assertThat(reponses.get(0).getClassId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("listByStudent renvoie l'historique de l'eleve")
    void listByStudentRenvoieLHistorique() {
        Student a = eleve(6L, 3L, null);
        when(attendanceRepository.findByStudentId(6L))
                .thenReturn(List.of(presence(a, AttendanceStatus.ABSENT), presence(a, AttendanceStatus.PRESENT)));

        List<AttendanceResponse> reponses = attendanceService.listByStudent(6L);

        assertThat(reponses).hasSize(2);
    }
}
