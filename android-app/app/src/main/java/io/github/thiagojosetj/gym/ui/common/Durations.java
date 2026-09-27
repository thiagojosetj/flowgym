package io.github.thiagojosetj.gym.ui.common;

import android.icu.text.MeasureFormat;
import android.icu.util.Measure;
import android.icu.util.MeasureUnit;

import java.util.Locale;

/**
 * Formats the durations the workout screen shows. Digits for the eye ("1:23"), words for TalkBack
 * ("1 minuto e 23 segundos"): a screen reader announcing "um dois pontos vinte e tres" is useless.
 */
public final class Durations {

    private Durations() {
    }

    /** m:ss, or h:mm:ss once the workout passes an hour. */
    public static String clock(long millis) {
        long totalSeconds = Math.max(0L, millis) / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    /** The same duration in words, for content descriptions. */
    public static String spoken(long millis) {
        long totalSeconds = Math.max(0L, millis) / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        MeasureFormat format = MeasureFormat.getInstance(
                Locale.getDefault(), MeasureFormat.FormatWidth.WIDE);
        if (minutes > 0 && seconds > 0) {
            return format.formatMeasures(new Measure(minutes, MeasureUnit.MINUTE),
                    new Measure(seconds, MeasureUnit.SECOND));
        }
        if (minutes > 0) {
            return format.formatMeasures(new Measure(minutes, MeasureUnit.MINUTE));
        }
        return format.formatMeasures(new Measure(seconds, MeasureUnit.SECOND));
    }

    /** "1:30 min" for a planned rest, where the unit has to be explicit. */
    public static String restLabel(int seconds) {
        return clock(seconds * 1000L);
    }
}
