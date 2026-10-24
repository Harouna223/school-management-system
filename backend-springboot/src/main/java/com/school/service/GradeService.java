package com.school.service;

import com.school.dto.request.GradeRequest;
import com.school.dto.response.BulletinLineResponse;
import com.school.dto.response.BulletinResponse;
import com.school.dto.response.GradeResponse;
import com.school.entity.Bulletin;
import com.school.entity.Exam;
import com.school.entity.Grade;
import com.school.entity.Notification;
import com.school.entity.Student;
import com.school.entity.Subject;
import com.school.enums.DeliberationStatus;
import com.school.enums.ExamType;
import com.school.enums.NotificationType;
import com.school.enums.Term;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.BulletinRepository;
import com.school.repository.GradeRepository;
import com.school.repository.NotificationRepository;
import com.school.repository.StudentRepository;
import com.school.utils.CodeGenerator;
import com.school.utils.GradeObservation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Module notes : saisie, moyennes pondérées, classements, bulletins.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GradeService {

    private final GradeRepository gradeRepository;
    private final StudentRepository studentRepository;
    private final BulletinRepository bulletinRepository;
    private final NotificationRepository notificationRepository;
    private final ExamService examService;
    private final AuditService auditService;
    private final ReportService reportService;
    private final WhatsAppService whatsappService;
    private final EmailService emailService;
    private final SmsService smsService;
    private final AccessControlService accessControlService;
    private final AcademicCalendarService academicCalendarService;

    @Transactional(readOnly = true)
    public List<GradeResponse> listByExam(Long examId) {
        return gradeRepository.findByExamId(examId).stream()
                .map(GradeResponse::from).toList();
    }

    /**
     * Notes d'une évaluation, bornées à une année scolaire (celle de l'évaluation
     * par défaut) : évite de mélanger les notes de deux années sur un même écran.
     */
    @Transactional(readOnly = true)
    public List<GradeResponse> listByExam(Long examId, Long yearId) {
        Exam exam = examService.findById(examId);
        String year = yearId != null
                ? academicCalendarService.resolveYear(yearId, null, null).getLabel()
                : exam.getAcademicYear();
        return gradeRepository.findByExamIdAndYear(examId, year).stream()
                .map(GradeResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<GradeResponse> listByStudent(Long studentId) {
        return gradeRepository.findByStudentId(studentId).stream()
                .map(GradeResponse::from).toList();
    }

    /**
     * Saisie d'une note avec validation des bornes (0 à maxValue, max 20).
     */
    @Transactional
    public GradeResponse save(GradeRequest request, HttpServletRequest httpRequest) {
        Exam exam = examService.findById(request.getExamId());
        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> ResourceNotFoundException.of("Élève", request.getStudentId()));

        BigDecimal maxValue = request.getMaxValue() != null ? request.getMaxValue()
                : BigDecimal.valueOf(20);
        if (maxValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Le barème doit être supérieur à zéro");
        }
        // Cohérence élève ↔ évaluation : une note saisie sur l'évaluation d'une
        // autre classe polluait la moyenne et le rang de l'élève (les moyennes
        // sont calculées par élève + trimestre).
        if (student.getSchoolClass() == null
                || exam.getSchoolClass() == null
                || !student.getSchoolClass().getId().equals(exam.getSchoolClass().getId())) {
            String classeEvaluation = exam.getSchoolClass() != null
                    ? exam.getSchoolClass().getName() : "inconnue";
            throw new BusinessException("Cet élève (" + student.getFullName()
                    + ") n'appartient pas à la classe de l'évaluation (" + classeEvaluation + ")");
        }
        if (request.getValue() == null) {
            throw new BusinessException("La note est obligatoire");
        }
        if (request.getValue().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("La note ne peut pas être négative");
        }
        if (request.getValue().compareTo(maxValue) > 0) {
            throw new BusinessException("La note ne peut pas dépasser " + maxValue);
        }

        Grade grade = gradeRepository.findByStudentIdAndExamId(student.getId(), exam.getId())
                .orElseGet(() -> Grade.builder()
                        .student(student)
                        .exam(exam)
                        .maxValue(maxValue)
                        .build());
        grade.setValue(request.getValue());
        grade.setMaxValue(maxValue);
        grade.setAppreciation(request.getAppreciation());

        Grade saved = gradeRepository.save(grade);
        auditService.log("SAVE_GRADE", "Grade", saved.getId(),
                "Note " + saved.getValue() + "/" + maxValue + " pour " + student.getFullName()
                        + " (" + exam.getName() + ")", httpRequest);
        return GradeResponse.from(saved);
    }

    /**
     * Moyenne trimestrielle pondérée d'un élève.
     * moyenne = Σ(note × coef évaluation × coef matière) / Σ(coef évaluation × coef matière)
     *
     * @param yearId année scolaire ; {@code null} = année courante (celle qui
     *               couvre la date du jour, sinon celle marquée « courante »)
     */
    @Transactional(readOnly = true)
    public BigDecimal averageForStudent(Long studentId, Term term) {
        return averageForStudent(studentId, term, null);
    }

    @Transactional(readOnly = true)
    public BigDecimal averageForStudent(Long studentId, Term term, Long yearId) {
        String year = academicCalendarService.resolveYear(yearId, null, null).getLabel();
        // Une seule source de calcul : les lignes du bulletin. Rang, mention,
        // décision et PDF restent donc TOUJOURS cohérents entre eux.
        return generalAverage(bulletinLines(studentId, term, year));
    }

    // ==================================================================
    // Bulletin à sept colonnes
    // ==================================================================

    /**
     * Lignes du bulletin d'un élève pour un trimestre : une par matière.
     *
     * <p>Règles de calcul (validées par l'établissement) :</p>
     * <ul>
     *   <li><b>Note/classe</b> = moyenne des évaluations dont le type n'est PAS
     *       {@code COMPOSITION}, pondérée par le coefficient de chaque
     *       évaluation ;</li>
     *   <li><b>Note/compo</b> = moyenne des évaluations de type
     *       {@code COMPOSITION} ;</li>
     *   <li><b>Moyenne</b> = (note/classe + note/compo) / 2 ; si l'une des deux
     *       manque (élève absent, matière sans composition), la moyenne retient
     *       la seule note disponible plutôt que de pénaliser l'élève ;</li>
     *   <li><b>Note/compo coef</b> = moyenne × coefficient de la matière ;</li>
     *   <li><b>Observation</b> = échelle officielle appliquée à la moyenne.</li>
     * </ul>
     *
     * <p>Les notes sont ramenées sur 20 : un devoir noté sur 10 ou sur 40 ne
     * fausse ni la moyenne ni le classement.</p>
     */
    @Transactional(readOnly = true)
    public List<BulletinLineResponse> bulletinLines(Long studentId, Term term, String year) {
        List<Grade> grades = gradeRepository.findByStudentIdAndTermAndYear(studentId, term, year);

        Map<Long, List<Grade>> bySubject = new LinkedHashMap<>();
        for (Grade grade : grades) {
            if (grade.getValue() == null || grade.getExam() == null
                    || grade.getExam().getSubject() == null) {
                continue;
            }
            bySubject.computeIfAbsent(grade.getExam().getSubject().getId(), k -> new ArrayList<>())
                    .add(grade);
        }

        List<BulletinLineResponse> lines = new ArrayList<>();
        for (List<Grade> subjectGrades : bySubject.values()) {
            Subject subject = subjectGrades.get(0).getExam().getSubject();
            BigDecimal noteClasse = weightedNote(subjectGrades, false);
            BigDecimal noteCompo = weightedNote(subjectGrades, true);
            BigDecimal moyenne = round2(subjectAverage(noteClasse, noteCompo));
            int coefficient = subject.getCoefficient() != null ? subject.getCoefficient() : 1;

            lines.add(BulletinLineResponse.builder()
                    .subjectId(subject.getId())
                    .subjectName(subject.getName())
                    .noteClasse(round2(noteClasse))
                    .noteComposition(round2(noteCompo))
                    .coefficient(coefficient)
                    // Calculé sur la moyenne AFFICHÉE : le bulletin imprimé doit
                    // pouvoir être recompté à la main sans écart d'arrondi.
                    .noteCoef(moyenne == null ? null
                            : round2(moyenne.multiply(BigDecimal.valueOf(coefficient))))
                    .moyenne(moyenne)
                    .observation(GradeObservation.of(moyenne))
                    .build());
        }
        lines.sort(Comparator.comparing(BulletinLineResponse::getSubjectName,
                String.CASE_INSENSITIVE_ORDER));
        return lines;
    }

    /** Moyenne générale = Σ(note/compo coef) / Σ(coef), sur les matières notées. */
    private BigDecimal generalAverage(List<BulletinLineResponse> lines) {
        BigDecimal points = BigDecimal.ZERO;
        int coefficients = 0;
        for (BulletinLineResponse line : lines) {
            if (line.getMoyenne() == null || line.getNoteCoef() == null) {
                continue;
            }
            points = points.add(line.getNoteCoef());
            coefficients += line.getCoefficient() != null ? line.getCoefficient() : 1;
        }
        return coefficients == 0 ? null
                : points.divide(BigDecimal.valueOf(coefficients), 2, RoundingMode.HALF_UP);
    }

    /**
     * Moyenne pondérée d'un sous-ensemble de notes, normalisée sur 20.
     *
     * @param composition {@code true} = compositions seules, {@code false} =
     *                    toutes les autres évaluations
     */
    private BigDecimal weightedNote(List<Grade> grades, boolean composition) {
        BigDecimal weighted = BigDecimal.ZERO;
        BigDecimal weights = BigDecimal.ZERO;
        for (Grade grade : grades) {
            boolean isComposition = grade.getExam().getType() == ExamType.COMPOSITION;
            if (isComposition != composition) {
                continue;
            }
            BigDecimal maxValue = grade.getMaxValue() != null
                    && grade.getMaxValue().signum() > 0
                    ? grade.getMaxValue() : BigDecimal.valueOf(20);
            BigDecimal normalized = grade.getValue().multiply(BigDecimal.valueOf(20))
                    .divide(maxValue, 8, RoundingMode.HALF_UP);
            BigDecimal weight = BigDecimal.valueOf(
                    grade.getExam().getCoefficient() != null ? grade.getExam().getCoefficient() : 1);
            weighted = weighted.add(normalized.multiply(weight));
            weights = weights.add(weight);
        }
        return weights.signum() == 0 ? null : weighted.divide(weights, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal subjectAverage(BigDecimal noteClasse, BigDecimal noteCompo) {
        if (noteClasse == null) {
            return noteCompo;
        }
        if (noteCompo == null) {
            return noteClasse;
        }
        return noteClasse.add(noteCompo).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
    }

    private BigDecimal round2(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Complète une réponse de bulletin avec ses lignes, ses totaux et son
     * appréciation générale. Utilisé par les exports PDF.
     */
    private BulletinResponse enrich(BulletinResponse response, Long studentId, Term term, String year) {
        List<BulletinLineResponse> lines = bulletinLines(studentId, term, year);
        int totalCoefficient = lines.stream()
                .filter(line -> line.getMoyenne() != null)
                .mapToInt(line -> line.getCoefficient() != null ? line.getCoefficient() : 1)
                .sum();
        BigDecimal totalPoints = lines.stream()
                .map(BulletinLineResponse::getNoteCoef)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        response.setLines(lines);
        response.setTotalCoefficient(totalCoefficient);
        response.setTotalPoints(totalPoints);
        response.setObservation(GradeObservation.of(response.getAverage()));
        return response;
    }

    /**
     * Classement des élèves d'une classe sur un trimestre (par moyenne décroissante).
     */
    @Transactional(readOnly = true)
    public List<StudentRank> rankStudents(Long classId, Term term) {
        return rankStudents(classId, term, null);
    }

    @Transactional(readOnly = true)
    public List<StudentRank> rankStudents(Long classId, Term term, Long yearId) {
        String year = academicCalendarService.resolveYear(yearId, null, null).getLabel();
        List<Student> students = studentRepository.findBySchoolClassId(classId);
        return students.stream()
                .map(s -> new StudentRank(s.getId(), s.getFirstName(), s.getLastName(),
                        s.getMatricule(), averageForStudent(s.getId(), term, yearId)))
                .filter(r -> r.average() != null)
                .sorted(Comparator.comparing(StudentRank::average).reversed())
                .toList();
    }

    /**
     * Génération des bulletins pour une classe et un trimestre.
     *
     * @param yearId année scolaire à étiqueter ; {@code null} = année courante
     */
    @Transactional
    public List<BulletinResponse> generateBulletins(Long classId, Term term,
                                                    HttpServletRequest httpRequest) {
        return generateBulletins(classId, term, null, httpRequest);
    }

    @Transactional
    public List<BulletinResponse> generateBulletins(Long classId, Term term, Long yearId,
                                                    HttpServletRequest httpRequest) {
        String year = referenceYearLabel(yearId);
        List<StudentRank> ranking = rankStudents(classId, term, yearId);

        List<BigDecimal> averages = ranking.stream()
                .map(StudentRank::average)
                .filter(Objects::nonNull)
                .toList();
        BigDecimal classAverage = averages.isEmpty() ? null
                : averages.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(averages.size()), 2, RoundingMode.HALF_UP);
        BigDecimal classMin = averages.isEmpty() ? null
                : averages.stream().min(BigDecimal::compareTo).orElse(null);
        BigDecimal classMax = averages.isEmpty() ? null
                : averages.stream().max(BigDecimal::compareTo).orElse(null);

        List<Bulletin> bulletins = ranking.stream().map(rank -> {
            Student student = studentRepository.findById(rank.studentId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Élève", rank.studentId()));
            Bulletin bulletin = bulletinRepository
                    .findByStudentIdAndTermAndAcademicYear(rank.studentId(), term, year)
                    .orElseGet(() -> Bulletin.builder()
                            .student(student)
                            .term(term)
                            .academicYear(year)
                            .build());
            bulletin.setAverage(rank.average());
            bulletin.setClassAverage(classAverage);
            bulletin.setClassMin(classMin);
            bulletin.setClassMax(classMax);
            bulletin.setRank(ranking.indexOf(rank) + 1);
            bulletin.setMention(computeMention(rank.average()));
            return bulletin;
        }).toList();

        List<Bulletin> saved = bulletinRepository.saveAll(bulletins);
        auditService.log("BULLETIN", "Bulletin", classId,
                "Génération des bulletins " + term + " pour la classe " + classId, httpRequest);
        notifyParents(saved, term, year);
        return saved.stream()
                .sorted(Comparator.comparing(Bulletin::getRank,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(BulletinResponse::from).toList();
    }

    /**
     * Délibération d'une classe pour un trimestre : génère les bulletins manquants,
     * puis arrête la décision (admis / ajourné / redouble) pour chaque élève.
     */
    @Transactional
    public List<BulletinResponse> runDeliberation(Long classId, Term term,
                                                  HttpServletRequest httpRequest) {
        return runDeliberation(classId, term, null, httpRequest);
    }

    @Transactional
    public List<BulletinResponse> runDeliberation(Long classId, Term term, Long yearId,
                                                  HttpServletRequest httpRequest) {
        String year = referenceYearLabel(yearId);
        generateBulletins(classId, term, yearId, httpRequest);
        List<Bulletin> bulletins = bulletinRepository
                .findByStudentSchoolClassIdAndTermAndAcademicYear(classId, term, year)
                .stream()
                .sorted(Comparator.comparing(Bulletin::getRank,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        bulletins.forEach(bulletin -> {
            BigDecimal average = bulletin.getAverage();
            if (average == null) {
                bulletin.setDecision(DeliberationStatus.AJOURNE);
            } else if (average.compareTo(BigDecimal.TEN) >= 0) {
                bulletin.setDecision(DeliberationStatus.ADMIS);
            } else if (average.compareTo(new BigDecimal("8.00")) >= 0) {
                bulletin.setDecision(DeliberationStatus.AJOURNE);
            } else {
                bulletin.setDecision(DeliberationStatus.REDOUBLE);
            }
        });
        List<Bulletin> saved = bulletinRepository.saveAll(bulletins);
        auditService.log("DELIBERATE", "Bulletin", classId,
                "Délibération " + term + " - classe " + classId + " (" + saved.size() + " bulletins)", httpRequest);
        return saved.stream().map(BulletinResponse::from).toList();
    }

    /**
     * Historique académique d'un élève : tous ses bulletins, du plus récent au plus ancien.
     */
    @Transactional(readOnly = true)
    public List<BulletinResponse> bulletinsByStudent(Long studentId) {
        return bulletinRepository.findByStudentIdOrderByAcademicYearDescTermDesc(studentId)
                .stream().map(BulletinResponse::from)
                .sorted(java.util.Comparator.comparingInt(
                        (BulletinResponse b) -> b.getRank() != null ? b.getRank() : Integer.MAX_VALUE))
                .toList();
    }

    /**
     * Export PDF du bulletin d'un élève (notes par matière + synthèse).
     */
    @Transactional(readOnly = true)
    public void exportBulletinPdf(Long bulletinId, jakarta.servlet.http.HttpServletResponse response)
            throws java.io.IOException {
        Bulletin bulletin = bulletinRepository.findById(bulletinId)
                .orElseThrow(() -> ResourceNotFoundException.of("Bulletin", bulletinId));
        // IDOR : un élève / parent ne peut télécharger que le bulletin d'un élève
        // auquel il a accès, même en devinant un bulletinId.
        accessControlService.assertCanAccessStudent(bulletin.getStudent().getId());
        BulletinResponse responseData = enrich(BulletinResponse.from(bulletin),
                bulletin.getStudent().getId(), bulletin.getTerm(), bulletin.getAcademicYear());
        reportService.bulletinPdf(response, responseData);
    }

    /**
     * Export PDF de tous les bulletins d'une classe dans un seul document.
     */
    @Transactional(readOnly = true)
    public void exportClassBulletinsPdf(Long classId, Term term, Long yearId,
                                        jakarta.servlet.http.HttpServletResponse response)
            throws java.io.IOException {
        String year = referenceYearLabel(yearId);
        List<Bulletin> bulletins = bulletinsOfClass(classId, term, year);
        if (bulletins.isEmpty()) {
            throw new com.school.exception.BusinessException("Aucun bulletin trouvé pour cette classe et ce trimestre");
        }
        List<BulletinResponse> responses = bulletins.stream()
                .map(b -> enrich(BulletinResponse.from(b), b.getStudent().getId(),
                        b.getTerm(), b.getAcademicYear()))
                .toList();
        reportService.classBulletinsPdf(response, responses);
    }

    /**
     * Export PDF du procès-verbal de délibération (classement + décisions).
     */
    @Transactional(readOnly = true)
    public void exportDeliberationPv(Long classId, Term term, Long yearId,
                                     jakarta.servlet.http.HttpServletResponse response)
            throws java.io.IOException {
        String year = referenceYearLabel(yearId);
        List<Bulletin> bulletins = bulletinsOfClass(classId, term, year);
        bulletins.sort(Comparator.comparing(Bulletin::getRank,
                Comparator.nullsLast(Comparator.naturalOrder())));
        String className = bulletins.stream().findFirst()
                .map(b -> b.getStudent().getSchoolClass().getName())
                .orElse("Classe " + classId);
        List<String[]> rows = bulletins.stream().map(b -> new String[]{
                String.valueOf(b.getRank() != null ? b.getRank() : "-"),
                b.getStudent().getMatricule(),
                b.getStudent().getFullName(),
                b.getAverage() != null ? b.getAverage().toPlainString() : "-",
                b.getMention() != null ? b.getMention() : "-",
                b.getDecision() != null ? b.getDecision().name() : "-",
        }).toList();
        reportService.exportStudentsPdf(response, rows,
                new String[]{"Rang", "Matricule", "Élève", "Moyenne", "Mention", "Décision"},
                "Procès-verbal de délibération - " + className + " - " + term + " - " + year);
    }

    /**
     * Libellé de l'année scolaire de référence, avec repli explicite.
     *
     * <p>Si l'année demandée ne contient aucun bulletin (typiquement un
     * établissement dont les bulletins ont été générés par l'ancien calcul fondé
     * sur l'année civile), on retombe sur la dernière année où des bulletins
     * existent POUR CE TRIMESTRE plutôt que d'échouer : les documents restent
     * imprimables, et l'année réellement utilisée figure sur le document.</p>
     */
    private String referenceYearLabel(Long yearId) {
        return academicCalendarService.resolveYear(yearId, null, null).getLabel();
    }

    /** Bulletins d'une classe pour un trimestre et une année, avec repli documenté. */
    private List<Bulletin> bulletinsOfClass(Long classId, Term term, String year) {
        List<Bulletin> bulletins = bulletinRepository
                .findByStudentSchoolClassIdAndTermAndAcademicYear(classId, term, year)
                .stream()
                .sorted(Comparator.comparing(Bulletin::getRank,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (!bulletins.isEmpty()) {
            return bulletins;
        }
        String latest = bulletinRepository.findLatestAcademicYear(classId, term);
        if (latest == null || latest.equals(year)) {
            return bulletins;
        }
        log.warn("Aucun bulletin pour la classe {} / {} / {} : repli sur l'année {}",
                classId, term, year, latest);
        return bulletinRepository.findByStudentSchoolClassIdAndTermAndAcademicYear(classId, term, latest)
                .stream()
                .sorted(Comparator.comparing(Bulletin::getRank,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /**
     * Informe automatiquement les parents dont les bulletins sont désormais disponibles.
     */
    private void notifyParents(List<Bulletin> bulletins, Term term, String year) {
        for (Bulletin bulletin : bulletins) {
            Student student = bulletin.getStudent();
            if (student.getParent() == null || student.getParent().getUser() == null) {
                continue;
            }
            notificationRepository.save(Notification.builder()
                    .user(student.getParent().getUser())
                    .title("Bulletin disponible - " + student.getFullName())
                    .message("Le bulletin de notes de " + student.getFullName() +
                            " (" + term + " - année " + year + ") est disponible. Moyenne : " +
                            bulletin.getAverage() + " - Mention : " + bulletin.getMention())
                    .type(NotificationType.SUCCESS)
                    .link("/my-children")
                    .build());
            whatsappService.sendBulletinAvailable(student.getParent(), student, term,
                    bulletin.getAverage(), bulletin.getMention());
            emailService.sendBulletinAvailable(student.getParent(), student, term,
                    bulletin.getAverage(), bulletin.getMention());
            smsService.sendBulletinAvailable(student.getParent(), student, term,
                    bulletin.getAverage(), bulletin.getMention());
        }
    }

    public String computeMention(BigDecimal average) {
        // Même échelle que la colonne « Observation » du bulletin : une seule
        // définition pour la mention stockée et l'appréciation imprimée.
        return GradeObservation.of(average);
    }

    public record StudentRank(Long studentId, String firstName, String lastName,
                              String matricule, BigDecimal average) {
    }
}