package com.school.entity;

import com.school.enums.EducationCycle;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Barème d'appréciation d'un cycle scolaire.
 *
 * <p>Un barème est <b>versionné</b> : modifier un barème déjà utilisé pour des
 * bulletins ne l'écrase pas, il crée une nouvelle version. Les bulletins
 * gardent la référence de la version qui les a imprimés
 * ({@code Bulletin.appreciationScaleId}), donc une reconfiguration ne change
 * jamais silencieusement une appréciation déjà délivrée.</p>
 *
 * <p>Un seul barème ACTIF par cycle (contrôlé par le service et par
 * {@code AppreciationScaleService.activate}).</p>
 */
@Entity
@Table(name = "appreciation_scales")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppreciationScale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private EducationCycle cycle;

    @Column(name = "min_note", nullable = false, precision = 5, scale = 2)
    private BigDecimal minNote;

    @Column(name = "max_note", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxNote;

    @Column(nullable = false)
    @Builder.Default
    private int version = 1;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "scale", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<AppreciationBand> bands = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Ajoute une borne basse et une borne haute au barème. */
    public void addBand(AppreciationBand band) {
        band.setScale(this);
        bands.add(band);
    }

    public void clearBands() {
        bands.clear();
    }
}
