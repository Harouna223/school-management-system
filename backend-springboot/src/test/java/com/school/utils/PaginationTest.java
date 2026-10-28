package com.school.utils;

import com.school.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaginationTest {

    @Test
    @DisplayName("accepte les bornes légales et conserve le tri")
    void acceptsValidBounds() {
        Pageable pageable = Pagination.of(2, Pagination.MAX_SIZE,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(200);
        assertThat(pageable.getSort().getOrderFor("createdAt")).isNotNull();
    }

    @Test
    @DisplayName("size=1 reste valide (plus petite page possible)")
    void acceptsSmallestPage() {
        assertThat(Pagination.of(0, 1).getPageSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("refuse une page négative")
    void rejectsNegativePage() {
        assertThatThrownBy(() -> Pagination.of(-1, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("page");
    }

    @Test
    @DisplayName("refuse size=0")
    void rejectsZeroSize() {
        assertThatThrownBy(() -> Pagination.of(0, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("size");
    }

    @Test
    @DisplayName("refuse size au-delà du plafond (1000000 demandé par un client)")
    void rejectsOversizedPage() {
        assertThatThrownBy(() -> Pagination.of(0, 1_000_000))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("200");
    }

    @Test
    @DisplayName("refuse une taille négative")
    void rejectsNegativeSize() {
        assertThatThrownBy(() -> Pagination.of(0, -5))
                .isInstanceOf(BusinessException.class);
    }
}
