package io.github.thiagojosetj.gym.domain.session;

import java.util.List;

import io.github.thiagojosetj.gym.domain.model.TrackingType;
import io.github.thiagojosetj.gym.domain.model.Weight;

/**
 * Load volume, exactly as PRODUCT_SPEC section 9 defines it - and nothing more. The app shows data;
 * it never invents a number to make a total look complete.
 *
 * <p>A set counts only when it was performed, is not a warm-up, has a known positive load and more
 * than zero repetitions. Body-weight exercises are deliberately excluded, because the fraction of
 * the body being moved differs per exercise (a push-up is not a pull-up); they still contribute sets
 * and repetitions. Exercises measured in time contribute time, not volume.
 *
 * <p>Whatever is excluded is counted in {@link Totals#excludedSets()}, so the screen can say "N
 * series nao incluidas no volume" instead of hiding the difference.
 */
public final class SessionVolume {

    /**
     * @param loadGrams    sum of effective load x total repetitions, in grams (ADR-0008)
     * @param countedSets  sets that entered the volume
     * @param excludedSets performed sets left out (body weight, time, unknown load, warm-up)
     * @param totalReps    repetitions of every performed set, including the excluded ones
     * @param durationSeconds time under tension of performed timed sets
     */
    public record Totals(long loadGrams, int countedSets, int excludedSets, int totalReps,
                         int durationSeconds) {

        static final Totals EMPTY = new Totals(0L, 0, 0, 0, 0);

        Totals plus(Totals other) {
            return new Totals(
                    loadGrams + other.loadGrams,
                    countedSets + other.countedSets,
                    excludedSets + other.excludedSets,
                    totalReps + other.totalReps,
                    durationSeconds + other.durationSeconds);
        }

        public boolean hasExclusions() {
            return excludedSets > 0;
        }
    }

    private SessionVolume() {
    }

    public static Totals of(List<SessionExercise> exercises) {
        Totals totals = Totals.EMPTY;
        if (exercises != null) {
            for (SessionExercise exercise : exercises) {
                totals = totals.plus(ofExercise(exercise));
            }
        }
        return totals;
    }

    public static Totals ofExercise(SessionExercise exercise) {
        Totals totals = Totals.EMPTY;
        for (LoggedSet set : exercise.sets()) {
            if (!set.isCompleted()) {
                continue; // planned or skipped sets are not results
            }
            totals = totals.plus(ofSet(exercise, set));
        }
        return totals;
    }

    /**
     * One set, including the drops it was taken through (PRODUCT_SPEC section 9.1, ADR-0037).
     *
     * <p>A drop-set is {@code 40 kg x 10 -> 30 kg x 8 -> 20 kg x 6}. The load and the repetitions of
     * every drop are summed in, because they were performed and dropping them would be discarding
     * real work. The set itself still counts as <b>one</b> set: a drop-set is one set taken past
     * failure, not three, and counting it as three would inflate "series feitas" and the weekly
     * sets-per-muscle figures that are built on it.
     *
     * <p>That is also why "counted or excluded" is decided once, on the total: otherwise a drop-set
     * with one unloaded drop would appear inside and outside the volume at the same time.
     */
    private static Totals ofSet(SessionExercise exercise, LoggedSet set) {
        long load = setLoadGrams(exercise, set);
        int reps = repsOf(exercise, set);
        int duration = durationOf(set);
        for (LoggedSet segment : set.segments()) {
            if (!segment.isCompleted()) {
                continue; // a drop that was planned and not performed is not a result
            }
            // The drops of a warm-up are warm-up too. The set is excluded as a whole or not at all;
            // it never counts half its load.
            if (!set.isWarmUp()) {
                load += setLoadGrams(exercise, segment);
            }
            reps += repsOf(exercise, segment);
            duration += durationOf(segment);
        }
        boolean counted = load > 0;
        return new Totals(load, counted ? 1 : 0, counted ? 0 : 1, reps, duration);
    }

    private static int repsOf(SessionExercise exercise, LoggedSet set) {
        Integer reps = exercise.totalRepsOf(set);
        return reps == null ? 0 : reps;
    }

    private static int durationOf(LoggedSet set) {
        return set.values().durationSeconds() == null ? 0 : set.values().durationSeconds();
    }

    /** Volume of one set in grams, or 0 when the set does not qualify. */
    public static long setLoadGrams(SessionExercise exercise, LoggedSet set) {
        if (!set.isCompleted() || set.isWarmUp()) {
            return 0L;
        }
        TrackingType tracking = exercise.trackingType();
        if (tracking == TrackingType.BODYWEIGHT_REPS || !tracking.usesWeight() || !tracking.usesReps()) {
            return 0L;
        }
        Weight weight = set.values().weight();
        Integer reps = exercise.totalRepsOf(set);
        if (weight == null || weight.grams() <= 0 || reps == null || reps <= 0) {
            return 0L;
        }
        return effectiveLoadGrams(exercise, weight) * reps;
    }

    /**
     * The load that actually moved. For dumbbells the user types the weight of each one, so the
     * calculation multiplies by how many are used at the same time - while the screen keeps saying
     * "12 kg por halter" (PRODUCT_SPEC section 6.4).
     */
    public static long effectiveLoadGrams(SessionExercise exercise, Weight weight) {
        return exercise.isLoadPerImplement()
                ? weight.grams() * Math.max(1, exercise.implementCount())
                : weight.grams();
    }
}
