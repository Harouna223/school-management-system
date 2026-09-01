package com.school.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Conversion d'un montant en toutes lettres (français, règles « vingt »/« cent »).
 */
public final class AmountToWords {

    private static final String[] UNITS = {
            "", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf",
            "dix", "onze", "douze", "treize", "quatorze", "quinze", "seize",
            "dix-sept", "dix-huit", "dix-neuf"
    };
    private static final String[] TENS = {
            "", "dix", "vingt", "trente", "quarante", "cinquante", "soixante",
            "soixante", "quatre-vingt", "quatre-vingt"
    };

    private AmountToWords() {
    }

    /**
     * Convertit un montant en toutes lettres (partie entière + centimes si présents).
     * Ex : 12500 → "douze mille cinq cents francs CFA".
     */
    public static String toWords(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        long integerPart = amount.longValue();
        int cents = amount.remainder(BigDecimal.ONE).movePointRight(2).intValue();

        StringBuilder sb = new StringBuilder();
        if (integerPart == 0) {
            sb.append("zéro");
        } else {
            sb.append(threeDigits(integerPart));
        }
        sb.append(" francs CFA");
        if (cents > 0) {
            sb.append(" et ").append(cents < 10 ? "0" + cents : cents).append(" centimes");
        }
        return sb.toString().trim();
    }

    private static String threeDigits(long n) {
        if (n >= 1_000_000_000L) {
            throw new IllegalArgumentException("Montant trop grand pour la conversion en lettres");
        }
        String result = "";
        long millions = n / 1_000_000;
        long thousands = (n % 1_000_000) / 1000;
        long rest = n % 1000;

        if (millions > 0) {
            result += (millions == 1 ? "un million" : threeDigits(millions) + " millions") + " ";
        }
        if (thousands > 0) {
            if (thousands == 1) {
                result += "mille ";
            } else {
                result += threeDigits(thousands) + " mille ";
            }
        }
        result += hundredsTensUnits(rest);
        return result.trim();
    }

    private static String hundredsTensUnits(long n) {
        long hundreds = n / 100;
        long rest = n % 100;
        String result = "";

        if (hundreds > 0) {
            result += (hundreds == 1 ? "cent" : UNITS[(int) hundreds] + " cent");
            if (hundreds > 1 && rest == 0) {
                result += "s"; // deux cents
            }
            result += " ";
        }
        if (rest > 0) {
            result += tensUnits((int) rest);
        }
        return result.trim();
    }

    private static String tensUnits(int n) {
        if (n < 20) {
            return UNITS[n];
        }
        int tens = n / 10;
        int units = n % 10;
        if (tens == 7 || tens == 9) {
            // soixante-dix, quatre-vingt-dix
            if (units == 0) {
                return switch (tens) {
                    case 7 -> "soixante-dix";
                    case 9 -> "quatre-vingt-dix";
                    default -> "quatre-vingts";
                };
            }
            String base = TENS[tens];
            if (tens == 7 && units == 1) {
                return "soixante et onze";
            }
            if (tens == 9 && units == 1) {
                return "quatre-vingt-onze";
            }
            return base + "-" + UNITS[10 + units];
        }
        String base = TENS[tens];
        if (units == 0) {
            // vingt → vingts au pluriel seulement après un multiplicateur
            return tens == 8 ? "quatre-vingts" : base;
        }
        if (units == 1 && tens != 8) {
            return base + " et un";
        }
        return base + "-" + UNITS[units];
    }
}