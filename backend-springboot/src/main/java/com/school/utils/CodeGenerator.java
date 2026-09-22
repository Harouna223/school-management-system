package com.school.utils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * Utilitaires de génération de codes métier (matricules, reçus, factures...).
 */
public final class CodeGenerator {

    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter DATE_COMPACT = DateTimeFormatter.ofPattern("yyMMdd");

    private CodeGenerator() {
    }

    /**
     * Matricule élève : ETU-AAAA-XXXXXX (séquentiel).
     */
    public static String studentMatricule(long sequence) {
        return String.format("ETU-%s-%06d", LocalDate.now().format(YEAR), sequence);
    }

    /**
     * Numéro enseignant : ENS-AAAA-XXXX.
     */
    public static String teacherEmployeeNo(long sequence) {
        return String.format("ENS-%s-%04d", LocalDate.now().format(YEAR), sequence);
    }

    /**
     * Numéro de reçu : RCP-YYMMDD-XXXXX.
     */
    public static String receiptNo(long sequence) {
        return String.format("RCP-%s-%05d", LocalDate.now().format(DATE_COMPACT), sequence);
    }

    /**
     * Numéro de facture : FAC-YYMMDD-XXXXX.
     */
    public static String invoiceNo(long sequence) {
        return String.format("FAC-%s-%05d", LocalDate.now().format(DATE_COMPACT), sequence);
    }

    /**
     * Numéro de reçu de paiement enseignant : REC-ENS-YYMMDD-XXXXX.
     */
    public static String teacherReceiptNo(long sequence) {
        return String.format("REC-ENS-%s-%05d", LocalDate.now().format(DATE_COMPACT), sequence);
    }

    /**
     * Identifiant académique courant : AAAA-AAAA.
     */
    public static String currentAcademicYear() {
        int year = LocalDate.now().getYear();
        return year + "-" + (year + 1);
    }

    /**
     * Dernier jour du mois courant.
     */
    public static LocalDate endOfCurrentMonth() {
        YearMonth ym = YearMonth.now();
        return ym.atEndOfMonth();
    }

    /**
     * Début du mois courant à minuit.
     */
    public static LocalTime midnight() {
        return LocalTime.MIDNIGHT;
    }
}
