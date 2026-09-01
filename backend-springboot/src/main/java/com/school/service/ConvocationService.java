package com.school.service;

import com.school.dto.request.ConvocationRequest;
import com.school.dto.response.ConvocationResponse;
import com.school.entity.Convocation;
import com.school.entity.Student;
import com.school.enums.ConvocationStatus;
import com.school.enums.NotificationType;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.ConvocationRepository;
import com.school.repository.StudentRepository;
import com.school.utils.SecurityUtils;
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
 * Gestion des convocations scolaires et universitaires.
 * Notifie automatiquement le parent lié à l'élève concerné.
 */
@Service
@RequiredArgsConstructor
public class ConvocationService {

    private final ConvocationRepository convocationRepository;
    private final StudentRepository studentRepository;
    private final NotificationService notificationService;
    private final WhatsAppService whatsappService;
    private final AuditService auditService;

    private static final AtomicLong SEQUENCE = new AtomicLong(System.currentTimeMillis() % 100000);

    @Transactional
    public ConvocationResponse create(ConvocationRequest request, HttpServletRequest httpRequest) {
        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> ResourceNotFoundException.of("Élève", request.getStudentId()));
        String reference = "CONV-" + String.format("%06d", SEQUENCE.incrementAndGet());
        Convocation convocation = Convocation.builder()
                .reference(reference)
                .student(student)
                .context(request.getContext())
                .subject(request.getSubject())
                .message(request.getMessage())
                .date(request.getDate())
                .time(request.getTime())
                .location(request.getLocation())
                .status(ConvocationStatus.SENT)
                .createdBy(SecurityUtils.currentUser())
                .build();
        Convocation saved = convocationRepository.save(convocation);
        notifyParent(student, saved);
        auditService.log("CREATE", "Convocation", saved.getId(),
                "Convocation " + saved.getReference() + " pour " + student.getFullName(), httpRequest);
        return ConvocationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<ConvocationResponse> listByStudent(Long studentId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return convocationRepository.findByStudentId(studentId, pageable).map(ConvocationResponse::from);
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

    private void notifyParent(Student student, Convocation convocation) {
        if (student.getParent() == null || student.getParent().getUser() == null) return;
        try {
            notificationService.notify(
                    student.getParent().getUser(),
                    "Convocation : " + convocation.getSubject(),
                    "Date : " + convocation.getDate() + " à " + convocation.getTime().toLocalTime()
                            + " — Lieu : " + convocation.getLocation(),
                    NotificationType.WARNING);
        } catch (Exception ignored) {
            // la notification ne bloque jamais la création de la convocation
        }
        try {
            whatsappService.sendConvocation(student.getParent(), student, convocation.getSubject(),
                    convocation.getDate(), convocation.getTime().toLocalTime().toString(),
                    convocation.getLocation());
        } catch (Exception ignored) {
            // l'envoi WhatsApp est optionnel et ne bloque jamais le flux
        }
    }
}