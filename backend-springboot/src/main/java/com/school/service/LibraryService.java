package com.school.service;

import com.school.dto.request.BookRequest;
import com.school.dto.request.BorrowingRequest;
import com.school.dto.response.BookResponse;
import com.school.dto.response.PageResponse;
import com.school.entity.Book;
import com.school.entity.Borrowing;
import com.school.enums.BorrowingStatus;
import com.school.exception.BusinessException;
import com.school.exception.ResourceNotFoundException;
import com.school.repository.BookRepository;
import com.school.repository.BorrowingRepository;
import jakarta.servlet.http.HttpServletRequest;
import com.school.utils.Pagination;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Module bibliothèque : inventaire livres, emprunts, retours, retards.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class LibraryService {

    private final BookRepository bookRepository;
    private final BorrowingRepository borrowingRepository;
    private final StudentService studentService;
    private final AuditService auditService;

    public PageResponse<BookResponse> searchBooks(String search, String category, int page, int size) {
        Pageable pageable = Pagination.of(page, size, Sort.by("title").ascending());
        Page<Book> result = bookRepository.search(search, category, pageable);
        return PageResponse.from(result, BookResponse::from);
    }

    @Transactional
    public BookResponse createBook(BookRequest request, HttpServletRequest httpRequest) {
        int quantity = request.getQuantity() != null ? request.getQuantity() : 1;
        Book book = Book.builder()
                .title(request.getTitle())
                .author(request.getAuthor())
                .isbn(request.getIsbn())
                .category(request.getCategory())
                .quantity(quantity)
                .available(quantity)
                .publisher(request.getPublisher())
                .publicationYear(request.getPublicationYear())
                .build();
        Book saved = bookRepository.save(book);
        auditService.log("CREATE", "Book", saved.getId(), "Ajout livre " + saved.getTitle(), httpRequest);
        return BookResponse.from(saved);
    }

    @Transactional
    public BookResponse updateBook(Long id, BookRequest request, HttpServletRequest httpRequest) {
        Book book = findBook(id);
        int oldQuantity = book.getQuantity();
        book.setTitle(request.getTitle());
        book.setAuthor(request.getAuthor());
        book.setIsbn(request.getIsbn());
        book.setCategory(request.getCategory());
        book.setPublisher(request.getPublisher());
        book.setPublicationYear(request.getPublicationYear());
        if (request.getQuantity() != null) {
            int diff = request.getQuantity() - oldQuantity;
            int newAvailable = book.getAvailable() + diff;
            if (newAvailable < 0) {
                throw new BusinessException("Quantité trop faible : " + (-newAvailable)
                        + " exemplaire(s) actuellement emprunté(s)");
            }
            book.setQuantity(request.getQuantity());
            book.setAvailable(newAvailable);
        }
        auditService.log("UPDATE", "Book", id, "Modification livre " + book.getTitle(), httpRequest);
        return BookResponse.from(bookRepository.save(book));
    }

    @Transactional
    public void deleteBook(Long id, HttpServletRequest httpRequest) {
        Book book = findBook(id);
        // borrowings.book_id est en RESTRICT : sans ce contrôle, la suppression
        // renvoyait un 409 générique au lieu d'un message métier.
        if (borrowingRepository.existsByBookId(id)) {
            throw new BusinessException("Impossible de supprimer : des emprunts existent pour "
                    + "« " + book.getTitle() + " ». Consultez l'historique avant de retirer ce livre.");
        }
        auditService.log("DELETE", "Book", id, "Suppression livre " + book.getTitle(), httpRequest);
        bookRepository.delete(book);
    }

    // ---------- Emprunts ----------

    public PageResponse<BookResponse.BorrowingResponse> searchBorrowings(BorrowingStatus status,
                                                                         int page, int size) {
        Pageable pageable = Pagination.of(page, size, Sort.by("borrowDate").descending());
        Page<Borrowing> result = status != null
                ? borrowingRepository.findByStatus(status, pageable)
                : borrowingRepository.findAll(pageable);
        return PageResponse.from(result, BookResponse.BorrowingResponse::from);
    }

    public List<BookResponse.BorrowingResponse> borrowingsOfStudent(Long studentId) {
        return borrowingRepository.findByStudentId(studentId).stream()
                .map(BookResponse.BorrowingResponse::from).toList();
    }

    @Transactional
    public BookResponse.BorrowingResponse borrow(BorrowingRequest request, HttpServletRequest httpRequest) {
        // Cohérence des dates : un emprunt pouvait naître DÉJÀ en retard
        // (dueDate antérieure à borrowDate), ce qu'aucun contrôle ne signalait.
        if (request.getDueDate().isBefore(request.getBorrowDate())) {
            throw new BusinessException("La date de retour prévue (" + request.getDueDate()
                    + ") ne peut pas précéder la date d'emprunt (" + request.getBorrowDate() + ").");
        }
        if (request.getBorrowDate().isAfter(LocalDate.now())) {
            throw new BusinessException("La date d'emprunt ne peut pas être dans le futur.");
        }
        Book book = bookRepository.findWithLockingById(request.getBookId())
                .orElseThrow(() -> com.school.exception.ResourceNotFoundException.of("Livre", request.getBookId()));
        if (book.getAvailable() <= 0) {
            throw new BusinessException("Aucun exemplaire disponible pour ce livre");
        }
        Borrowing borrowing = Borrowing.builder()
                .book(book)
                .student(studentService.findById(request.getStudentId()))
                .borrowDate(request.getBorrowDate())
                .dueDate(request.getDueDate())
                .status(BorrowingStatus.BORROWED)
                .build();
        book.setAvailable(book.getAvailable() - 1);
        bookRepository.save(book);
        Borrowing saved = borrowingRepository.save(borrowing);
        auditService.log("BORROW", "Borrowing", saved.getId(),
                "Emprunt " + book.getTitle() + " par " + saved.getStudent().getFullName(), httpRequest);
        return BookResponse.BorrowingResponse.from(saved);
    }

    @Transactional
    public BookResponse.BorrowingResponse returnBook(Long borrowingId, HttpServletRequest httpRequest) {
        // Verrou pessimiste : deux retours simultanés passaient tous les deux le
        // test de statut et incrémentaient le stock deux fois.
        Borrowing borrowing = borrowingRepository.findWithLockingById(borrowingId)
                .orElseThrow(() -> ResourceNotFoundException.of("Emprunt", borrowingId));
        if (borrowing.getStatus() == BorrowingStatus.RETURNED) {
            throw new BusinessException("Cet emprunt a déjà été retourné");
        }
        borrowing.setStatus(BorrowingStatus.RETURNED);
        borrowing.setReturnDate(LocalDate.now());
        Book book = bookRepository.findWithLockingById(borrowing.getBook().getId())
                .orElse(borrowing.getBook());
        book.setAvailable(Math.min(book.getQuantity(), book.getAvailable() + 1));
        bookRepository.save(book);
        auditService.log("RETURN", "Borrowing", borrowingId,
                "Retour " + book.getTitle(), httpRequest);
        return BookResponse.BorrowingResponse.from(borrowingRepository.save(borrowing));
    }

    /**
     * Marquage automatique des emprunts en retard.
     *
     * <p>Exécuté chaque nuit : la méthode n'était atteignable que par un appel
     * manuel, donc le statut {@code OVERDUE} et la liste « en retard » restaient
     * vides par défaut, contrairement aux factures qui basculent en retard
     * automatiquement.</p>
     */
    @Transactional
    public int markOverdue() {
        List<Borrowing> overdue = borrowingRepository
                .findByStatusAndDueDateBefore(BorrowingStatus.BORROWED, LocalDate.now());
        overdue.forEach(b -> b.setStatus(BorrowingStatus.OVERDUE));
        borrowingRepository.saveAll(overdue);
        return overdue.size();
    }

    /**
     * Marquage quotidien des retards (4 h du matin).
     *
     * <p><b>Le {@code @Transactional} doit être ICI, sur le point d'entrée.</b>
     * La classe est annotée {@code @Transactional(readOnly = true)} : sans
     * annotation sur cette méthode, la tâche s'exécutait donc dans une
     * transaction <em>en lecture seule</em>, et le {@code @Transactional} de
     * {@link #markOverdue()} était contourné par l'auto-invocation
     * ({@code this.…}). Conséquence silencieuse : Hibernate passe le flush en
     * {@code MANUAL} en lecture seule, les changements d'état des emprunts
     * n'étaient <b>jamais écrits</b> — la tâche se terminait sans erreur mais la
     * base restait inchangée, et la liste « en retard » restait vide à jamais.
     * Constaté par {@code ScheduledTasksIT}.</p>
     */
    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void scheduledMarkOverdue() {
        int count = markOverdue();
        if (count > 0) {
            log.info("Bibliothèque : {} emprunt(s) passé(s) en retard", count);
        }
    }

    public long countBorrowed() {
        return borrowingRepository.countByStatus(BorrowingStatus.BORROWED)
                + borrowingRepository.countByStatus(BorrowingStatus.OVERDUE);
    }

    public Book findBook(Long id) {
        return bookRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Livre", id));
    }
}