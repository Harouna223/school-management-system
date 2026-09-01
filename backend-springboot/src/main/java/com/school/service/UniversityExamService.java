package com.school.service;

import com.school.entity.CourseUnit;
import com.school.entity.Room;
import com.school.entity.Teacher;
import com.school.entity.UniversityExam;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.CourseUnitRepository;
import com.school.repository.RoomRepository;
import com.school.repository.TeacherRepository;
import com.school.repository.UniversityExamRepository;
import com.school.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Calendrier des examens universitaires : planification des épreuves,
 * affectation des salles et des surveillants, détection de conflits.
 */
@Service
@RequiredArgsConstructor
public class UniversityExamService {

    private final UniversityExamRepository examRepository;
    private final CourseUnitRepository courseUnitRepository;
    private final RoomRepository roomRepository;
    private final TeacherRepository teacherRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<UniversityExam> listByField(Long fieldId) {
        return fieldId != null ? examRepository.findByCourseUnitUeFieldIdOrderByDateAsc(fieldId)
                : examRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<UniversityExam> listByDate(LocalDate date) {
        return date != null ? examRepository.findByDateOrderByStartTimeAsc(date)
                : examRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<UniversityExam> listBySemester(String semester) {
        return semester != null ? examRepository.findBySemesterOrderByDateAsc(semester)
                : examRepository.findAll();
    }

    @Transactional
    public UniversityExam save(UniversityExam exam, HttpServletRequest httpRequest) {
        if (exam.getCourseUnit() == null || exam.getCourseUnit().getId() == null) {
            throw new BusinessException("L'EC est obligatoire");
        }
        CourseUnit courseUnit = courseUnitRepository.findById(exam.getCourseUnit().getId())
                .orElseThrow(() -> ResourceNotFoundException.of("EC", exam.getCourseUnit().getId()));
        exam.setCourseUnit(courseUnit);
        if (exam.getRoom() != null && exam.getRoom().getId() != null) {
            Room room = roomRepository.findById(exam.getRoom().getId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Salle", exam.getRoom().getId()));
            exam.setRoom(room);
        }
        if (exam.getSupervisor() != null && exam.getSupervisor().getId() != null) {
            Teacher supervisor = teacherRepository.findById(exam.getSupervisor().getId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Enseignant", exam.getSupervisor().getId()));
            exam.setSupervisor(supervisor);
        }
        checkConflicts(exam);
        UniversityExam saved = examRepository.save(exam);
        auditService.log("CREATE", "UniversityExam", saved.getId(),
                "Examen " + courseUnit.getCode() + " le " + saved.getDate(), httpRequest);
        return saved;
    }

    @Transactional
    public void delete(Long id, HttpServletRequest httpRequest) {
        UniversityExam exam = examRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Examen", id));
        auditService.log("DELETE", "UniversityExam", id, "Suppression examen " + exam.getCourseUnit().getCode(), httpRequest);
        examRepository.delete(exam);
    }

    /**
     * Détecte les conflits de salle ou de surveillant sur le même créneau.
     */
    private void checkConflicts(UniversityExam exam) {
        if (exam.getRoom() == null && exam.getSupervisor() == null) return;
        List<UniversityExam> sameDay = examRepository.findByDateOrderByStartTimeAsc(exam.getDate());
        for (UniversityExam other : sameDay) {
            if (other.getId() != null && other.getId().equals(exam.getId())) continue;
            if (!overlaps(exam, other)) continue;
            if (exam.getRoom() != null && other.getRoom() != null
                    && exam.getRoom().getId().equals(other.getRoom().getId())) {
                throw new BusinessException("Conflit : la salle " + exam.getRoom().getName()
                        + " est déjà occupée à ce créneau (EC " + other.getCourseUnit().getCode() + ")");
            }
            if (exam.getSupervisor() != null && other.getSupervisor() != null
                    && exam.getSupervisor().getId().equals(other.getSupervisor().getId())) {
                throw new BusinessException("Conflit : le surveillant "
                        + exam.getSupervisor().getFirstName() + " " + exam.getSupervisor().getLastName()
                        + " est déjà affecté à ce créneau (EC " + other.getCourseUnit().getCode() + ")");
            }
        }
    }

    private boolean overlaps(UniversityExam a, UniversityExam b) {
        return a.getStartTime().isBefore(b.getEndTime())
                && b.getStartTime().isBefore(a.getEndTime());
    }
}