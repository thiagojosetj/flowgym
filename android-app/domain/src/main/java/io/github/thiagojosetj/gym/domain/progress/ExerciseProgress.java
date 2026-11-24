package io.github.thiagojosetj.gym.domain.progress;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.thiagojosetj.gym.domain.model.Weight;
import io.github.thiagojosetj.gym.domain.session.LoggedSet;
import io.github.thiagojosetj.gym.domain.session.SessionExercise;
import io.github.thiagojosetj.gym.domain.session.SessionVolume;

/**
 * One exercise across its whole history (PRODUCT_SPEC PRG-03): what each session did with it, and
 * the records that came out of that.
 *
 * <p>Oldest first, because that is the order a chart reads and the order that decides what counts
 * as a baseline (section 10).
 *
 * <p>Every figure is computed by {@link SessionVolume} under the section 9 rules, from each
 * session's OWN snapshot of the exercise. The same movement logged with dumbbells one month and a
 * barbell the next is two different measurements, and reading the older one with today's snapshot
 * is how a screen invents a jump nobody lifted.
 */
public record ExerciseProgress(String exerciseId, String name, List<Point> sessions,
                               ExercisePersonalRecords records) {

    /**
     * What one session did with this exercise.
     *
     * @param maxLoadGrams the heaviest load TYPED in a counted set - per implement where that is
     *                     how it is logged, matching how the app states a load everywhere else.
     *                     Zero when the exercise records no load (repetitions only, timed)
     * @param volumeGrams  this exercise's load volume in that session, section 9. Zero for a
     *                     body-weight or timed exercise, which is not the same as "no work": the
     *                     repetitions are right there beside it
     * @param performedSets counted sets, warm-ups excluded - a warm-up is not a result (6.2)
     * @param loadPerImplement whether {@code maxLoadGrams} is the weight of ONE implement, as
     *                         section 6.4 requires it to be stated. Carried per session because
     *                         it belongs to that session's snapshot: the same movement can be a
     *                         pair of dumbbells one month and a barbell the next, and a screen
     *                         that printed one basis for both would misreport the lighter one
     */
    public record Point(String sessionId, String localDate, long startedAt, int performedSets,
                        int totalReps, long maxLoadGrams, long volumeGrams,
                        boolean loadPerImplement) {

        public boolean hasLoad() {
            return maxLoadGrams != 0L;
        }

        public boolean hasVolume() {
            return volumeGrams > 0L;
        }
    }

    public ExerciseProgress {
        sessions = sessions == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(sessions));
    }

    public boolean isEmpty() {
        return sessions.isEmpty();
    }

    /** True when the exercise was performed once: there is a point, but no trend to read. */
    public boolean isSingleSession() {
        return sessions.size() == 1;
    }

    /**
     * @param appearances every time it was performed, OLDEST FIRST (see
     *                    {@link ExercisePersonalRecords#of})
     */
    public static ExerciseProgress of(String exerciseId, String name,
                                      List<ExerciseAppearance> appearances) {
        List<Point> points = new ArrayList<>(appearances == null ? 0 : appearances.size());
        if (appearances != null) {
            for (ExerciseAppearance appearance : appearances) {
                points.add(pointOf(appearance));
            }
        }
        return new ExerciseProgress(exerciseId, name, points,
                ExercisePersonalRecords.of(appearances));
    }

    private static Point pointOf(ExerciseAppearance appearance) {
        SessionExercise exercise = appearance.exercise();
        SessionVolume.Totals totals = SessionVolume.ofExercise(exercise);
        int performed = 0;
        long maxLoad = 0L;
        boolean anyLoad = false;
        for (LoggedSet set : exercise.sets()) {
            if (!set.isCompleted() || set.isWarmUp()) {
                continue;
            }
            performed++;
            Weight typed = set.values().weight();
            if (typed != null && exercise.trackingType().usesWeight()) {
                // Assistance on a body-weight exercise is a negative load, so the first value
                // seen has to win outright: starting the maximum at zero would report "0 kg" for
                // someone still using the band.
                maxLoad = anyLoad ? Math.max(maxLoad, typed.grams()) : typed.grams();
                anyLoad = true;
            }
        }
        return new Point(appearance.sessionId(), appearance.localDate(), appearance.startedAt(),
                performed, totals.totalReps(), maxLoad, totals.loadGrams(),
                exercise.isLoadPerImplement());
    }
}
