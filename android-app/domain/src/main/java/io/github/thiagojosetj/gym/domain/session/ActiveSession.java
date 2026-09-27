package io.github.thiagojosetj.gym.domain.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A whole session as the screen sees it: the header plus its exercises and sets. Read-only - every
 * change goes to the database through the repository and comes back as a new instance, so the screen
 * can never show a value that was not persisted (ADR for phase 3).
 */
public record ActiveSession(SessionHeader header, List<SessionExercise> exercises) {

    public ActiveSession {
        exercises = exercises == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(exercises));
    }

    public String id() {
        return header.id();
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
                underReview.add(new FinishReview.SetUnderReview(
                        set.id(),
                        exercise.name(),
                        set.workingNumber() != null ? set.workingNumber() : set.position() + 1,
                        set.status(),
                        set.values(),
                        exercise.trackingType(),
                        exercise.sideMode()));
            }
        }
        return FinishReview.of(underReview);
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
