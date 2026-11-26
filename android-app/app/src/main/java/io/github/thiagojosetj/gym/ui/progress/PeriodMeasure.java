package io.github.thiagojosetj.gym.ui.progress;

/**
 * Which measure the period's chart draws (PRODUCT_SPEC PRG-01, "gráficos ... do treino").
 *
 * <p><b>No load.</b> A session is many exercises, and the heaviest weight in a workout of squats
 * and curls is a fact about the squats, not about the workout - there is no honest "load of a
 * session" to plot. Volume, sets and repetitions are the three that mean something for a whole
 * session, which is why this is not {@link ChartMeasure}.
 */
public enum PeriodMeasure {
    VOLUME,
    SETS,
    REPS
}
