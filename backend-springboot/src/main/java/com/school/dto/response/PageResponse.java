package com.school.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Pagination normalisée utilisée par tous les endpoints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return PageResponse.<T>builder()
                .content(page.getContent().stream().map(mapper).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    /**
     * Charge TOUTES les pages d'une recherche, lot par lot.
     *
     * <p>Pour les exports : un unique appel {@code size=10000} est refusé par le
     * plafond de pagination (400) et ne tiendrait de toute façon pas dans une
     * page. Le chargement passe donc par des pages bornées jusqu'à la dernière.</p>
     *
     * @param loader reçoit le numéro de page et renvoie la page correspondante
     */
    public static <T> List<T> collectAll(java.util.function.IntFunction<PageResponse<T>> loader) {
        List<T> all = new ArrayList<>();
        int page = 0;
        PageResponse<T> chunk;
        do {
            chunk = loader.apply(page++);
            all.addAll(chunk.getContent());
        } while (page < chunk.getTotalPages());
        return all;
    }
}
