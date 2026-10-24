package com.school.pdf;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalogue des types de documents PDF produits par Sahel Edu.
 *
 * <p>Chaque type porte ses capacités de mise en page : c'est ce qui permet à
 * l'écran de prévisualisation de ne proposer que des options réalisables
 * (on ne propose pas « paysage » sur un reçu A5, ni « en-tête » sur une carte
 * scolaire dont l'en-tête est dessiné à la main).</p>
 */
@Component
public class DocumentTypes {

    /** Générateur commun : en-tête, titre, tableaux stylés, pied de page. */
    public static final String COMMON = "COMMON";

    /** Mise en page fixe dessinée au {@code PdfContentByte}. */
    public static final String FIXED = "FIXED";

    /** Une entrée du catalogue. */
    public record Info(String code, String label, String group, String renderer,
                       boolean flexiblePage, boolean customHeader) {
    }

    public static final List<Info> ALL = List.of(
            // A. Documents scolaires
            new Info("BULLETIN", "Bulletin de notes", "scolarite", COMMON, true, false),
            new Info("CLASS_BULLETINS", "Bulletins d'une classe", "scolarite", COMMON, true, false),
            new Info("SCHOOL_CERTIFICATE", "Certificat de scolarité", "scolarite", COMMON, true, false),
            new Info("ATTENDANCE_CERTIFICATE", "Certificat de fréquentation", "scolarite", FIXED, false, true),
            new Info("STUDENT_CARD", "Carte scolaire", "scolarite", FIXED, false, true),
            new Info("STUDENT_LIST", "Liste des élèves", "scolarite", COMMON, true, false),
            // B. Rapports
            new Info("GENERIC_REPORT", "Rapport tabulaire", "rapports", COMMON, true, false),
            new Info("ATTENDANCE_REPORT", "Rapport de présences", "rapports", COMMON, true, false),
            // C. Documents financiers
            new Info("PAYMENT_RECEIPT", "Reçu de paiement", "finance", COMMON, true, false),
            new Info("TEACHER_PAYMENT_RECEIPT", "Reçu de paie enseignant", "finance", COMMON, true, false),
            new Info("FINANCE_REPORT", "Bilan financier", "finance", COMMON, true, false),
            new Info("EXPENSE_LIST", "Liste des dépenses", "finance", COMMON, true, false),
            new Info("PAYMENT_HISTORY", "Historique des paiements", "finance", COMMON, true, false),
            new Info("INVOICE_LIST", "Liste des factures", "finance", COMMON, true, false),
            new Info("PAYROLL_REPORT", "État des paiements enseignants", "finance", COMMON, true, false)
    );

    public static final List<String> CODES = ALL.stream().map(Info::code).toList();

    private final Map<String, Info> byCode = new LinkedHashMap<>();

    public DocumentTypes() {
        ALL.forEach(i -> byCode.put(i.code(), i));
    }

    public Info info(String code) {
        return code == null ? null : byCode.get(code.toUpperCase(java.util.Locale.ROOT));
    }

    public boolean isFlexiblePage(String code) {
        Info i = info(code);
        return i != null && i.flexiblePage();
    }

    public boolean hasCustomHeader(String code) {
        Info i = info(code);
        return i != null && i.customHeader();
    }

    /** Catalogue exposé à l'API (groupes de l'écran d'aperçu). */
    public List<Map<String, Object>> catalogue() {
        return ALL.stream().map(i -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", i.code());
            m.put("label", i.label());
            m.put("group", i.group());
            m.put("renderer", i.renderer());
            m.put("flexiblePage", i.flexiblePage());
            m.put("customHeader", i.customHeader());
            return m;
        }).toList();
    }
}
