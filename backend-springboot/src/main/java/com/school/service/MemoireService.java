package com.school.service;

import com.school.dto.request.MemoireRequest;
import com.school.dto.response.MemoireResponse;
import com.school.entity.Memoire;
import com.school.entity.Student;
import com.school.entity.Teacher;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.MemoireRepository;
import com.school.repository.StudentRepository;
import com.school.repository.TeacherRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gestion des mémoires et soutenances universitaires.
 *
 * <p>Le service expose des {@link MemoireResponse} pour la même raison que
 * {@link StageService} : les rattachements paresseux ne sont lisibles que
 * dans la transaction.</p>
 */
@Service
@RequiredArgsConstructor
public class MemoireService {

    private final MemoireRepository memoireRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<MemoireResponse> listAll() {
        return memoireRepository.findAllByOrderByDefenseDateDesc().stream()
                .map(MemoireResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MemoireResponse> listByStudent(Long studentId) {
        return memoireRepository.findByStudentIdOrderByDefenseDateDesc(studentId).stream()
                .map(MemoireResponse::from)
                .toList();
    }

    @Transactional
    public MemoireResponse create(MemoireRequest request, HttpServletRequest httpRequest) {
        Student student = requireStudent(request);
        Teacher director = resolveDirector(request);
        Memoire memoire = Memoire.builder()
                .student(student)
                .subject(request.getSubject().trim())
                .director(director)
                .defenseDate(request.getDefenseDate())
                .jury(request.getJury())
                .defenseLocation(request.getDefenseLocation())
                .grade(request.getGrade())
                .decision(request.getDecision())
                .status(request.getStatus())
                .build();
        Memoire saved = memoireRepository.save(memoire);
        auditService.log("CREATE", "Memoire", saved.getId(),
                "Mémoire : " + saved.getSubject(), httpRequest);
        return MemoireResponse.from(saved);
    }

    /**
     * Modification partielle : seuls les champs transmis sont remplacés, ce qui
     * permet de compléter un mémoire (jury, note, décision) sans réécrire le reste.
     */
    @Transactional
    public MemoireResponse update(Long id, MemoireRequest request, HttpServletRequest httpRequest) {
        Memoire existing = memoireRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Mémoire", id));
        if (request.getSubject() != null && !request.getSubject().isBlank()) {
            existing.setSubject(request.getSubject().trim());
        }
        if (request.getDefenseDate() != null) existing.setDefenseDate(request.getDefenseDate());
        if (request.getJury() != null) existing.setJury(request.getJury());
        if (request.getDefenseLocation() != null) existing.setDefenseLocation(request.getDefenseLocation());
        if (request.getGrade() != null) existing.setGrade(request.getGrade());
        if (request.getDecision() != null) existing.setDecision(request.getDecision());
        if (request.getStatus() != null) existing.setStatus(request.getStatus());
        if (request.getDirectorId() != null) {
            existing.setDirector(teacherRepository.findById(request.getDirectorId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Enseignant", request.getDirectorId())));
        }
        Memoire saved = memoireRepository.save(existing);
        auditService.log("UPDATE", "Memoire", id, "Mise à jour mémoire", httpRequest);
        return MemoireResponse.from(saved);
    }

    @Transactional
    public void delete(Long id, HttpServletRequest httpRequest) {
        Memoire memoire = memoireRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Mémoire", id));
        auditService.log("DELETE", "Memoire", id, "Suppression mémoire", httpRequest);
        memoireRepository.delete(memoire);
    }

    private Student requireStudent(MemoireRequest request) {
        if (request.getSubject() == null || request.getSubject().isBlank()) {
            throw new BusinessException("Le sujet est obligatoire");
        }
        if (request.getStudentId() == null) {
            throw new BusinessException("L'étudiant est obligatoire");
        }
        return studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> ResourceNotFoundException.of("Élève", request.getStudentId()));
    }

    private Teacher resolveDirector(MemoireRequest request) {
        if (request.getDirectorId() == null) {
            return null;
        }
        return teacherRepository.findById(request.getDirectorId())
                .orElseThrow(() -> ResourceNotFoundException.of("Enseignant", request.getDirectorId()));
    }
}
