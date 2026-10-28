package com.school.dto.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chargement de toutes les pages pour les exports.
 *
 * <p>Garde-fou : une implémentation qui ne lirait que la première page
 * compilerait et passerait les tests HTTP tant que le jeu de données tient dans
 * une page. Ici le découpage est explicite.</p>
 */
class PageResponseTest {

    @Test
    @DisplayName("collectAll parcourt toutes les pages jusqu'à la dernière")
    void collecteToutesLesPages() {
        List<Integer> pagesDemandees = new ArrayList<>();

        List<Integer> lignes = PageResponse.collectAll(page -> {
            pagesDemandees.add(page);
            return page(page, 200, 401);
        });

        assertThat(pagesDemandees).containsExactly(0, 1, 2);
        assertThat(lignes).hasSize(401);
        assertThat(lignes.get(400)).isEqualTo(400);
    }

    @Test
    @DisplayName("collectAll s'arrête après une page vide")
    void pageVideNEstJamaisSuivie() {
        List<Integer> pagesDemandees = new ArrayList<>();

        List<Integer> lignes = PageResponse.collectAll(page -> {
            pagesDemandees.add(page);
            return page(page, 0, 0);
        });

        assertThat(pagesDemandees).containsExactly(0);
        assertThat(lignes).isEmpty();
    }

    /** Page numéro {@code page}, de taille {@code size}, sur {@code total} lignes. */
    private PageResponse<Integer> page(int page, int size, long total) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
        return PageResponse.<Integer>builder()
                .content(IntStream.range(page * size, Math.min((page + 1) * size, (int) total))
                        .boxed().toList())
                .page(page)
                .size(size)
                .totalElements(total)
                .totalPages(totalPages)
                .build();
    }
}
