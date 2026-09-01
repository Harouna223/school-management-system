package com.school.service;

import com.school.dto.response.AcademicTimelineEntry;
import com.school.dto.response.BulletinResponse;
import com.school.entity.EnrollmentHistory;
import com.school.entity.Parent;
import com.school.entity.Student;
import com.school.entity.StudentHistory;
import com.school.entity.User;
import com.school.enums.EnrollmentStatus;
import com.school.enums.StudentHistoryAction;
import com.school.exception.BusinessException;
import com.school.repository.AttendanceRepository;
import com.school.repository.BulletinRepository;
import com.school.repository.EnrollmentHistoryRepository;
import com.school.repository.GradeRepository;
import com.school.repository.LmdEnrollmentRepository;
import com.school.repository.ParentRepository;
import com.school.repository.StudentHistoryRepository;
import com.school.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Tests de l'espace parent : timeline consolidée et contrôle d'appartenance.
 */
@ExtendWith(MockitoExtension.class)
class ParentServiceTest {

    @Mock
    private ParentRepository parentRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private BulletinRepository bulletinRepository;
    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private GradeRepository gradeRepository;
    @Mock
    private StudentHistoryRepository studentHistoryRepository;
    @Mock
    private EnrollmentHistoryRepository enrollmentHistoryRepository;
    @Mock
    private LmdEnrollmentRepository lmdEnrollmentRepository;
    @Mock
    private LmdService lmdService;

    private ParentService service;

    @BeforeEach
    void setUp() {
        service = new ParentService(parentRepository, studentRepository, bulletinRepository,
                attendanceRepository, gradeRepository, studentHistoryRepository,
                enrollmentHistoryRepository, lmdEnrollmentRepository, lmdService);
    }

    private User user(Long id) {
        return User.builder().id(id).username("parent" + id).build();
    }

    private Parent parent(Long id, Long userId) {
        return Parent.builder().id(id).user(user(id)).build();
    }

    private Student child(Long id, Parent parent) {
        return Student.builder().id(id).firstName("Enfant").lastName("Test" + id)
                .parent(parent).build();
    }

    @Test
    void childBulletinsRejectsWhenNotOwned() {
        Parent parent = parent(10L, 1L);
        Student otherChild = child(30L, parent(20L, 2L));
        when(parentRepository.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(parent));
        when(studentRepository.findById(30L)).thenReturn(Optional.of(otherChild));

        try (MockedStatic<com.school.utils.SecurityUtils> utils =
                     mockStatic(com.school.utils.SecurityUtils.class)) {
            utils.when(com.school.utils.SecurityUtils::currentUserId).thenReturn(1L);
            assertThatThrownBy(() -> service.childBulletins(30L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("n'est pas lié");
        }
    }

    @Test
    void childBulletinsSucceedsForOwnedChild() {
        Parent parent = parent(10L, 1L);
        Student ownChild = child(20L, parent);
        when(parentRepository.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(parent));
        when(studentRepository.findById(20L)).thenReturn(Optional.of(ownChild));
        when(bulletinRepository.findByStudentId(20L)).thenReturn(List.of());

        try (MockedStatic<com.school.utils.SecurityUtils> utils =
                     mockStatic(com.school.utils.SecurityUtils.class)) {
            utils.when(com.school.utils.SecurityUtils::currentUserId).thenReturn(1L);
            List<BulletinResponse> result = service.childBulletins(20L);
            assertThat(result).isEmpty();
        }
    }

    @Test
    void childTimelineMergesSchoolAndUniversity() {
        Parent parent = parent(10L, 1L);
        Student ownChild = child(20L, parent);
        when(parentRepository.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(parent));
        when(studentRepository.findById(20L)).thenReturn(Optional.of(ownChild));

        StudentHistory school = StudentHistory.builder()
                .id(1L).student(ownChild).action(StudentHistoryAction.TRANSFER)
                .fromClass("6ème").toClass("5ème").createdAt(LocalDateTime.of(2023, 9, 1, 10, 0)).build();
        EnrollmentHistory university = EnrollmentHistory.builder()
                .id(2L).student(ownChild).fromLevel("L1").toLevel("L2")
                .academicYear("2025-2026").enrollmentStatus(EnrollmentStatus.INSCRIT)
                .createdAt(LocalDateTime.of(2025, 9, 1, 10, 0)).build();

        when(studentHistoryRepository.findByStudentIdOrderByCreatedAtDesc(20L)).thenReturn(List.of(school));
        when(enrollmentHistoryRepository.findByStudentIdOrderByCreatedAtDesc(20L)).thenReturn(List.of(university));

        try (MockedStatic<com.school.utils.SecurityUtils> utils =
                     mockStatic(com.school.utils.SecurityUtils.class)) {
            utils.when(com.school.utils.SecurityUtils::currentUserId).thenReturn(1L);
            List<AcademicTimelineEntry> timeline = service.childTimeline(20L);
            assertThat(timeline).hasSize(2);
            // Tri décroissant : université (2025) en premier, école (2023) ensuite
            assertThat(timeline.get(0).getContext()).isEqualTo("UNIVERSITY");
            assertThat(timeline.get(1).getContext()).isEqualTo("SCHOOL");
            assertThat(timeline.get(0).getAction()).isEqualTo("ENROLLMENT_INSCRIT");
            assertThat(timeline.get(0).getFromLabel()).isEqualTo("L1");
            assertThat(timeline.get(0).getToLabel()).isEqualTo("L2");
        }
    }

    @Test
    void getOwnedChildThrowsForNonOwned() {
        Parent parent = parent(10L, 1L);
        Student other = child(99L, parent(20L, 2L));
        when(parentRepository.findByUserIdOrderByIdAsc(1L)).thenReturn(List.of(parent));
        when(studentRepository.findById(99L)).thenReturn(Optional.of(other));

        try (MockedStatic<com.school.utils.SecurityUtils> utils =
                     mockStatic(com.school.utils.SecurityUtils.class)) {
            utils.when(com.school.utils.SecurityUtils::currentUserId).thenReturn(1L);
            assertThatThrownBy(() -> service.getOwnedChild(99L))
                    .isInstanceOf(BusinessException.class);
        }
    }
}