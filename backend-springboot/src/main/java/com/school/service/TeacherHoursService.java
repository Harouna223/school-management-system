package com.school.service;

import com.school.dto.request.TeacherHourlyRateRequest;
import com.school.dto.request.TeacherWorkHourRequest;
import com.school.dto.response.PageResponse;
import com.school.dto.response.TeacherHourlyRateResponse;
import com.school.dto.response.TeacherWorkHourResponse;
import com.school.entity.AcademicYear;
import com.school.entity.Teacher;
import com.school.entity.TeacherHourlyRate;
import com.school.entity.TeacherWorkHour;
import com.school.entity.User;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.AcademicYearRepository;
import com.school.repository.SchoolClassRepository;
import com.school.repository.SubjectRepository;
import com.school.repository.TeacherHourlyRateRepository;
import com.school.repository.TeacherMonthClosureRepository;
import com.school.repository.TeacherRepository;
import com.school.repository.TeacherWorkHourRepository;
import com.school.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Module « Gestion des heures enseignées » :
 * tarifs horaires historisés et saisie quotidienne des heures.
 * <p>
 * Règles clés :
 * <ul>
 *   <li>le tarif appliqué à chaque saisie est figé (pas de recalcul des anciens mois) ;</li>
 *   <li>les montants sont TOUJOURS recalculés côté serveur ;</li>
 *   <li>doublons interdits (enseignant + date + matière + classe) ;</li>
 *   <li>un mois clôturé ne peut plus être modifié (sauf direction).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TeacherHoursService {

    private final TeacherHourlyRateRepository rateRepository;
    private final TeacherWorkHourRepository workHourRepository;
    private final TeacherMonthClosureRepository closureRepository;
    private final TeacherRepository teacherRepository;
    private final SubjectRepository subjectRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final AcademicYearRepository academicYearRepository;
    private final TeacherService teacherService;
    private final AuditService auditService;

    // ---------- Tarifs horaires ----------

    public List<TeacherHourlyRateResponse> listRates(Long teacherId, Long yearId, Boolean active) {
        return rateRepository.search(teacherId, yearId, active).stream()
                .map(TeacherHourlyRateResponse::from).toList();
    }

    @Transactional
    public TeacherHourlyRateResponse createRate(TeacherHourlyRateRequest request, HttpServletRequest httpRequest) {
        Teacher teacher = teacherService.findById(request.getTeacherId());
        TeacherHourlyRate rate = TeacherHourlyRate.builder()
                .teacher(teacher)
                .hourlyRate(request.getHourlyRate())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .academicYear(resolveYear(request.getAcademicYearId()))
                .active(true)
                .build();
        rate.validateDates();
        if (rateRepository.existsByTeacherIdAndStartDate(teacher.getId(), request.getStartDate())) {
            throw new BusinessException("Un tarif existe déjà pour cet enseignant à cette date de début. "
                    + "Modifiez-le ou désactivez-le plutôt que d'en créer un doublon.");
        }
        TeacherHourlyRate saved = rateRepository.save(rate);
        auditService.log("RATE_CREATE", "TeacherHourlyRate", saved.getId(),
                "Tarif horaire " + saved.getHourlyRate() + " pour " + teacher.getFullName()
                        + " à partir du " + saved.getStartDate(), httpRequest);
        return TeacherHourlyRateResponse.from(saved);
    }

    @Transactional
    public TeacherHourlyRateResponse toggleRate(Long id, boolean active, HttpServletRequest httpRequest) {
        TeacherHourlyRate rate = rateRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Tarif horaire", id));
        rate.setActive(active);
        auditService.log("RATE_STATUS", "TeacherHourlyRate", id,
                (active ? "Activation" : "Désactivation") + " du tarif horaire de "
                        + rate.getTeacher().getFullName(), httpRequest);
        return TeacherHourlyRateResponse.from(rateRepository.save(rate));
    }

    /**
     * Tarif horaire applicable à une date donnée (période de validité couvrante,
     * le plus récent en premier).
     */
    public BigDecimal resolveRate(Long teacherId, LocalDate date) {
        List<TeacherHourlyRate> applicable = rateRepository.findApplicable(teacherId, date);
        if (applicable.isEmpty()) {
            throw new BusinessException("Aucun tarif horaire actif n'est configuré pour cet enseignant "
                    + "à la date du " + date + ". Configurez d'abord son tarif dans « Paie des enseignants → Tarifs ».");
        }
        return applicable.get(0).getHourlyRate();
    }

    @Transactional
    public void delete(Long id, HttpServletRequest httpRequest) {
        TeacherWorkHour workHour = workHourRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Saisie d'heures", id));
        assertEditable(workHour.getDate());
        workHourRepository.delete(workHour);
        auditService.log("HOURS_DELETE", "TeacherWorkHour", id,
                workHour.getTeacher().getFullName() + " : suppression de la saisie du " + workHour.getDate(),
                httpRequest);
    }

    public PageResponse<TeacherWorkHourResponse> search(Long teacherId, Long subjectId, Long classId,
                                                        LocalDate from, LocalDate to, Long yearId,
                                                        int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "date")
                .and(Sort.by(Sort.Direction.DESC, "id")));
        Page<TeacherWorkHour> result = workHourRepository.search(
                teacherId, subjectId, classId, from, to, yearId, pageable);
        return PageResponse.from(result, TeacherWorkHourResponse::from);
    }

    public List<TeacherWorkHourResponse> byTeacherAndDate(Long teacherId, LocalDate date) {
        return workHourRepository.findByTeacherIdAndDateOrderBySubjectAsc(teacherId, date).stream()
                .map(TeacherWorkHourResponse::from).toList();
    }

    // ---------- Clôture mensuelle ----------

    public boolean isClosed(LocalDate monthDate) {
        return closureRepository.existsByMonthDateAndClosedTrue(monthDate);
    }

    /**
     * Refuse l'opération si le mois est clôturé et que l'utilisateur courant
     * n'a pas le droit de réouverture (SUPER_ADMIN / DIRECTEUR).
     */
    public void assertEditable(LocalDate date) {
        LocalDate monthDate = date.withDayOfMonth(1);
        if (!isClosed(monthDate)) {
            return;
        }
        User user = SecurityUtils.currentUser();
        boolean admin = user != null && user.getRoles().stream().anyMatch(r ->
                "SUPER_ADMIN".equals(r.getName()) || "DIRECTEUR".equals(r.getName()));
        if (!admin) {
            throw new BusinessException("Le mois est clôturé : la modification des heures est interdite. "
                    + "Demandez à un administrateur de rouvrir le mois.");
        }
    }

    private void assertMonthOpen(LocalDate date) {
        if (isClosed(date.withDayOfMonth(1))) {
            throw new BusinessException("Le mois est clôturé : les heures ne peuvent plus être "
                    + "modifiées ou supprimées. Seul un administrateur peut rouvrir le mois.");
        }
    }

    private AcademicYear resolveYear(Long yearId) {
        if (yearId != null) {
            return academicYearRepository.findById(yearId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Année scolaire", yearId));
        }
        return academicYearRepository.findByCurrentTrue().orElse(null);
    }

    /** Année scolaire couvrant la date, sinon l'année courante, sinon null. */
    private AcademicYear resolveYearFor(LocalDate date) {
        Optional<AcademicYear> covering = academicYearRepository.findAll().stream()
                .filter(y -> y.getStartDate() != null && y.getEndDate() != null
                        && !y.getStartDate().isAfter(date) && !y.getEndDate().isBefore(date))
                .findFirst();
        return covering.orElseGet(() -> resolveYear(null));
    }

    // ---------- Saisie quotidienne des heures ----------

    @Transactional
    public TeacherWorkHourResponse record(TeacherWorkHourRequest request, HttpServletRequest httpRequest) {
        Teacher teacher = teacherService.findById(request.getTeacherId());
        assertMonthOpen(request.getDate());

        LocalDate date = request.getDate();
        BigDecimal rate = resolveRate(teacher.getId(), date);

        Long subjectId = request.getSubjectId();
        Long classId = request.getClassId();
        Long duplicateKeySubject = subjectId != null ? subjectId : -1L;
        Long duplicateKeyClass = classId != null ? classId : -1L;
        workHourRepository
                .findByTeacherIdAndDateAndSubjectIdAndSchoolClassId(
                        teacher.getId(), date, duplicateKeySubject, duplicateKeyClass)
                .ifPresent(existing -> {
                    throw new BusinessException("Une saisie existe déjà pour ce professeur, cette date, "
                            + "cette matière et cette classe : modifiez-la au lieu d'en créer une nouvelle.");
                });

        // Contrôle des heures excessives : max 24 h cumulées par jour
        BigDecimal already = workHourRepository.sumHoursOfTeacherOnDate(teacher.getId(), date);
        if (already.add(request.getHours()).compareTo(BigDecimal.valueOf(24)) > 0) {
            throw new BusinessException("Heures excessives : " + already + " h déjà saisies pour ce jour. "
                    + "Le total journalier ne peut pas dépasser 24 h.");
        }

        TeacherWorkHour workHour = TeacherWorkHour.builder()
                .teacher(teacher)
                .date(date)
                .hours(request.getHours())
                .subject(subjectId != null ? subjectRepository.findById(subjectId)
                        .orElseThrow(() -> ResourceNotFoundException.of("Matière", subjectId)) : null)
                .schoolClass(classId != null ? schoolClassRepository.findById(classId)
                        .orElseThrow(() -> ResourceNotFoundException.of("Classe", classId)) : null)
                .academicYear(resolveYearFor(date))
                .hourlyRateApplied(rate)
                .observation(request.getObservation())
                .createdBy(SecurityUtils.currentUser())
                .build();
        workHour.validate();
        TeacherWorkHour saved = workHourRepository.save(workHour);
        auditService.log("HOURS_RECORD", "TeacherWorkHour", saved.getId(),
                teacher.getFullName() + " : " + saved.getHours() + " h le " + saved.getDate()
                        + " (tarif " + saved.getHourlyRateApplied() + ")", httpRequest);
        return TeacherWorkHourResponse.from(saved);
    }

    /**
     * Correction d'une saisie journalière.
     * Le tarif est redéterminé selon la (nouvelle) date : le montant est recalculé côté serveur.
     */
    @Transactional
    public TeacherWorkHourResponse update(Long id, TeacherWorkHourRequest request, HttpServletRequest httpRequest) {
        TeacherWorkHour workHour = workHourRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Saisie d'heures", id));
        assertEditable(workHour.getDate());

        Teacher teacher = teacherService.findById(request.getTeacherId());
        workHour.setTeacher(teacher);
        workHour.setDate(request.getDate());
        workHour.setHours(request.getHours());
        workHour.setSubject(request.getSubjectId() != null ? subjectRepository.findById(request.getSubjectId())
                .orElseThrow(() -> ResourceNotFoundException.of("Matière", request.getSubjectId())) : null);
        workHour.setSchoolClass(request.getClassId() != null ? schoolClassRepository.findById(request.getClassId())
                .orElseThrow(() -> ResourceNotFoundException.of("Classe", request.getClassId())) : null);
        workHour.setObservation(request.getObservation());
        workHour.validate();

        Long subjectKey = workHour.getSubject() != null ? workHour.getSubject().getId() : -1L;
        Long classKey = workHour.getSchoolClass() != null ? workHour.getSchoolClass().getId() : -1L;
        workHourRepository
                .findByTeacherIdAndDateAndSubjectIdAndSchoolClassId(
                        teacher.getId(), workHour.getDate(), subjectKey, classKey)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new BusinessException("Une autre saisie existe déjà pour ce professeur, "
                            + "cette date, cette matière et cette classe.");
                });

        // Le tarif appliqué est celui valide à la date de la saisie
        workHour.setHourlyRateApplied(resolveRate(teacher.getId(), workHour.getDate()));
        TeacherWorkHour saved = workHourRepository.save(workHour);
        auditService.log("HOURS_UPDATE", "TeacherWorkHour", id,
                teacher.getFullName() + " : correction → " + saved.getHours() + " h le " + saved.getDate(),
                httpRequest);
        return TeacherWorkHourResponse.from(saved);
    }
}

