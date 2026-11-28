package io.github.thiagojosetj.gym.ui.progress;

/**
 * Which measure the chart of PRG-01 is drawing.
 *
 * <p><b>One at a time, and that is the rule, not a limitation.</b> Load is kilograms, volume is
 * kilograms of a completely different magnitude, and repetitions are a count. Put on one plot
 * they would need two or three y axes, and the alignment between two y scales is arbitrary - the
 * chart would invent a correlation that is not in the data (ADR-0042).
 */
public enum ChartMeasure {
    LOAD,
    VOLUME,
    REPS
}
