package io.github.thiagojosetj.gym.domain.progress;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.thiagojosetj.gym.domain.model.Weight;
import io.github.thiagojosetj.gym.domain.session.LoggedSet;
import io.github.thiagojosetj.gym.domain.session.SessionExercise;
import io.github.thiagojosetj.gym.domain.session.SessionVolume;

/**
 * The personal records of one exercise (PRODUCT_SPEC section 10), computed from the history.
 *
 * <p><b>Derived, not stored.</b> Section 10 says a PR can always be recomputed from the sessions,
 * and so it is: there is no table here and nothing to keep in step. What a table would add is the
 * HISTORY of records - every PR ever beaten, kept as its own row - and that is the one thing this
 * cannot answer. It answers what the records are now and when each was set.
 *
 * <p><b>A tie is not a record.</b> Equalling your best is not beating it, so every comparison here
 * is strictly greater. And the first time an exercise is performed sets a <b>baseline</b>, not an
 * achievement: there was nothing to beat. {@code isBaseline} says which, so a screen can show the
 * number without congratulating anybody for turning up.
 *
 * <p><b>Which sets count.</b> Warm-ups never (section 6.2: not results) and unperformed sets never.
 * Beyond that, section 10 parts company with section 9 on purpose: body-weight exercises are out
 * of LOAD VOLUME but their repetitions and their added load are real records, so they count for
 * {@link #maxLoad()} and {@link #repsAtLoad()} and not for the volume records.
 */
public record ExercisePersonalRecords(
        Record maxLoad,
        Record maxSetVolume,
        Record maxSessionVolume,
        List<RepsAtLoad> repsAtLoad) {

    public static final ExercisePersonalRecords NONE =
            new ExercisePersonalRecords(null, null, null, Collections.emptyList());

    /**
     * @param value            grams, or a count, depending on which record this is
     * @param isBaseline       true when it was set the first time the exercise was ever
     *                         performed, so there was nothing to beat
     * @param loadPerImplement whether {@code value} is the weight of ONE implement (section 6.4).
     *                         Only {@link #maxLoad()} can be: the volume records are sums of
     *                         effective load, where "per implement" means nothing, and they carry
     *                         {@code false} so a screen prints them with no suffix
     */
    public record Record(long value, String sessionId, String localDate, boolean isBaseline,
                         boolean loadPerImplement) {
    }

    /**
     * The most repetitions done at one exact load (section 10: "comparada em gramas").
     *
     * <p>The load is the one the person TYPED - per dumbbell where that is how they log it - and
     * not the effective load section 9 multiplies for volume. "Maior carga" in section 10 is
     * explicitly per implement, and a record the app states in units the person never entered
     * would be a different claim from the one they made.
     */
    public record RepsAtLoad(long loadGrams, int reps, String sessionId, String localDate,
                             boolean isBaseline, boolean loadPerImplement) {
    }

    /**
     * What makes two loads "the same load" (section 10 compares them in grams).
     *
     * <p>The basis is part of the key, because 12 kg per dumbbell and a 12 kg barbell are the same
     * number and not the same load - one is twice the work. Keyed on grams alone they would merge,
     * and the screen would report the repetitions of one under the weight of the other.
     */
    private record LoadKey(long grams, boolean perImplement) {
    }

    public ExercisePersonalRecords {
        repsAtLoad = repsAtLoad == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(repsAtLoad));
    }

    public boolean isEmpty() {
        return maxLoad == null && maxSetVolume == null && maxSessionVolume == null
                && repsAtLoad.isEmpty();
    }

    /**
     * Reads the records out of an exercise's whole history.
     *
     * @param appearances every time the exercise was performed, OLDEST FIRST. The order decides
     *                    which values are a baseline, so a caller that hands them over newest
     *                    first would turn the first session's numbers into achievements
     */
    public static ExercisePersonalRecords of(List<ExerciseAppearance> appearances) {
        if (appearances == null || appearances.isEmpty()) {
            return NONE;
        }
        String firstSessionId = appearances.get(0).sessionId();
        Record maxLoad = null;
        Record maxSetVolume = null;
        Record maxSessionVolume = null;
        Map<LoadKey, RepsAtLoad> bestRepsByLoad = new LinkedHashMap<>();

        for (ExerciseAppearance appearance : appearances) {
            SessionExercise exercise = appearance.exercise();
            boolean baseline = appearance.sessionId().equals(firstSessionId);
            boolean perImplement = exercise.isLoadPerImplement();
            long sessionVolume = 0L;
            for (LoggedSet set : exercise.sets()) {
                if (!set.isCompleted() || set.isWarmUp()) {
                    continue;
                }
                Integer reps = exercise.totalRepsOf(set);
                Weight typed = set.values().weight();
                if (typed != null && reps != null && reps >= 1 && exercise.trackingType().usesWeight()) {
                    // A tie is not a record, here and everywhere below.
                    maxLoad = better(maxLoad, typed.grams(), appearance, baseline, perImplement);
                    bestRepsByLoad.merge(new LoadKey(typed.grams(), perImplement),
                            new RepsAtLoad(typed.grams(), reps, appearance.sessionId(),
                                    appearance.localDate(), baseline, perImplement),
                            (old, fresh) -> fresh.reps() > old.reps() ? fresh : old);
                }
                long setVolume = SessionVolume.ofSet(exercise, set).loadGrams();
                if (setVolume > 0L) {
                    maxSetVolume = better(maxSetVolume, setVolume, appearance, baseline, false);
                    sessionVolume += setVolume;
                }
            }
            if (sessionVolume > 0L) {
                maxSessionVolume = better(maxSessionVolume, sessionVolume, appearance, baseline,
                        false);
            }
        }
        List<RepsAtLoad> reps = new ArrayList<>(bestRepsByLoad.values());
        // Heaviest first, and where the same number appears on two bases the per-implement one
        // comes first: it is the heavier work. A fixed order, so the list never shuffles between
        // reads of the same history.
        reps.sort(Comparator.comparingLong(RepsAtLoad::loadGrams)
                .thenComparing(RepsAtLoad::loadPerImplement)
                .reversed());
        return new ExercisePersonalRecords(maxLoad, maxSetVolume, maxSessionVolume, reps);
    }

    private static Record better(Record current, long candidate, ExerciseAppearance appearance,
                                 boolean baseline, boolean perImplement) {
        if (current != null && candidate <= current.value()) {
            return current;
        }
        return new Record(candidate, appearance.sessionId(), appearance.localDate(), baseline,
                perImplement);
    }
}
