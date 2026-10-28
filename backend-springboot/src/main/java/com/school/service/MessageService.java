package com.school.service;

import com.school.dto.request.MessageReplyRequest;
import com.school.dto.request.MessageRequest;
import com.school.dto.response.MessageResponse;
import com.school.dto.response.PageResponse;
import com.school.entity.Message;
import com.school.entity.User;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.MessageRepository;
import com.school.repository.UserRepository;
import com.school.utils.SecurityUtils;
import com.school.utils.Pagination;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Messagerie interne entre utilisateurs.
 *
 * <p>Toute opération portant sur un message précis (lecture, marquage lu,
 * réponse, suppression) est bornée aux deux participants : l'expéditeur et le
 * destinataire. Un identifiant deviné ne donne donc accès à rien.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MessageService {

    /** Longueur maximale de l'objet ({@code messages.subject VARCHAR(150)}). */
    private static final int SUBJECT_MAX = 150;

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional
    public MessageResponse send(MessageRequest request, HttpServletRequest httpRequest) {
        User sender = SecurityUtils.currentUser();
        User recipient = userRepository.findById(request.getRecipientId())
                .orElseThrow(() -> ResourceNotFoundException.of("Utilisateur", request.getRecipientId()));
        Message message = Message.builder()
                .sender(sender)
                .recipient(recipient)
                .subject(request.getSubject())
                .content(request.getContent())
                .build();
        Message saved = messageRepository.save(message);
        auditService.log("MESSAGE", "Message", saved.getId(),
                "Message envoyé à " + recipient.getUsername(), httpRequest);
        return MessageResponse.from(saved);
    }

    /**
     * Ouvre un message reçu ou envoyé et le marque lu s'il était non lu.
     *
     * <p>Le marquage repose sur le <em>dirty checking</em> de la transaction :
     * aucun {@code save()} explicite n'est nécessaire.</p>
     */
    @Transactional
    public MessageResponse open(Long id, Long userId) {
        Message message = messageRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Message", id));
        assertParticipant(message, userId);
        if (message.getRecipient().getId().equals(userId)) {
            message.setRead(true);
        }
        return MessageResponse.from(message);
    }

    /** Marque un message reçu comme lu (appelé à l'ouverture du détail). */
    @Transactional
    public void markAsRead(Long id, Long userId) {
        Message message = messageRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Message", id));
        if (!message.getRecipient().getId().equals(userId)) {
            throw new BusinessException("Accès refusé : ce message ne vous est pas adressé");
        }
        message.setRead(true);
    }

    /** Supprime un message : réservé à ses deux participants. */
    @Transactional
    public void delete(Long id, Long userId, HttpServletRequest httpRequest) {
        Message message = messageRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Message", id));
        assertParticipant(message, userId);
        auditService.log("DELETE", "Message", id, "Message supprimé", httpRequest);
        messageRepository.delete(message);
    }

    /**
     * Répond à un message : le destinataire devient l'autre participant.
     * Répondre à son propre message envoyé écrit donc au destinataire d'origine.
     */
    @Transactional
    public MessageResponse reply(Long id, MessageReplyRequest request, HttpServletRequest httpRequest) {
        User current = SecurityUtils.currentUser();
        Message original = messageRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Message", id));
        assertParticipant(original, current.getId());

        User recipient = original.getSender().getId().equals(current.getId())
                ? original.getRecipient() : original.getSender();
        String subject = original.getSubject().startsWith("Re:")
                ? original.getSubject() : "Re: " + original.getSubject();
        if (subject.length() > SUBJECT_MAX) {
            subject = subject.substring(0, SUBJECT_MAX);
        }

        Message reply = messageRepository.save(Message.builder()
                .sender(current)
                .recipient(recipient)
                .subject(subject)
                .content(request.getContent())
                .build());
        auditService.log("MESSAGE", "Message", reply.getId(),
                "Réponse à " + recipient.getUsername(), httpRequest);
        return MessageResponse.from(reply);
    }

    public PageResponse<MessageResponse> inbox(Long userId, int page, int size) {
        Pageable pageable = Pagination.of(page, size);
        Page<Message> result = messageRepository.findByRecipientIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.from(result, MessageResponse::from);
    }

    public PageResponse<MessageResponse> sent(Long userId, int page, int size) {
        Pageable pageable = Pagination.of(page, size);
        Page<Message> result = messageRepository.findBySenderIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.from(result, MessageResponse::from);
    }

    public long unreadCount(Long userId) {
        return messageRepository.countByRecipientIdAndReadFalse(userId);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        messageRepository.markAllAsRead(userId);
    }

    /** Seuls l'expéditeur et le destinataire peuvent toucher au message. */
    private void assertParticipant(Message message, Long userId) {
        boolean participant = message.getSender().getId().equals(userId)
                || message.getRecipient().getId().equals(userId);
        if (!participant) {
            throw new BusinessException("Accès refusé : ce message ne vous appartient pas");
        }
    }
}
