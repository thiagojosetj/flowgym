package io.github.thiagojosetj.gym.domain.progress;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.thiagojosetj.gym.domain.model.MuscleRole;
import io.github.thiagojosetj.gym.domain.session.ActiveSession;
import io.github.thiagojosetj.gym.domain.session.LoggedSet;
import io.github.thiagojosetj.gym.domain.session.SessionExercise;
import io.github.thiagojosetj.gym.domain.session.SessionVolume;

/**
 * What a week or a month of training added up to (PRODUCT_SPEC PRG-04).
 *
 * <p>Every total here comes from {@link SessionVolume}, the same code that produced the figure the
 * person saw when they finished each of these workouts. A second implementation would eventually
 * disagree with it, and the number they saw first is the one they would believe.
 *
 * <p><b>Two different set counts, on purpose.</b> {@link #performedSets()} counts every set
 * confirmed as done, warm-ups included, because that is what every other screen in the app means
 * by "séries feitas". The per-muscle counts exclude warm-ups, because a warm-up is not training
 * volume for the muscle (PRODUCT_SPEC section 6.2) and the sets-per-muscle-per-week figure would
 * be wrong with it in. {@link #warmUpSets()} is reported so the two reconcile instead of looking
 * like an error.
 *
 * <p>The per-muscle numbers also do NOT add up to the set count, and cannot: one set of bench
 * press is counted for the chest AND for the triceps. It is a list of "how much each muscle got",
 * not a division of the sets into piles.
 *
 * @param sessions          finished sessions in the period
 * @param performedSets     sets confirmed as done, warm-ups included
 * @param warmUpSets        how many of those were warm-ups
 * @param setsOutsideVolume performed sets that legitimately have no load volume, so the total can
 *                          say so rather than look complete (PRODUCT_SPEC section 9)
 */
public record PeriodStatistics(
        int sessions,
        int performedSets,
        int warmUpSets,
        int totalReps,
        long volumeGrams,
        int setsOutsideVolume,
        long effectiveMs,
        int durationSeconds,
        List<MuscleWorkload> muscles) {

    public static final PeriodStatistics EMPTY =
            new PeriodStatistics(0, 0, 0, 0, 0L, 0, 0L, 0, Collections.emptyList());

    public PeriodStatistics {
        muscles = muscles == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(muscles));
    }

    public boolean isEmpty() {
        return sessions == 0;
    }

    public boolean hasVolume() {
        return volumeGrams > 0L;
    }

    /**
     * Adds up a period.
     *
     * @param sessions      the finished sessions of the period, each with its own exercises and
     *                      sets. Whole sessions rather than pre-summed rows, because the volume
     *                      rules of section 9 are set-by-set and cannot be expressed in SQL
     * @param muscleGroups  which groups each exercise trains. Resolved from the library as it
     *                      stands TODAY, not snapshotted by the session: a muscle map is a
     *                      classification, not a measurement of what happened, and re-reading it
     *                      is what lets a corrected catalogue fix past weeks too
     */
    public static PeriodStatistics of(Collection<ActiveSession> sessions,
                                      Collection<ExerciseMuscleGroup> muscleGroups) {
        if (sessions == null || sessions.isEmpty()) {
            return EMPTY;
        }
        Map<String, Map<String, ExerciseMuscleGroup>> byExercise = index(muscleGroups);
        Map<String, int[]> counts = new HashMap<>();
        Map<String, ExerciseMuscleGroup> groups = new HashMap<>();

        SessionVolume.Totals totals = SessionVolume.ofSessions(sessions);
        int performed = 0;
        int warmUps = 0;
        long effectiveMs = 0L;
        for (ActiveSession session : sessions) {
            performed += session.completedSets();
            effectiveMs += session.header().clock().effectiveMs(endInstantOf(session));
            for (SessionExercise exercise : session.exercises()) {
                Map<String, ExerciseMuscleGroup> trained =
                        byExercise.getOrDefault(exercise.exerciseId(), Collections.emptyMap());
                for (LoggedSet set : exercise.sets()) {
                    if (!set.isCompleted()) {
                        continue;
                    }
                    if (set.isWarmUp()) {
                        warmUps++;
                        continue;
                    }
                    for (ExerciseMuscleGroup group : trained.values()) {
                        groups.putIfAbsent(group.muscleGroupId(), group);
                        int[] tally = counts.computeIfAbsent(group.muscleGroupId(),
                                key -> new int[2]);
                        tally[group.role() == MuscleRole.PRIMARY ? 0 : 1]++;
                    }
                }
            }
        }
        return new PeriodStatistics(sessions.size(), performed, warmUps, totals.totalReps(),
                totals.loadGrams(), totals.excludedSets(), effectiveMs, totals.durationSeconds(),
                workloads(counts, groups));
    }

    /**
     * Each exercise's groups, one entry per group, with the strongest role it has there.
     *
     * <p>An exercise can name two subgroups of the same group in different roles - the middle
     * chest as a main target and the upper chest as assisting. Kept as two entries, that one set
     * would be counted as a main set AND as an assisting set for the chest, so the same work
     * appears twice in the same row. The main role wins: the exercise does train that group on
     * purpose.
     */
    private static Map<String, Map<String, ExerciseMuscleGroup>> index(
            Collection<ExerciseMuscleGroup> muscleGroups) {
        Map<String, Map<String, ExerciseMuscleGroup>> byExercise = new HashMap<>();
        if (muscleGroups == null) {
            return byExercise;
        }
        for (ExerciseMuscleGroup group : muscleGroups) {
            Map<String, ExerciseMuscleGroup> forExercise =
                    byExercise.computeIfAbsent(group.exerciseId(), key -> new HashMap<>());
            ExerciseMuscleGroup existing = forExercise.get(group.muscleGroupId());
            if (existing == null || group.role() == MuscleRole.PRIMARY) {
                forExercise.put(group.muscleGroupId(), group);
            }
        }
        return byExercise;
    }

    /** Ordered by how much was done, then by the catalogue's order so ties do not shuffle. */
    private static List<MuscleWorkload> workloads(Map<String, int[]> counts,
                                                  Map<String, ExerciseMuscleGroup> groups) {
        List<MuscleWorkload> workloads = new ArrayList<>(counts.size());
        Set<String> ids = new LinkedHashSet<>(counts.keySet());
        for (String id : ids) {
            int[] tally = counts.get(id);
            ExerciseMuscleGroup group = groups.get(id);
            workloads.add(new MuscleWorkload(id, group.name(), group.sortOrder(), tally[0],
                    tally[1]));
        }
        workloads.sort(Comparator
                .comparingInt(MuscleWorkload::primarySets).reversed()
                .thenComparing(Comparator.comparingInt(MuscleWorkload::totalSets).reversed())
                .thenComparingInt(MuscleWorkload::sortOrder)
                .thenComparing(MuscleWorkload::name));
        return workloads;
    }

    /**
     * A finished session's clock ignores "now"; the end instant keeps the arithmetic honest if one
     * of these is somehow still running.
     */
    private static long endInstantOf(ActiveSession session) {
        Long endedAt = session.header().clock().endedAt();
        return endedAt == null ? session.header().clock().startedAt() : endedAt;
    }
}
