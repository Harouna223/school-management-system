package com.school.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Une ligne du bulletin, une par matière.
 *
 * <p>Correspond aux sept colonnes demandées :</p>
 * <pre>
 * Matières | Note/classe | Note/compo | Coef | Note/compo coef | Moyenne générale | Observation
 * </pre>
 *
 * <ul>
 *   <li>{@code noteClasse} — moyenne des évaluations AUTRES que la composition,
 *       pondérée par le coefficient de chaque évaluation ;</li>
 *   <li>{@code noteComposition} — moyenne des compositions du trimestre ;</li>
 *   <li>{@code coefficient} — coefficient de la matière ;</li>
 *   <li>{@code noteCoef} — {@code moyenne × coefficient} ;</li>
 *   <li>{@code moyenne} — {@code (noteClasse + noteCompo) / 2}, ou la seule
 *       note disponible si l'une des deux manque (élève absent à la composition,
 *       matière sans composition) ;</li>
 *   <li>{@code observation} — appréciation de {@code moyenne} sur l'échelle
 *       officielle (Excellent → Très insuffisant).</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulletinLineResponse {

    private Long subjectId;
    private String subjectName;
    private BigDecimal noteClasse;
    private BigDecimal noteComposition;
    private Integer coefficient;
    private BigDecimal noteCoef;
    private BigDecimal moyenne;
    private String observation;
}
