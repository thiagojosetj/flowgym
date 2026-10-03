package io.github.thiagojosetj.gym.domain.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A whole session as the screen sees it: the header plus its exercises and sets. Read-only - every
 * change goes to the database through the repository and comes back as a new instance, so the screen
 * can never show a value that was not persisted (ADR for phase 3).
 *
 * <p>The groups travel beside the exercises instead of inside {@link SessionExercise}: that record
 * is built positionally in the mapper and in several tests, so widening it would break every one
 * of them for a fact most exercises (the ungrouped ones) do not even have.
 *
 * @param groupByExerciseId the group of each exercise that is in one, keyed by the session
 *                          exercise's id; an exercise that stands alone has no entry
 */
public record ActiveSession(SessionHeader header, List<SessionExercise> exercises,
                            Map<String, SessionGroup> groupByExerciseId) {

    public ActiveSession {
        exercises = exercises == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(exercises));
        groupByExerciseId = groupByExerciseId == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(groupByExerciseId));
    }

    /** A session in which every exercise stands alone. */
    public ActiveSession(SessionHeader header, List<SessionExercise> exercises) {
        this(header, exercises, null);
    }

    public String id() {
        return header.id();
    }

    /** The group an exercise was in, or null when it stood alone. */
    public SessionGroup groupOf(String sessionExerciseId) {
        return groupByExerciseId.get(sessionExerciseId);
    }

    /**
     * The exercises of one group, in session order: exactly what
     * {@link GroupRounds#isRoundComplete(List, int)} takes.
     */
    public List<SessionExercise> exercisesOfGroup(String groupId) {
        List<SessionExercise> members = new ArrayList<>();
        for (SessionExercise exercise : exercises) {
            SessionGroup group = groupByExerciseId.get(exercise.id());
            if (group != null && group.id().equals(groupId)) {
                members.add(exercise);
            }
        }
        return Collections.unmodifiableList(members);
    }

    public int totalSets() {
        int total = 0;
        for (SessionExercise exercise : exercises) {
            total += exercise.sets().size();
        }
        return total;
    }

    public int completedSets() {
        int done = 0;
        for (SessionExercise exercise : exercises) {
            done += exercise.completedSets();
        }
        return done;
    }

    public SessionVolume.Totals totals() {
        return SessionVolume.of(exercises);
    }

    /** What finishing right now would do with the sets that were never confirmed (section 8). */
    public FinishReview finishReview() {
        List<FinishReview.SetUnderReview> underReview = new ArrayList<>(totalSets());
        for (SessionExercise exercise : exercises) {
            for (LoggedSet set : exercise.sets()) {
                int number = set.workingNumber() != null ? set.workingNumber() : set.position() + 1;
                underReview.add(review(exercise, set, number, false));
                // The drops of this set go in too. They are rows of their own in the database, so
                // without this they stay PENDING in a finished session and their repetitions
                // count for nothing, with nothing on screen saying so (section 8).
                for (LoggedSet segment : set.segments()) {
                    underReview.add(review(exercise, segment, number, true));
                }
            }
        }
        return FinishReview.of(underReview);
    }

    private static FinishReview.SetUnderReview review(SessionExercise exercise, LoggedSet set,
                                                      int number, boolean isSegment) {
        return new FinishReview.SetUnderReview(set.id(), exercise.name(), number, set.status(),
                set.values(), exercise.trackingType(), exercise.sideMode(), isSegment);
    }

    public SessionExercise exerciseById(String sessionExerciseId) {
        for (SessionExercise exercise : exercises) {
            if (exercise.id().equals(sessionExerciseId)) {
                return exercise;
            }
        }
        return null;
    }

    /** The exercise a set belongs to - the UI needs it to know how to read the set's load. */
    public SessionExercise exerciseOfSet(String setId) {
        for (SessionExercise exercise : exercises) {
            for (LoggedSet set : exercise.sets()) {
                if (set.id().equals(setId)) {
                    return exercise;
                }
            }
        }
        return null;
    }

    public LoggedSet setById(String setId) {
        for (SessionExercise exercise : exercises) {
            for (LoggedSet set : exercise.sets()) {
                if (set.id().equals(setId)) {
                    return set;
                }
            }
        }
        return null;
    }
}
