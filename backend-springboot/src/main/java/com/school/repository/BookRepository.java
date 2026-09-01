package com.school.repository;

import com.school.entity.Book;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Book b WHERE b.id = :id")
    Optional<Book> findWithLockingById(@Param("id") Long id);

    @Query("""
            SELECT b FROM Book b
            WHERE (:search IS NULL OR :search = '' OR
                   LOWER(b.title) LIKE LOWER(CONCAT('%', :search, '%')) OR
                   LOWER(b.author) LIKE LOWER(CONCAT('%', :search, '%')) OR
                   LOWER(b.isbn) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:category IS NULL OR :category = '' OR b.category = :category)
            """)
    Page<Book> search(@Param("search") String search,
                      @Param("category") String category,
                      Pageable pageable);
}
