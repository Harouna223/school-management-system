package com.school.service;

import com.school.dto.request.MessageRequest;
import com.school.dto.response.MessageResponse;
import com.school.dto.response.PageResponse;
import com.school.entity.Message;
import com.school.entity.User;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.MessageRepository;
import com.school.repository.UserRepository;
import com.school.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Messagerie interne entre utilisateurs.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MessageService {

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

    public PageResponse<MessageResponse> inbox(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Message> result = messageRepository.findByRecipientIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.from(result, MessageResponse::from);
    }

    public PageResponse<MessageResponse> sent(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
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
}