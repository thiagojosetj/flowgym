package io.github.thiagojosetj.gym.ui.common;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Parsing and formatting of numbers typed by people. Brazilians write "42,5"; keyboards and other
 * locales produce "42.5". Both must work. Pure Java: unit-tested on the JVM.
 */
public final class NumberInput {

    private NumberInput() {
    }

    /**
     * Parses a non-negative decimal that may use ',' or '.' as decimal separator.
     *
     * @return null for blank input
     * @throws NumberFormatException for anything that is not a plain non-negative decimal
     */
    public static Double parseDecimal(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String normalized = text.trim().replace(',', '.');
        if (!normalized.matches("\\d{1,6}(\\.\\d{1,3})?")) {
            throw new NumberFormatException("Not a decimal: " + text);
        }
        return Double.parseDouble(normalized);
    }

    /**
     * Parses a non-negative whole number.
     *
     * @return null for blank input
     * @throws NumberFormatException for anything else
     */
    public static Integer parseWholeNumber(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String trimmed = text.trim();
        if (!trimmed.matches("\\d{1,6}")) {
            throw new NumberFormatException("Not a whole number: " + text);
        }
        return Integer.parseInt(trimmed);
    }

    /** Up to two decimals, no trailing zeros, locale decimal separator: 40 → "40", 42.5 → "42,5". */
    public static String formatDecimal(double value, Locale locale) {
        DecimalFormat format = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(locale));
        return format.format(value);
    }
}
