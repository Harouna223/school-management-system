package com.school.service;

import com.school.dto.request.ConvocationRequest;
import com.school.dto.response.ConvocationResponse;
import com.school.entity.Convocation;
import com.school.entity.Parent;
import com.school.entity.Student;
import com.school.entity.Teacher;
import com.school.entity.User;
import com.school.enums.ConvocationStatus;
import com.school.enums.ConvocationTarget;
import com.school.enums.NotificationType;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.ConvocationRepository;
import com.school.repository.ParentRepository;
import com.school.repository.StudentRepository;
import com.school.repository.TeacherRepository;
import com.school.utils.SecurityUtils;
import com.school.utils.Pagination;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gestion des convocations : élèves, parents d'élèves et enseignants.
 *
 * <p>La cible ({@link ConvocationTarget}) détermine le rattachement exigé et
 * qui est prévenu : notification interne (cloche) pour tous, et message
 * WhatsApp lorsqu'un numéro est disponible (élève via son parent, ou parent
 * convoqué directement). Une convocation d'enseignant est notifiée dans
 * l'application uniquement : {@code message_logs} ne porte pas de rattachement
 * enseignant.</p>
 */
@Service
@RequiredArgsConstructor
public class ConvocationService {

    private final ConvocationRepository convocationRepository;
    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;
    private final TeacherRepository teacherRepository;
    private final NotificationService notificationService;
    private final WhatsAppService whatsappService;
    private final AuditService auditService;

    private static final AtomicLong SEQUENCE = new AtomicLong(System.currentTimeMillis() % 100000);

    @Transactional
    public ConvocationResponse create(ConvocationRequest request, HttpServletRequest httpRequest) {
        ConvocationTarget target = request.getTarget() == null
                ? ConvocationTarget.ELEVE : request.getTarget();
        String reference = "CONV-" + String.format("%06d", SEQUENCE.incrementAndGet());

        Convocation.ConvocationBuilder builder = Convocation.builder()
                .reference(reference)
                .target(target)
                .context(request.getContext())
                .subject(request.getSubject())
                .message(request.getMessage())
                .date(request.getDate())
                .time(request.getTime())
                .location(request.getLocation())
                .status(ConvocationStatus.SENT)
                .createdBy(SecurityUtils.currentUser());

        String destinataire;
        switch (target) {
            case ELEVE -> {
                Student student = studentRepository.findById(requiredId(request.getStudentId(), "L'élève"))
                        .orElseThrow(() -> ResourceNotFoundException.of("Élève", request.getStudentId()));
                builder.student(student);
                destinataire = "élève " + student.getFullName();
            }
            case PARENT -> {
                Parent parent = parentRepository.findById(requiredId(request.getParentId(), "Le parent"))
                        .orElseThrow(() -> ResourceNotFoundException.of("Parent", request.getParentId()));
                builder.parent(parent);
                destinataire = "parent " + parent.getFirstName() + " " + parent.getLastName();
            }
            default -> {
                Teacher teacher = teacherRepository.findById(requiredId(request.getTeacherId(), "L'enseignant"))
                        .orElseThrow(() -> ResourceNotFoundException.of("Enseignant", request.getTeacherId()));
                builder.teacher(teacher);
                destinataire = "enseignant " + teacher.getFullName();
            }
        }

        Convocation saved = convocationRepository.save(builder.build());
        notifyTarget(saved);
        auditService.log("CREATE", "Convocation", saved.getId(),
                "Convocation " + saved.getReference() + " pour " + destinataire, httpRequest);
        return ConvocationResponse.from(saved);
    }

    /**
     * Liste paginée. Sans filtre, toutes les convocations sont renvoyées : après
     * l'élargissement aux parents et aux enseignants, l'administration doit
     * pouvoir consulter l'ensemble (auparavant la requête « sans élève »
     * interrogeait {@code student_id IS NULL} et ne renvoyait rien).
     *
     * <p>Le bornage par élève est appliqué par le contrôleur
     * ({@link AccessControlService}).</p>
     */
    @Transactional(readOnly = true)
    public Page<ConvocationResponse> list(Long studentId, Long parentId, Long teacherId,
                                          ConvocationTarget target, int page, int size) {
        Pageable pageable = Pagination.of(page, size, Sort.by("createdAt").descending());
        Page<Convocation> result;
        if (studentId != null) {
            result = convocationRepository.findByStudentId(studentId, pageable);
        } else if (parentId != null) {
            result = convocationRepository.findByParentId(parentId, pageable);
        } else if (teacherId != null) {
            result = convocationRepository.findByTeacherId(teacherId, pageable);
        } else if (target != null) {
            result = convocationRepository.findByTarget(target, pageable);
        } else {
            result = convocationRepository.findAll(pageable);
        }
        return result.map(ConvocationResponse::from);
    }

    @Transactional(readOnly = true)
    public List<ConvocationResponse> listByStudentAll(Long studentId) {
        return convocationRepository.findByStudentIdOrderByCreatedAtDesc(studentId)
                .stream().map(ConvocationResponse::from).toList();
    }

    @Transactional
    public void delete(Long id, HttpServletRequest httpRequest) {
        Convocation convocation = convocationRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Convocation", id));
        auditService.log("DELETE", "Convocation", id, "Suppression convocation " + convocation.getReference(), httpRequest);
        convocationRepository.delete(convocation);
    }

    /** L'identifiant du rattachement correspondant à la cible est obligatoire. */
    private Long requiredId(Long id, String label) {
        if (id == null) {
            throw new BusinessException(label + " est obligatoire pour cette cible de convocation");
        }
        return id;
    }

    /**
     * Notifie le destinataire de la convocation.
     *
     * <p>Pour un élève, le parent (tuteur) est prévenu comme avant
     * l'élargissement ; l'élève connecté reçoit en plus la notification.</p>
     */
    private void notifyTarget(Convocation convocation) {
        String titre = "Convocation : " + convocation.getSubject();
        String corps = "Date : " + convocation.getDate() + " à "
                + convocation.getTime().toLocalTime() + " — Lieu : " + convocation.getLocation();

        Student student = convocation.getStudent();
        if (student != null) {
            if (student.getUser() != null) {
                notify(student.getUser(), titre, corps);
            }
            Parent parent = student.getParent();
            if (parent != null) {
                if (parent.getUser() != null) {
                    notify(parent.getUser(), titre, corps);
                }
                try {
                    whatsappService.sendConvocation(parent, student, convocation.getSubject(),
                            convocation.getDate(), convocation.getTime().toLocalTime().toString(),
                            convocation.getLocation());
                } catch (Exception ignored) {
                    // l'envoi WhatsApp est optionnel et ne bloque jamais le flux
                }
            }
            return;
        }

        Parent parent = convocation.getParent();
        if (parent != null) {
            if (parent.getUser() != null) {
                notify(parent.getUser(), titre, corps);
            }
            try {
                whatsappService.sendConvocationToParent(parent, convocation.getSubject(),
                        convocation.getDate(), convocation.getTime().toLocalTime().toString(),
                        convocation.getLocation());
            } catch (Exception ignored) {
                // idem
            }
            return;
        }

        Teacher teacher = convocation.getTeacher();
        if (teacher != null && teacher.getUser() != null) {
            notify(teacher.getUser(), titre, corps);
        }
    }

    private void notify(User user, String titre, String corps) {
        try {
            notificationService.notify(user, titre, corps, NotificationType.WARNING);
        } catch (Exception ignored) {
            // la notification ne bloque jamais la création de la convocation
        }
    }
}
