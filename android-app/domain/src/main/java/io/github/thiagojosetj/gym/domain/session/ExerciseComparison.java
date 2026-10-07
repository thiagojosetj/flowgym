package io.github.thiagojosetj.gym.domain.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One exercise of this session next to the same exercise of the previous session of the same
 * workout (PRODUCT_SPEC HIS-04).
 *
 * <p>This pairs; it does not format and it does not judge. Both snapshots are carried whole because
 * the two sessions may disagree about what the exercise <em>is</em>: an exercise logged as dumbbell
 * then and barbell now has a different load basis, a different implement count and possibly a
 * different laterality, and each side has to be read with its own snapshot or the numbers beside
 * each other are not the same measurement.
 *
 * <p>Exercises pair by {@code exerciseId}, and by the <b>nth occurrence</b> of it when a workout
 * contains the same exercise more than once - the first block with the first, the second with the
 * second. Position is deliberately not used: reordering a workout does not make it a different
 * exercise.
 *
 * <p>An exercise with no counterpart is NOT a comparison against zero. It is named separately by
 * {@link SessionComparison}, because "you did not do this last time" and "you did less of it" are
 * different facts and only one of them is a drop.
 */
public record ExerciseComparison(
        SessionExercise current,
        SessionExercise previous,
        MetricChange volumeGrams,
        MetricChange performedSets,
        MetricChange totalReps,
        List<SetPair> sets) {

    /**
     * One set beside the set it pairs with, by ordinal among working sets (PRODUCT_SPEC section
     * 11 - see {@link WorkingSetPairing}). Warm-ups are not here: they are not results.
     *
     * @param number      the working-set number as the screen shows it
     * @param current     today's set, or null when today has no set at that ordinal
     * @param previous    the earlier session's set, or null when it had none at that ordinal
     * @param volumeGrams the change in that set's load volume, or null when there is no change to
     *                    speak of: a bodyweight or timed exercise, or a set only one of the two
     *                    sessions has. A set that WAS there and was skipped is a different matter
     *                    and does get a change - not doing the work is a result, and the row says
     *                    so. What never happens is a change measured against a set that does not
     *                    exist: that would announce as progress the simple fact of having done a
     *                    fourth set for the first time, which the empty side already shows
     */
    public record SetPair(int number, LoggedSet current, LoggedSet previous,
                          MetricChange volumeGrams) {

        /** True when only one of the two sessions has this set. */
        public boolean isUnpaired() {
            return current == null || previous == null;
        }
    }

    public ExerciseComparison {
        sets = sets == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(sets));
    }

    /** This session's name for the exercise - the snapshot it took, not the library's today. */
    public String name() {
        return current.name();
    }

    /**
     * True when at least one of the two sessions recorded load volume for this exercise.
     *
     * <p>False for a bodyweight or timed exercise, where the screen has to say there is no load
     * volume rather than print "=" - which would read as "the same amount of nothing".
     */
    public boolean hasVolume() {
        return volumeGrams.current() > 0L || volumeGrams.previous() > 0L;
    }

    /** Pairs one exercise with its counterpart. Both snapshots are required. */
    public static ExerciseComparison between(SessionExercise current, SessionExercise previous) {
        SessionVolume.Totals now = SessionVolume.ofExercise(current);
        SessionVolume.Totals then = SessionVolume.ofExercise(previous);
        boolean anyLoad = now.loadGrams() > 0L || then.loadGrams() > 0L;
        return new ExerciseComparison(current, previous,
                new MetricChange(now.loadGrams(), then.loadGrams()),
                new MetricChange(completedOf(current), completedOf(previous)),
                new MetricChange(now.totalReps(), then.totalReps()),
                pairSets(current, previous, anyLoad));
    }

    private static int completedOf(SessionExercise exercise) {
        int done = 0;
        for (LoggedSet set : exercise.sets()) {
            if (set.isCompleted()) {
                done++;
            }
        }
        return done;
    }

    private static List<SetPair> pairSets(SessionExercise current, SessionExercise previous,
                                          boolean anyLoad) {
        List<LoggedSet> today = current.sets();
        List<LoggedSet> before = previous.sets();
        int[] paired = WorkingSetPairing.pair(workingFlags(today), workingFlags(before));
        boolean[] taken = new boolean[before.size()];
        List<SetPair> pairs = new ArrayList<>();
        for (int i = 0; i < today.size(); i++) {
            LoggedSet set = today.get(i);
            if (set.isWarmUp()) {
                continue;
            }
            LoggedSet other = null;
            if (paired[i] != WorkingSetPairing.NONE) {
                other = before.get(paired[i]);
                taken[paired[i]] = true;
            }
            pairs.add(new SetPair(numberOf(set, pairs.size()), set, other,
                    anyLoad ? volumeChange(current, set, previous, other) : null));
        }
        // Sets of the earlier session that today never reached. Listed rather than dropped: "four
        // last time, three today" is exactly what this screen exists to show, and a set that only
        // disappears is a number the person is never told they lost.
        for (int j = 0; j < before.size(); j++) {
            LoggedSet set = before.get(j);
            if (taken[j] || set.isWarmUp()) {
                continue;
            }
            pairs.add(new SetPair(numberOf(set, pairs.size()), null, set,
                    anyLoad ? volumeChange(current, null, previous, set) : null));
        }
        return pairs;
    }

    /**
     * The working number the set carries, falling back to its place in this list.
     *
     * <p>The fallback exists because {@code workingNumber} is derived when a session is read and
     * could in principle be absent on a set that counts; numbering it by position here is still
     * the number the person sees, rather than a zero.
     */
    private static int numberOf(LoggedSet set, int index) {
        return set.workingNumber() == null ? index + 1 : set.workingNumber();
    }

    /**
     * The load volume of two paired sets, drops included, or null when there is only one of them.
     *
     * <p>A set that was NOT PERFORMED still counts, as zero: the work was not done, and that is a
     * measurement. A set that is NOT THERE does not, because there is nothing to measure against -
     * the same reason a brand new exercise is named rather than compared with zero.
     */
    private static MetricChange volumeChange(SessionExercise currentExercise, LoggedSet current,
                                             SessionExercise previousExercise, LoggedSet previous) {
        if (current == null || previous == null) {
            return null;
        }
        return new MetricChange(SessionVolume.ofSet(currentExercise, current).loadGrams(),
                SessionVolume.ofSet(previousExercise, previous).loadGrams());
    }

    private static List<Boolean> workingFlags(List<LoggedSet> sets) {
        List<Boolean> flags = new ArrayList<>(sets.size());
        for (LoggedSet set : sets) {
            flags.add(!set.isWarmUp());
        }
        return flags;
    }
}
