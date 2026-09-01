package com.school.service;

import com.school.dto.request.GradeRequest;
import com.school.dto.response.BulletinResponse;
import com.school.dto.response.GradeResponse;
import com.school.entity.Bulletin;
import com.school.entity.Exam;
import com.school.entity.Grade;
import com.school.entity.Notification;
import com.school.entity.Student;
import com.school.enums.DeliberationStatus;
import com.school.enums.NotificationType;
import com.school.enums.Term;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.BulletinRepository;
import com.school.repository.GradeRepository;
import com.school.repository.NotificationRepository;
import com.school.repository.StudentRepository;
import com.school.utils.CodeGenerator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Module notes : saisie, moyennes pondérées, classements, bulletins.
 */
@Service
@RequiredArgsConstructor
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

    @Transactional(readOnly = true)
    public List<GradeResponse> listByExam(Long examId) {
        return gradeRepository.findByExamId(examId).stream()
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
        if (request.getValue().compareTo(maxValue) > 0) {
            throw new BusinessException("La note ne peut pas dépasser " + maxValue);
        }
        if (request.getValue().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("La note ne peut pas être négative");
        }

        Grade grade = gradeRepository.findByStudentIdAndExamId(student.getId(), exam.getId())
                .orElseGet(() -> Grade.builder()
                        .student(student)
                        .exam(exam)
                        .maxValue(maxValue)
                        .build());
        grade.setValue(request.getValue());
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
     */
    @Transactional(readOnly = true)
    public BigDecimal averageForStudent(Long studentId, Term term) {
        List<Grade> grades = gradeRepository.findByStudentIdAndTerm(studentId, term);
        if (grades.isEmpty()) {
            return null;
        }
        BigDecimal totalWeighted = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (Grade g : grades) {
            BigDecimal weight = BigDecimal.valueOf(g.getExam().getCoefficient())
                    .multiply(BigDecimal.valueOf(g.getExam().getSubject().getCoefficient()));
            totalWeighted = totalWeighted.add(g.getValue().multiply(weight));
            totalWeight = totalWeight.add(weight);
        }
        return totalWeight.compareTo(BigDecimal.ZERO) == 0 ? null
                : totalWeighted.divide(totalWeight, 2, RoundingMode.HALF_UP);
    }

    /**
     * Classement des élèves d'une classe sur un trimestre (par moyenne décroissante).
     */
    @Transactional(readOnly = true)
    public List<StudentRank> rankStudents(Long classId, Term term) {
        List<Student> students = studentRepository.findBySchoolClassId(classId);
        return students.stream()
                .map(s -> new StudentRank(s.getId(), s.getFirstName(), s.getLastName(),
                        s.getMatricule(), averageForStudent(s.getId(), term)))
                .filter(r -> r.average() != null)
                .sorted(Comparator.comparing(StudentRank::average).reversed())
                .toList();
    }

    /**
     * Génération des bulletins pour une classe et un trimestre.
     */
    @Transactional
    public List<BulletinResponse> generateBulletins(Long classId, Term term,
                                                    HttpServletRequest httpRequest) {
        List<StudentRank> ranking = rankStudents(classId, term);
        String year = CodeGenerator.currentAcademicYear();

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
        return saved.stream().map(BulletinResponse::from).toList();
    }

    /**
     * Délibération d'une classe pour un trimestre : génère les bulletins manquants,
     * puis arrête la décision (admis / ajourné / redouble) pour chaque élève.
     */
    @Transactional
    public List<BulletinResponse> runDeliberation(Long classId, Term term,
                                                  HttpServletRequest httpRequest) {
        String year = CodeGenerator.currentAcademicYear();
        generateBulletins(classId, term, httpRequest);
        List<Bulletin> bulletins = bulletinRepository
                .findByStudentSchoolClassIdAndTermAndAcademicYear(classId, term, year);
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
                .stream().map(BulletinResponse::from).toList();
    }

    /**
     * Export PDF du bulletin d'un élève (notes par matière + synthèse).
     */
    @Transactional(readOnly = true)
    public void exportBulletinPdf(Long bulletinId, jakarta.servlet.http.HttpServletResponse response)
            throws java.io.IOException {
        Bulletin bulletin = bulletinRepository.findById(bulletinId)
                .orElseThrow(() -> ResourceNotFoundException.of("Bulletin", bulletinId));
        BulletinResponse responseData = BulletinResponse.from(bulletin);
        List<Grade> grades = gradeRepository
                .findByStudentIdAndTerm(bulletin.getStudent().getId(), bulletin.getTerm());
        reportService.bulletinPdf(response, responseData, grades);
    }

    /**
     * Export PDF de tous les bulletins d'une classe dans un seul document.
     */
    @Transactional(readOnly = true)
    public void exportClassBulletinsPdf(Long classId, Term term,
                                        jakarta.servlet.http.HttpServletResponse response)
            throws java.io.IOException {
        String year = com.school.utils.CodeGenerator.currentAcademicYear();
        List<Bulletin> bulletins = bulletinRepository
                .findByStudentSchoolClassIdAndTermAndAcademicYear(classId, term, year);
        if (bulletins.isEmpty()) {
            throw new com.school.exception.BusinessException("Aucun bulletin trouvé pour cette classe et ce trimestre");
        }
        List<BulletinResponse> responses = bulletins.stream().map(BulletinResponse::from).toList();
        List<List<Grade>> gradesList = bulletins.stream()
                .map(b -> gradeRepository.findByStudentIdAndTerm(b.getStudent().getId(), term))
                .toList();
        reportService.classBulletinsPdf(response, responses, gradesList);
    }

    /**
     * Export PDF du procès-verbal de délibération (classement + décisions).
     */
    @Transactional(readOnly = true)
    public void exportDeliberationPv(Long classId, Term term,
                                     jakarta.servlet.http.HttpServletResponse response)
            throws java.io.IOException {
        String year = CodeGenerator.currentAcademicYear();
        String className = bulletinRepository
                .findByStudentSchoolClassIdAndTermAndAcademicYear(classId, term, year).stream()
                .findFirst()
                .map(b -> b.getStudent().getSchoolClass().getName())
                .orElse("Classe " + classId);
        List<Bulletin> bulletins = bulletinRepository
                .findByStudentSchoolClassIdAndTermAndAcademicYear(classId, term, year);
        bulletins.sort(Comparator.comparing(Bulletin::getRank,
                Comparator.nullsLast(Comparator.naturalOrder())));
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
        double a = average.doubleValue();
        if (a >= 16) return "Très bien";
        if (a >= 14) return "Bien";
        if (a >= 12) return "Assez bien";
        if (a >= 10) return "Passable";
        return "Insuffisant";
    }

    public record StudentRank(Long studentId, String firstName, String lastName,
                              String matricule, BigDecimal average) {
    }
}