package io.github.thiagojosetj.gym.domain.settings;

import io.github.thiagojosetj.gym.domain.model.RepRange;
import io.github.thiagojosetj.gym.domain.template.TemplateDefaults;
import io.github.thiagojosetj.gym.domain.template.TemplateRules;

/**
 * Preferences that follow the account (PRODUCT_SPEC §16). They are data, not constants, so the user
 * can change them; the app only provides the starting values.
 *
 * @param defaultRestSeconds      rest applied when an exercise is added to a template
 * @param restSoundEnabled        play a sound when the rest timer reaches zero (Phase 3)
 * @param restVibrationEnabled    vibrate when the rest timer reaches zero (Phase 3)
 */
public record AppSettings(int defaultRestSeconds, boolean restSoundEnabled, boolean restVibrationEnabled) {

    public AppSettings {
        if (defaultRestSeconds < 0 || defaultRestSeconds > TemplateRules.MAX_REST_SECONDS) {
            throw new IllegalArgumentException("defaultRestSeconds out of range: " + defaultRestSeconds);
        }
    }

    /** Defaults for a fresh install: 90 s of rest, with sound and vibration on. */
    public static AppSettings standard() {
        return new AppSettings(TemplateDefaults.DEFAULT_REST_SECONDS, true, true);
    }

    public AppSettings withDefaultRestSeconds(int seconds) {
        return new AppSettings(seconds, restSoundEnabled, restVibrationEnabled);
    }

    public AppSettings withRestSoundEnabled(boolean enabled) {
        return new AppSettings(defaultRestSeconds, enabled, restVibrationEnabled);
    }

    public AppSettings withRestVibrationEnabled(boolean enabled) {
        return new AppSettings(defaultRestSeconds, restSoundEnabled, enabled);
    }

    /** What "add exercise" applies: 3 × 12 (or 3 × 30 s when timed) with the user's rest. */
    public TemplateDefaults templateDefaults() {
        return new TemplateDefaults(
                TemplateDefaults.DEFAULT_SET_COUNT,
                RepRange.exactly(TemplateDefaults.DEFAULT_REPS),
                TemplateDefaults.DEFAULT_DURATION_SECONDS,
                defaultRestSeconds);
    }
}
