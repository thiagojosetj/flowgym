package io.github.thiagojosetj.gym.domain.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.thiagojosetj.gym.domain.model.Laterality;
import io.github.thiagojosetj.gym.domain.model.LoadBasis;
import io.github.thiagojosetj.gym.domain.model.RepRange;
import io.github.thiagojosetj.gym.domain.model.SideMode;
import io.github.thiagojosetj.gym.domain.model.TrackingType;
import io.github.thiagojosetj.gym.domain.model.Weight;
import io.github.thiagojosetj.gym.domain.model.WeightUnit;

/**
 * Finishing a session that has drop-set steps (PRODUCT_SPEC section 8 meets section 9.1).
 *
 * <p>Section 8 says nothing is thrown away in silence: a set filled in and never confirmed is
 * either completed or marked skipped, and the user is shown the list first. A drop is written as a
 * child row of its set, so the same promise has to hold for it.
 */
public class FinishWithSegmentsTest {

    @Test
    public void aDropFilledInAndNeverConfirmedIsNotLeftUndecided() {
        LoggedSet drop = segment("s1-a", new SetValues(kg(30), 8, null, null, null),
                SetStatus.PENDING);
        LoggedSet performed = set("s1", new SetValues(kg(40), 10, null, null, null),
                SetStatus.COMPLETED, Collections.singletonList(drop));

        FinishReview review = session(bench(performed)).finishReview();

        // Either finishing records it, or finishing marks it skipped. What it must not do is leave
        // it PENDING inside a session that is over: the reps happened, and the user is never told
        // they did not count.
        List<String> decided = Arrays.asList(
                review.setIdsToComplete().toArray(new String[0]));
        boolean resolved = decided.contains("s1-a") || review.setIdsToSkip().contains("s1-a");
        assertTrue("a etapa preenchida ficou sem decisao ao finalizar", resolved);
    }

    @Test
    public void anEmptyDropIsSkippedLikeAnEmptySet() {
        LoggedSet drop = segment("s1-a", SetValues.EMPTY, SetStatus.PENDING);
        LoggedSet performed = set("s1", new SetValues(kg(40), 10, null, null, null),
                SetStatus.COMPLETED, Collections.singletonList(drop));

        FinishReview review = session(bench(performed)).finishReview();

        assertTrue("a etapa vazia devia ser marcada como pulada",
                review.setIdsToSkip().contains("s1-a"));
    }

    @Test
    public void aDropIsNotCountedAsASetOfItsOwnInTheDialog() {
        // ADR-0037: a drop-set is ONE set taken past failure. The dialog counts sets, so a drop
        // must not make "1 serie" read as "2".
        LoggedSet drop = segment("s1-a", new SetValues(kg(30), 8, null, null, null),
                SetStatus.PENDING);
        LoggedSet performed = set("s1", new SetValues(kg(40), 10, null, null, null),
                SetStatus.COMPLETED, Collections.singletonList(drop));

        FinishReview review = session(bench(performed)).finishReview();

        assertEquals(1, review.alreadyCompleted());
        assertEquals("a etapa nao e uma serie na contagem do dialogo", 0, review.pendingCount());
    }

    // ------------------------------------------------------------------ helpers

    private static Weight kg(double value) {
        return Weight.of(value, WeightUnit.KILOGRAM);
    }

    private static LoggedSet segment(String id, SetValues values, SetStatus status) {
        return new LoggedSet(id, 0, null, null, null, true, null, null, null, 0,
                values, status, null, null, null, Collections.emptyList());
    }

    private static LoggedSet set(String id, SetValues values, SetStatus status,
                                 List<LoggedSet> segments) {
        return new LoggedSet(id, 0, 1, null, null, true, RepRange.exactly(10), null, null, 90,
                values, status, status == SetStatus.COMPLETED ? 1L : null, null, null, segments);
    }

    private static SessionExercise bench(LoggedSet... sets) {
        return new SessionExercise("se-1", "ex-1", 0, "Supino reto com barra",
                TrackingType.WEIGHT_REPS, LoadBasis.TOTAL, 1, Laterality.BILATERAL,
                SideMode.COMBINED, 90, null, null, null, Arrays.asList(sets));
    }

    private static ActiveSession session(SessionExercise... exercises) {
        SessionHeader header = new SessionHeader("sess-1", "tpl-1", "Push A", null,
                SessionStatus.ACTIVE, new SessionClock(1_000_000L, null, 0L, null),
                null, null, null);
        return new ActiveSession(header, Arrays.asList(exercises));
    }
}
