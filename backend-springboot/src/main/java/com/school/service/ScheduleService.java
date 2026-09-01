package com.school.service;

import com.school.dto.request.ScheduleRequest;
import com.school.dto.response.ScheduleResponse;
import com.school.entity.Schedule;
import com.school.enums.DayOfWeek;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.ScheduleRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

/**
 * Module emploi du temps : génération, détection de conflits, vues classe/enseignant/salle.
 */
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final ClassService classService;
    private final SubjectService subjectService;
    private final TeacherService teacherService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<ScheduleResponse> listByClass(Long classId) {
        return scheduleRepository.findBySchoolClassId(classId).stream()
                .map(ScheduleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> listByTeacher(Long teacherId) {
        return scheduleRepository.findByTeacherId(teacherId).stream()
                .map(ScheduleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> listByRoom(Long roomId) {
        return scheduleRepository.findByRoomId(roomId).stream()
                .map(ScheduleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> listAll() {
        return scheduleRepository.findAll().stream()
                .map(ScheduleResponse::from).toList();
    }

    @Transactional
    public ScheduleResponse create(ScheduleRequest request, HttpServletRequest httpRequest) {
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new BusinessException("L'heure de fin doit être postérieure à l'heure de début");
        }
        checkConflict(request, null);
        Schedule schedule = build(request);
        Schedule saved = scheduleRepository.save(schedule);
        auditService.log("CREATE", "Schedule", saved.getId(), "Création créneau emploi du temps", httpRequest);
        return ScheduleResponse.from(saved);
    }

    @Transactional
    public ScheduleResponse update(Long id, ScheduleRequest request, HttpServletRequest httpRequest) {
        Schedule schedule = findById(id);
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new BusinessException("L'heure de fin doit être postérieure à l'heure de début");
        }
        checkConflict(request, id);
        schedule.setDayOfWeek(request.getDayOfWeek());
        schedule.setStartTime(request.getStartTime());
        schedule.setEndTime(request.getEndTime());
        schedule.setSchoolClass(classService.findById(request.getClassId()));
        schedule.setSubject(subjectService.findById(request.getSubjectId()));
        schedule.setTeacher(teacherService.findById(request.getTeacherId()));
        schedule.setRoom(request.getRoomId() != null ? classService.getRoom(request.getRoomId()) : null);
        auditService.log("UPDATE", "Schedule", id, "Modification créneau emploi du temps", httpRequest);
        return ScheduleResponse.from(scheduleRepository.save(schedule));
    }

    @Transactional
    public void delete(Long id, HttpServletRequest httpRequest) {
        Schedule schedule = findById(id);
        auditService.log("DELETE", "Schedule", id, "Suppression créneau emploi du temps", httpRequest);
        scheduleRepository.delete(schedule);
    }

    /**
     * Détection de conflits : même classe, même enseignant ou même salle sur un créneau chevauchant.
     */
    private void checkConflict(ScheduleRequest request, Long excludeId) {
        boolean conflict = scheduleRepository.isConflict(
                request.getDayOfWeek(), request.getStartTime(), request.getEndTime(),
                request.getClassId(), request.getTeacherId(), request.getRoomId(), excludeId);
        if (conflict) {
            throw new BusinessException("Conflit d'emploi du temps : la classe, l'enseignant ou la salle " +
                    "est déjà occupé(e) sur ce créneau (même jour, horaires chevauchants)");
        }
    }

    public boolean hasConflict(DayOfWeek day, LocalTime start, LocalTime end,
                               Long classId, Long teacherId, Long roomId, Long excludeId) {
        return scheduleRepository.isConflict(day, start, end, classId, teacherId, roomId, excludeId);
    }

    private Schedule build(ScheduleRequest request) {
        return Schedule.builder()
                .dayOfWeek(request.getDayOfWeek())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .schoolClass(classService.findById(request.getClassId()))
                .subject(subjectService.findById(request.getSubjectId()))
                .teacher(teacherService.findById(request.getTeacherId()))
                .room(request.getRoomId() != null ? classService.getRoom(request.getRoomId()) : null)
                .build();
    }

    public Schedule findById(Long id) {
        return scheduleRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Créneau", id));
    }
}