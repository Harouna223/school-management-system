package com.school.service;

import com.school.dto.request.MessageReplyRequest;
import com.school.dto.response.MessageResponse;
import com.school.entity.Message;
import com.school.entity.User;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.MessageRepository;
import com.school.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Messagerie interne : ouverture (marquage lu), réponse et suppression.
 *
 * <p>Le point sensible est l'IDOR : un identifiant de message deviné ne doit
 * jamais donner accès à une conversation dont on n'est pas participant.</p>
 */
@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    private static final Long ALICE = 1L;
    private static final Long BOB = 2L;
    private static final Long TIERS = 99L;

    @Mock private MessageRepository messageRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @Mock private HttpServletRequest httpRequest;

    private MessageService service;

    @BeforeEach
    void setUp() {
        service = new MessageService(messageRepository, userRepository, auditService);
    }

    private static User user(Long id, String first, String last) {
        return User.builder().id(id).username(first.toLowerCase())
                .firstName(first).lastName(last).build();
    }

    /** Message d'Alice (1) adressé à Bob (2), non lu. */
    private Message message(Long id) {
        return Message.builder()
                .id(id)
                .sender(user(ALICE, "Alice", "Martin"))
                .recipient(user(BOB, "Bob", "Diallo"))
                .subject("Réunion")
                .content("Bonjour")
                .read(false)
                .build();
    }

    @Test
    @DisplayName("Le destinataire qui ouvre un message le marque lu (dirty checking, sans save)")
    void ouvertureParLeDestinataireMarqueLu() {
        Message m = message(10L);
        when(messageRepository.findById(10L)).thenReturn(Optional.of(m));

        MessageResponse response = service.open(10L, BOB);

        assertThat(m.isRead()).isTrue();
        assertThat(response.isRead()).isTrue();
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    @DisplayName("L'expéditeur qui rouvre son propre message ne le marque pas lu")
    void ouvertureParLExpediteurNeMarquePasLu() {
        Message m = message(11L);
        when(messageRepository.findById(11L)).thenReturn(Optional.of(m));

        service.open(11L, ALICE);

        assertThat(m.isRead()).isFalse();
    }

    @Test
    @DisplayName("Un tiers ne peut pas ouvrir un message qui ne le concerne pas")
    void ouvertureParUnTiersRefusee() {
        when(messageRepository.findById(12L)).thenReturn(Optional.of(message(12L)));

        assertThatThrownBy(() -> service.open(12L, TIERS))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Accès refusé");
    }

    @Test
    @DisplayName("Un tiers ne peut pas supprimer un message qui ne le concerne pas")
    void suppressionParUnTiersRefusee() {
        when(messageRepository.findById(13L)).thenReturn(Optional.of(message(13L)));

        assertThatThrownBy(() -> service.delete(13L, TIERS, httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Accès refusé");
        verify(messageRepository, never()).delete(any(Message.class));
    }

    @Test
    @DisplayName("Marquer lu un message adressé à quelqu'un d'autre est refusé")
    void marquageLuParUnTiersRefuse() {
        when(messageRepository.findById(14L)).thenReturn(Optional.of(message(14L)));

        assertThatThrownBy(() -> service.markAsRead(14L, TIERS))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("adressé");
    }

    @Test
    @DisplayName("La réponse part vers l'auteur d'origine, avec le préfixe « Re: »")
    void reponseVaVersLAuteurDOrigine() {
        Message original = message(15L);
        when(messageRepository.findById(15L)).thenReturn(Optional.of(original));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<com.school.utils.SecurityUtils> utils =
                     mockStatic(com.school.utils.SecurityUtils.class)) {
            utils.when(com.school.utils.SecurityUtils::currentUser).thenReturn(user(BOB, "Bob", "Diallo"));

            MessageResponse response = service.reply(15L,
                    MessageReplyRequest.builder().content("Bien reçu").build(), httpRequest);

            assertThat(response.getSenderId()).isEqualTo(BOB);
            assertThat(response.getRecipientId()).isEqualTo(ALICE);
            assertThat(response.getSubject()).isEqualTo("Re: Réunion");
            assertThat(response.getContent()).isEqualTo("Bien reçu");
        }
    }

    @Test
    @DisplayName("Répondre à son propre message envoyé écrit au destinataire d'origine")
    void reponseAUnMessageEnvoyeVaVersLeDestinataire() {
        Message original = message(16L);
        when(messageRepository.findById(16L)).thenReturn(Optional.of(original));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<com.school.utils.SecurityUtils> utils =
                     mockStatic(com.school.utils.SecurityUtils.class)) {
            utils.when(com.school.utils.SecurityUtils::currentUser).thenReturn(user(ALICE, "Alice", "Martin"));

            MessageResponse response = service.reply(16L,
                    MessageReplyRequest.builder().content("Relance").build(), httpRequest);

            assertThat(response.getSenderId()).isEqualTo(ALICE);
            assertThat(response.getRecipientId()).isEqualTo(BOB);
        }
    }

    @Test
    @DisplayName("Répondre à un message inexistant produit un 404 métier")
    void reponseAMessageInexistant() {
        when(messageRepository.findById(404L)).thenReturn(Optional.empty());

        try (MockedStatic<com.school.utils.SecurityUtils> utils =
                     mockStatic(com.school.utils.SecurityUtils.class)) {
            utils.when(com.school.utils.SecurityUtils::currentUser).thenReturn(user(BOB, "Bob", "Diallo"));

            assertThatThrownBy(() -> service.reply(404L,
                    MessageReplyRequest.builder().content("?").build(), httpRequest))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
