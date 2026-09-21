package io.github.thiagojosetj.gym.domain.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalizes free text for accent- and case-insensitive search (ADR-0014).
 *
 * <p>"Supino Reto", "SUPINO  reto" and "súpino reto" all become {@code "supino reto"}. The same
 * function is applied when a row is written ({@code search_text}) and when the user types a
 * query, so both sides always agree.
 */
public final class TextNormalizer {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{Nd}]+");

    private TextNormalizer() {
    }

    /**
     * Lower-cases, strips diacritics (NFD decomposition + removal of combining marks), replaces any
     * run of punctuation/whitespace by a single space and trims. Returns an empty string for null.
     */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        String withoutMarks = COMBINING_MARKS.matcher(decomposed).replaceAll("");
        String lower = withoutMarks.toLowerCase(Locale.ROOT);
        return NON_ALPHANUMERIC.matcher(lower).replaceAll(" ").trim();
    }
}
