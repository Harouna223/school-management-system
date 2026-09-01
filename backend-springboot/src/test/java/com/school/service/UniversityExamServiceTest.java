package com.school.service;

import com.school.entity.CourseUnit;
import com.school.entity.Room;
import com.school.entity.Teacher;
import com.school.entity.UniversityExam;
import com.school.exception.BusinessException;
import com.school.repository.CourseUnitRepository;
import com.school.repository.RoomRepository;
import com.school.repository.TeacherRepository;
import com.school.repository.UniversityExamRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests du calendrier des examens universitaires, notamment la détection
 * de conflits de salle et de surveillant.
 */
@ExtendWith(MockitoExtension.class)
class UniversityExamServiceTest {

    @Mock
    private UniversityExamRepository examRepository;
    @Mock
    private CourseUnitRepository courseUnitRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private TeacherRepository teacherRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private HttpServletRequest httpRequest;

    private UniversityExamService service;

    @BeforeEach
    void setUp() {
        service = new UniversityExamService(examRepository, courseUnitRepository,
                roomRepository, teacherRepository, auditService);
    }

    private CourseUnit ec(Long id) {
        return CourseUnit.builder().id(id).code("ALGO" + id).name("Algorithmique " + id).build();
    }

    private Room room(Long id) {
        return Room.builder().id(id).name("Salle " + id).build();
    }

    private Teacher teacher(Long id) {
        return Teacher.builder().id(id).firstName("Jean").lastName("Prof" + id).build();
    }

    private UniversityExam exam(Long id, Long ecId, Long roomId, Long supervisorId,
                                LocalDate date, LocalTime start, LocalTime end) {
        return UniversityExam.builder()
                .id(id).courseUnit(ec(ecId)).room(room(roomId) != null ? room(roomId) : null)
                .supervisor(supervisorId != null ? teacher(supervisorId) : null)
                .date(date).startTime(start).endTime(end).session(1).build();
    }

    @Test
    void saveDetectsRoomConflict() {
        CourseUnit ec = ec(1L);
        LocalDate date = LocalDate.of(2026, 9, 15);
        when(courseUnitRepository.findById(1L)).thenReturn(Optional.of(ec));
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room(1L)));
        when(teacherRepository.findById(3L)).thenReturn(Optional.of(teacher(3L)));
        List<UniversityExam> existing = List.of(
                exam(5L, 2L, 1L, 2L, date, LocalTime.of(9, 0), LocalTime.of(11, 0)));
        when(examRepository.findByDateOrderByStartTimeAsc(date)).thenReturn(existing);

        UniversityExam newExam = exam(null, 1L, 1L, 3L, date, LocalTime.of(10, 0), LocalTime.of(12, 0));

        assertThatThrownBy(() -> service.save(newExam, httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Conflit");
    }

    @Test
    void saveDetectsSupervisorConflict() {
        CourseUnit ec = ec(1L);
        LocalDate date = LocalDate.of(2026, 9, 15);
        when(courseUnitRepository.findById(1L)).thenReturn(Optional.of(ec));
        when(roomRepository.findById(3L)).thenReturn(Optional.of(room(3L)));
        when(teacherRepository.findById(1L)).thenReturn(Optional.of(teacher(1L)));
        List<UniversityExam> existing = List.of(
                exam(5L, 2L, 2L, 1L, date, LocalTime.of(9, 0), LocalTime.of(11, 0)));
        when(examRepository.findByDateOrderByStartTimeAsc(date)).thenReturn(existing);

        UniversityExam newExam = exam(null, 1L, 3L, 1L, date, LocalTime.of(10, 0), LocalTime.of(12, 0));

        assertThatThrownBy(() -> service.save(newExam, httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Conflit");
    }

    @Test
    void saveSucceedsWhenNoConflict() {
        CourseUnit ec = ec(1L);
        LocalDate date = LocalDate.of(2026, 9, 15);
        when(courseUnitRepository.findById(1L)).thenReturn(Optional.of(ec));
        when(examRepository.findByDateOrderByStartTimeAsc(date)).thenReturn(List.of());
        when(examRepository.save(any(UniversityExam.class))).thenAnswer(inv -> inv.getArgument(0));

        UniversityExam exam = exam(null, 1L, null, null, date, LocalTime.of(10, 0), LocalTime.of(12, 0));

        UniversityExam saved = service.save(exam, httpRequest);
        assertThat(saved.getDate()).isEqualTo(date);
        assertThat(saved.getCourseUnit().getId()).isEqualTo(1L);
        verify(examRepository).save(any(UniversityExam.class));
    }

    @Test
    void saveRejectsMissingCourseUnit() {
        UniversityExam exam = UniversityExam.builder().build();
        assertThatThrownBy(() -> service.save(exam, httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("EC");
    }

    @Test
    void deleteExisting() {
        UniversityExam exam = UniversityExam.builder().id(1L).courseUnit(ec(1L)).build();
        when(examRepository.findById(1L)).thenReturn(Optional.of(exam));
        service.delete(1L, httpRequest);
        verify(examRepository).delete(exam);
    }
}