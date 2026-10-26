package com.school.service;

import com.school.dto.request.BookRequest;
import com.school.dto.request.BorrowingRequest;
import com.school.dto.response.BookResponse;
import com.school.entity.Book;
import com.school.entity.Borrowing;
import com.school.entity.Student;
import com.school.enums.BorrowingStatus;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.BookRepository;
import com.school.repository.BorrowingRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Module bibliothèque : inventaire, emprunts, retours et marquage des retards.
 *
 * <p>Ce service n'avait aucun test alors qu'il concentre trois risques réels du
 * projet :</p>
 * <ul>
 *   <li><b>le job de nuit</b> {@code scheduledMarkOverdue()} — il a déjà échoué
 *       <em>silencieusement</em> (transaction en lecture seule ⇒ aucune écriture,
 *       aucune erreur, liste « en retard » vide à jamais) ;</li>
 *   <li><b>le double retour</b> — sans verrou pessimiste, deux retours simultanés
 *       passaient tous les deux le contrôle de statut et incrémentaient le stock
 *       deux fois ;</li>
 *   <li><b>les dates incohérentes</b> — un emprunt pouvait naître déjà en retard
 *       ({@code dueDate} antérieure à {@code borrowDate}).</li>
 * </ul>
 *
 * <p>Chaque invariant testé ici a été vérifié par mutation : casser volontairement
 * la ligne de production fait échouer le test correspondant. Un test qui n'a
 * jamais échoué ne prouve rien.</p>
 */
@ExtendWith(MockitoExtension.class)
class LibraryServiceTest {

    @Mock private BookRepository bookRepository;
    @Mock private BorrowingRepository borrowingRepository;
    @Mock private StudentService studentService;
    @Mock private AuditService auditService;
    @Mock private HttpServletRequest httpRequest;

    private LibraryService service;

    @BeforeEach
    void setUp() {
        service = new LibraryService(bookRepository, borrowingRepository, studentService, auditService);
    }

    // ---------- Fabriques ----------

    private static Book livre(Long id, int quantity, int available) {
        return Book.builder()
                .id(id)
                .title("Les Misérables")
                .author("Victor Hugo")
                .quantity(quantity)
                .available(available)
                .build();
    }

    private static Student eleve() {
        return Student.builder()
                .id(7L)
                .matricule("EL-2026-007")
                .firstName("Aïssata")
                .lastName("Traoré")
                .build();
    }

    private static Borrowing emprunt(Long id, Book book, BorrowingStatus status, LocalDate dueDate) {
        return Borrowing.builder()
                .id(id)
                .book(book)
                .student(eleve())
                .borrowDate(dueDate.minusDays(14))
                .dueDate(dueDate)
                .status(status)
                .build();
    }

    private static BorrowingRequest demande(Long bookId, LocalDate borrowDate, LocalDate dueDate) {
        return BorrowingRequest.builder()
                .bookId(bookId)
                .studentId(7L)
                .borrowDate(borrowDate)
                .dueDate(dueDate)
                .build();
    }

    // =====================================================================
    // Marquage des retards (job de nuit)
    // =====================================================================

    @Nested
    @DisplayName("Marquage des retards")
    class Retards {

        @Test
        @DisplayName("bascule en OVERDUE les emprunts renvoyés par le dépôt")
        void basculeEnOverdue() {
            Book book = livre(1L, 3, 2);
            Borrowing a = emprunt(10L, book, BorrowingStatus.BORROWED, LocalDate.now().minusDays(3));
            Borrowing b = emprunt(11L, book, BorrowingStatus.BORROWED, LocalDate.now().minusDays(1));
            when(borrowingRepository.findByStatusAndDueDateBefore(BorrowingStatus.BORROWED, LocalDate.now()))
                    .thenReturn(List.of(a, b));

            int count = service.markOverdue();

            assertThat(count).isEqualTo(2);
            assertThat(a.getStatus()).isEqualTo(BorrowingStatus.OVERDUE);
            assertThat(b.getStatus()).isEqualTo(BorrowingStatus.OVERDUE);
        }

        @Test
        @DisplayName("interroge le dépôt sur BORROWED et la date du jour")
        void interrogeAvecLeBonStatut() {
            // Le statut interrogé est le cœur du contrat : passer OVERDUE au lieu de
            // BORROWED ne marquerait plus jamais rien, sans lever d'erreur.
            when(borrowingRepository.findByStatusAndDueDateBefore(any(), any())).thenReturn(List.of());

            service.markOverdue();

            verify(borrowingRepository).findByStatusAndDueDateBefore(BorrowingStatus.BORROWED, LocalDate.now());
        }

        @Test
        @DisplayName("écrit les changements via saveAll")
        void persisteLesChangements() {
            Book book = livre(1L, 3, 2);
            Borrowing a = emprunt(10L, book, BorrowingStatus.BORROWED, LocalDate.now().minusDays(3));
            when(borrowingRepository.findByStatusAndDueDateBefore(any(), any())).thenReturn(List.of(a));

            service.markOverdue();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Borrowing>> captor = ArgumentCaptor.forClass(List.class);
            verify(borrowingRepository).saveAll(captor.capture());
            assertThat(captor.getValue()).containsExactly(a);
        }

        @Test
        @DisplayName("ne lève rien et renvoie 0 quand aucun emprunt n'est en retard")
        void aucunRetard() {
            when(borrowingRepository.findByStatusAndDueDateBefore(any(), any())).thenReturn(List.of());

            assertThat(service.markOverdue()).isZero();
            verify(borrowingRepository).saveAll(List.of());
        }

        @Test
        @DisplayName("la tâche planifiée porte @Scheduled ET @Transactional")
        void tachePlanifieeAnnotee() throws NoSuchMethodException {
            /*
             * Garde structurelle, volontairement basée sur la réflexion : le bug
             * d'origine n'était PAS détectable à l'exécution d'un test unitaire.
             * La classe est @Transactional(readOnly = true) ; sans @Transactional
             * sur cette méthode, l'auto-invocation (this.markOverdue()) contourne
             * le proxy, Hibernate passe le flush en MANUAL, et les changements
             * d'état ne sont JAMAIS écrits — sans aucune erreur. La tâche se
             * terminait donc « avec succès » chaque nuit en ne faisant rien.
             */
            Method m = LibraryService.class.getMethod("scheduledMarkOverdue");

            Scheduled scheduled = m.getAnnotation(Scheduled.class);
            assertThat(scheduled).as("@Scheduled obligatoire : sans lui, la méthode n'est jamais appelée")
                    .isNotNull();
            assertThat(scheduled.cron()).isEqualTo("0 0 4 * * *");

            Transactional tx = m.getAnnotation(Transactional.class);
            assertThat(tx).as("@Transactional obligatoire sur le point d'entrée @Scheduled").isNotNull();
            assertThat(tx.readOnly())
                    .as("readOnly = true réintroduirait exactement l'échec silencieux d'origine")
                    .isFalse();
        }

        @Test
        @DisplayName("markOverdue() est transactionnel en écriture")
        void markOverdueEcrit() throws NoSuchMethodException {
            Transactional tx = LibraryService.class.getMethod("markOverdue")
                    .getAnnotation(Transactional.class);
            assertThat(tx).isNotNull();
            assertThat(tx.readOnly()).isFalse();
        }

        @Test
        @DisplayName("la tâche planifiée écrit réellement en base")
        void tachePlanifieeEcrit() {
            Book book = livre(1L, 3, 2);
            Borrowing a = emprunt(10L, book, BorrowingStatus.BORROWED, LocalDate.now().minusDays(5));
            when(borrowingRepository.findByStatusAndDueDateBefore(any(), any())).thenReturn(List.of(a));

            service.scheduledMarkOverdue();

            assertThat(a.getStatus()).isEqualTo(BorrowingStatus.OVERDUE);
            verify(borrowingRepository).saveAll(List.of(a));
        }
    }

    // =====================================================================
    // Emprunt
    // =====================================================================

    @Nested
    @DisplayName("Emprunter un livre")
    class Emprunter {

        @Test
        @DisplayName("refuse une date de retour antérieure à la date d'emprunt")
        void refuseDateDeRetourAnterieure() {
            LocalDate jour = LocalDate.now().minusDays(1);
            BorrowingRequest request = demande(1L, jour, jour.minusDays(1));

            assertThatThrownBy(() -> service.borrow(request, httpRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("ne peut pas précéder");

            // Aucun accès au stock : l'emprunt ne doit pas avoir d'effet de bord.
            verifyNoInteractions(bookRepository, borrowingRepository, studentService, auditService);
        }

        @Test
        @DisplayName("accepte une date de retour le jour même de l'emprunt")
        void accepteRetourLeJourMeme() {
            LocalDate jour = LocalDate.now();
            Book book = livre(1L, 2, 1);
            when(bookRepository.findWithLockingById(1L)).thenReturn(Optional.of(book));
            when(studentService.findById(7L)).thenReturn(eleve());
            when(borrowingRepository.save(any(Borrowing.class))).thenAnswer(inv -> {
                Borrowing b = inv.getArgument(0);
                b.setId(50L);
                return b;
            });

            BookResponse.BorrowingResponse response = service.borrow(demande(1L, jour, jour), httpRequest);

            assertThat(response.getDueDate()).isEqualTo(jour);
            assertThat(response.getStatus()).isEqualTo(BorrowingStatus.BORROWED);
        }

        @Test
        @DisplayName("refuse une date d'emprunt dans le futur")
        void refuseDateDempruntFuture() {
            LocalDate demain = LocalDate.now().plusDays(1);
            BorrowingRequest request = demande(1L, demain, demain.plusDays(14));

            assertThatThrownBy(() -> service.borrow(request, httpRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("futur");

            verifyNoInteractions(bookRepository, borrowingRepository, studentService, auditService);
        }

        @Test
        @DisplayName("refuse un livre introuvable")
        void refuseLivreIntrouvable() {
            when(bookRepository.findWithLockingById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.borrow(
                    demande(99L, LocalDate.now(), LocalDate.now().plusDays(7)), httpRequest))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(borrowingRepository, never()).save(any());
        }

        @Test
        @DisplayName("refuse un livre sans exemplaire disponible")
        void refuseStockEpuise() {
            Book book = livre(1L, 2, 0);
            when(bookRepository.findWithLockingById(1L)).thenReturn(Optional.of(book));

            assertThatThrownBy(() -> service.borrow(
                    demande(1L, LocalDate.now(), LocalDate.now().plusDays(7)), httpRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Aucun exemplaire");

            assertThat(book.getAvailable()).isZero();
            verify(bookRepository, never()).save(any());
            verify(borrowingRepository, never()).save(any());
        }

        @Test
        @DisplayName("décrémente le stock, trace l'audit et verrouille le livre")
        void decrementeLeStock() {
            Book book = livre(1L, 3, 2);
            when(bookRepository.findWithLockingById(1L)).thenReturn(Optional.of(book));
            when(studentService.findById(7L)).thenReturn(eleve());
            when(borrowingRepository.save(any(Borrowing.class))).thenAnswer(inv -> {
                Borrowing b = inv.getArgument(0);
                b.setId(50L);
                return b;
            });

            BookResponse.BorrowingResponse response = service.borrow(
                    demande(1L, LocalDate.now().minusDays(1), LocalDate.now().plusDays(13)), httpRequest);

            assertThat(book.getAvailable()).isEqualTo(1);
            assertThat(book.getQuantity()).as("la quantité totale ne bouge pas").isEqualTo(3);
            verify(bookRepository).save(book);
            // Le verrou pessimiste est la seule protection contre la surréservation.
            verify(bookRepository, never()).findById(anyLong());
            verify(auditService).log(eq("BORROW"), eq("Borrowing"), eq(50L), anyString(), eq(httpRequest));

            assertThat(response.getId()).isEqualTo(50L);
            assertThat(response.getBookTitle()).isEqualTo("Les Misérables");
            assertThat(response.getStudentName()).isEqualTo("Aïssata Traoré");
            assertThat(response.getMatricule()).isEqualTo("EL-2026-007");
        }
    }

    // =====================================================================
    // Retour
    // =====================================================================

    @Nested
    @DisplayName("Retourner un livre")
    class Retourner {

        @Test
        @DisplayName("refuse un double retour sans toucher au stock")
        void refuseDoubleRetour() {
            Book book = livre(1L, 3, 3);
            Borrowing emprunt = emprunt(10L, book, BorrowingStatus.RETURNED, LocalDate.now().minusDays(3));
            when(borrowingRepository.findWithLockingById(10L)).thenReturn(Optional.of(emprunt));

            assertThatThrownBy(() -> service.returnBook(10L, httpRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("déjà été retourné");

            // L'essentiel : le stock ne doit pas être incrémenté une seconde fois.
            assertThat(book.getAvailable()).isEqualTo(3);
            verify(bookRepository, never()).save(any());
            verify(borrowingRepository, never()).save(any());
        }

        @Test
        @DisplayName("refuse un emprunt introuvable")
        void refuseEmpruntIntrouvable() {
            when(borrowingRepository.findWithLockingById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.returnBook(99L, httpRequest))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(bookRepository, never()).save(any());
        }

        @Test
        @DisplayName("passe l'emprunt en RETURNED, date le retour et rend l'exemplaire")
        void retourneLeLivre() {
            Book book = livre(1L, 3, 1);
            Borrowing emprunt = emprunt(10L, book, BorrowingStatus.BORROWED, LocalDate.now().plusDays(2));
            when(borrowingRepository.findWithLockingById(10L)).thenReturn(Optional.of(emprunt));
            when(bookRepository.findWithLockingById(1L)).thenReturn(Optional.of(book));
            when(borrowingRepository.save(emprunt)).thenReturn(emprunt);

            BookResponse.BorrowingResponse response = service.returnBook(10L, httpRequest);

            assertThat(emprunt.getStatus()).isEqualTo(BorrowingStatus.RETURNED);
            assertThat(emprunt.getReturnDate()).isEqualTo(LocalDate.now());
            assertThat(book.getAvailable()).isEqualTo(2);
            assertThat(response.getStatus()).isEqualTo(BorrowingStatus.RETURNED);
            verify(auditService).log(eq("RETURN"), eq("Borrowing"), eq(10L), anyString(), eq(httpRequest));
        }

        @Test
        @DisplayName("ne dépasse jamais la quantité totale (Math.min)")
        void plafonneALaQuantite() {
            // available déjà au maximum (3/3) : un retour de trop ne doit pas
            // produire 4 exemplaires pour 3 livres.
            Book book = livre(1L, 3, 3);
            Borrowing emprunt = emprunt(10L, book, BorrowingStatus.BORROWED, LocalDate.now().plusDays(2));
            when(borrowingRepository.findWithLockingById(10L)).thenReturn(Optional.of(emprunt));
            when(bookRepository.findWithLockingById(1L)).thenReturn(Optional.of(book));
            when(borrowingRepository.save(emprunt)).thenReturn(emprunt);

            service.returnBook(10L, httpRequest);

            assertThat(book.getAvailable()).isEqualTo(3);
        }

        @Test
        @DisplayName("verrouille l'emprunt et le livre en écriture")
        void verrouilleLesDeux() {
            Book book = livre(1L, 3, 1);
            Borrowing emprunt = emprunt(10L, book, BorrowingStatus.OVERDUE, LocalDate.now().minusDays(4));
            when(borrowingRepository.findWithLockingById(10L)).thenReturn(Optional.of(emprunt));
            when(bookRepository.findWithLockingById(1L)).thenReturn(Optional.of(book));
            when(borrowingRepository.save(emprunt)).thenReturn(emprunt);

            service.returnBook(10L, httpRequest);

            verify(borrowingRepository, never()).findById(anyLong());
            verify(bookRepository, never()).findById(anyLong());
        }

        @Test
        @DisplayName("se rabat sur le livre porté par l'emprunt s'il n'est plus lu en direct")
        void repliSurLeLivreDeLEmprunt() {
            Book book = livre(1L, 3, 1);
            Borrowing emprunt = emprunt(10L, book, BorrowingStatus.BORROWED, LocalDate.now().plusDays(2));
            when(borrowingRepository.findWithLockingById(10L)).thenReturn(Optional.of(emprunt));
            when(bookRepository.findWithLockingById(1L)).thenReturn(Optional.empty());
            when(borrowingRepository.save(emprunt)).thenReturn(emprunt);

            service.returnBook(10L, httpRequest);

            assertThat(book.getAvailable()).isEqualTo(2);
            verify(bookRepository).save(book);
        }
    }

    // =====================================================================
    // Inventaire
    // =====================================================================

    @Nested
    @DisplayName("Inventaire")
    class Inventaire {

        @Test
        @DisplayName("refuse la suppression d'un livre déjà emprunté")
        void refuseSuppressionAvecEmprunts() {
            // borrowings.book_id est en RESTRICT : sans ce contrôle, l'utilisateur
            // recevait un 409 générique au lieu d'un message métier.
            Book book = livre(1L, 2, 1);
            when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
            when(borrowingRepository.existsByBookId(1L)).thenReturn(true);

            assertThatThrownBy(() -> service.deleteBook(1L, httpRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("des emprunts existent");

            verify(bookRepository, never()).delete(any());
            verifyNoInteractions(auditService);
        }

        @Test
        @DisplayName("supprime un livre sans historique")
        void supprimeSansHistorique() {
            Book book = livre(1L, 2, 2);
            when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
            when(borrowingRepository.existsByBookId(1L)).thenReturn(false);

            service.deleteBook(1L, httpRequest);

            verify(bookRepository).delete(book);
            verify(auditService).log(eq("DELETE"), eq("Book"), eq(1L), anyString(), eq(httpRequest));
        }

        @Test
        @DisplayName("refuse une quantité inférieure aux exemplaires empruntés")
        void refuseQuantiteTropFaible() {
            Book book = livre(1L, 5, 2); // 3 exemplaires en circulation
            when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

            BookRequest request = BookRequest.builder().title("Les Misérables").quantity(1).build();

            assertThatThrownBy(() -> service.updateBook(1L, request, httpRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("2 exemplaire(s) actuellement emprunté(s)");

            assertThat(book.getQuantity()).as("aucune modification partielle").isEqualTo(5);
            assertThat(book.getAvailable()).isEqualTo(2);
            verify(bookRepository, never()).save(any());
        }

        @Test
        @DisplayName("ajuste la disponibilité au prorata de la quantité")
        void ajusteLaDisponibilite() {
            Book book = livre(1L, 5, 2);
            when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
            when(bookRepository.save(book)).thenReturn(book);

            BookRequest request = BookRequest.builder().title("Les Misérables").quantity(7).build();
            BookResponse response = service.updateBook(1L, request, httpRequest);

            assertThat(response.getQuantity()).isEqualTo(7);
            assertThat(response.getAvailable()).isEqualTo(4); // 2 + (7 - 5)
        }

        @Test
        @DisplayName("crée un livre avec un seul exemplaire par défaut")
        void quantiteParDefaut() {
            when(bookRepository.save(any(Book.class))).thenAnswer(inv -> {
                Book b = inv.getArgument(0);
                b.setId(3L);
                return b;
            });

            BookRequest request = BookRequest.builder().title("Le Baobab fou").build();
            BookResponse response = service.createBook(request, httpRequest);

            assertThat(response.getQuantity()).isEqualTo(1);
            assertThat(response.getAvailable()).isEqualTo(1);
        }

        @Test
        @DisplayName("compte les emprunts en cours ET en retard")
        void compteLesEmpruntsActifs() {
            // Un emprunt en retard reste un livre sorti : ne compter que BORROWED
            // sous-estime l'inventaire réel.
            when(borrowingRepository.countByStatus(BorrowingStatus.BORROWED)).thenReturn(4L);
            when(borrowingRepository.countByStatus(BorrowingStatus.OVERDUE)).thenReturn(3L);

            assertThat(service.countBorrowed()).isEqualTo(7L);
        }
    }
}
