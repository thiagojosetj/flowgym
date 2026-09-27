package io.github.thiagojosetj.gym.domain.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import io.github.thiagojosetj.gym.domain.model.SideMode;
import io.github.thiagojosetj.gym.domain.model.TrackingType;
import io.github.thiagojosetj.gym.domain.model.Weight;
import io.github.thiagojosetj.gym.domain.model.WeightUnit;
import io.github.thiagojosetj.gym.domain.session.FinishReview.SetUnderReview;

public class FinishReviewTest {

    private static final Weight FORTY_KG = Weight.of(40, WeightUnit.KILOGRAM);

    @Test
    public void aSessionWithEverythingConfirmedNeedsNoConfirmation() {
        FinishReview review = FinishReview.of(Arrays.asList(
                set("a", SetStatus.COMPLETED, values(FORTY_KG, 10)),
                set("b", SetStatus.COMPLETED, values(FORTY_KG, 9))));

        assertEquals(2, review.alreadyCompleted());
        assertFalse(review.needsConfirmation());
        assertFalse(review.wouldRecordNothing());
    }

    @Test
    public void aFilledButUnconfirmedSetIsCompletedNotDiscarded() {
        FinishReview review = FinishReview.of(Collections.singletonList(
                set("a", SetStatus.PENDING, values(FORTY_KG, 10))));

        assertEquals(Collections.singletonList("a"), review.setIdsToComplete());
        assertTrue(review.setIdsToSkip().isEmpty());
        assertTrue(review.needsConfirmation());
    }

    @Test
    public void weightWithoutRepsIsPartialAndIsNeverInvented() {
        // The load is known but the repetitions are not: the app must not guess a number.
        FinishReview review = FinishReview.of(Collections.singletonList(
                set("a", SetStatus.PENDING, values(FORTY_KG, null))));

        assertEquals(1, review.partiallyFilled().size());
        assertTrue(review.setIdsToComplete().isEmpty());
        assertEquals(Collections.singletonList("a"), review.setIdsToSkip());
        assertTrue(review.wouldRecordNothing());
    }

    @Test
    public void repsWithoutWeightCountAsPerformedBecauseTheLoadMayNotApply() {
        // Body weight with no extra load, or an elastic band: reps are the result.
        FinishReview review = FinishReview.of(Collections.singletonList(
                set("a", SetStatus.PENDING, values(null, 12))));

        assertEquals(Collections.singletonList("a"), review.setIdsToComplete());
    }

    @Test
    public void anUntouchedSetIsSkippedAndListedSoItIsNotLostSilently() {
        FinishReview review = FinishReview.of(Arrays.asList(
                set("a", SetStatus.COMPLETED, values(FORTY_KG, 10)),
                set("b", SetStatus.PENDING, SetValues.EMPTY),
                set("c", SetStatus.PENDING, SetValues.EMPTY)));

        assertEquals(2, review.empty().size());
        assertEquals(Arrays.asList("b", "c"), review.setIdsToSkip());
        assertEquals(1, review.alreadyCompleted());
        assertTrue(review.needsConfirmation());
        assertFalse(review.wouldRecordNothing());
    }

    @Test
    public void aSetTheUserAlreadySkippedIsNotAskedAboutAgain() {
        FinishReview review = FinishReview.of(Arrays.asList(
                set("a", SetStatus.COMPLETED, values(FORTY_KG, 10)),
                set("b", SetStatus.SKIPPED, SetValues.EMPTY)));

        assertFalse(review.needsConfirmation());
        assertEquals(0, review.pendingCount());
    }

    @Test
    public void perSideLoggingNeedsBothSidesToCountAsPerformed() {
        SetValues onlyLeft = new SetValues(FORTY_KG, null, 10, null, null);
        FinishReview partial = FinishReview.of(Collections.singletonList(
                new SetUnderReview("a", "Rosca unilateral", 1, SetStatus.PENDING, onlyLeft,
                        TrackingType.WEIGHT_REPS, SideMode.PER_SIDE)));
        assertEquals(1, partial.partiallyFilled().size());

        SetValues bothSides = new SetValues(FORTY_KG, null, 10, 9, null);
        FinishReview complete = FinishReview.of(Collections.singletonList(
                new SetUnderReview("a", "Rosca unilateral", 1, SetStatus.PENDING, bothSides,
                        TrackingType.WEIGHT_REPS, SideMode.PER_SIDE)));
        assertEquals(Collections.singletonList("a"), complete.setIdsToComplete());
        assertEquals(Integer.valueOf(19), bothSides.totalReps());
    }

    @Test
    public void aTimedExerciseNeedsItsDuration() {
        SetValues noDuration = new SetValues(null, null, null, null, null);
        FinishReview empty = FinishReview.of(Collections.singletonList(
                new SetUnderReview("a", "Prancha", 1, SetStatus.PENDING, noDuration,
                        TrackingType.DURATION, SideMode.COMBINED)));
        assertEquals(1, empty.empty().size());

        SetValues withDuration = new SetValues(null, null, null, null, 45);
        FinishReview done = FinishReview.of(Collections.singletonList(
                new SetUnderReview("a", "Prancha", 1, SetStatus.PENDING, withDuration,
                        TrackingType.DURATION, SideMode.COMBINED)));
        assertEquals(Collections.singletonList("a"), done.setIdsToComplete());
    }

    @Test
    public void theListsAreCopiesAndCannotBeChangedFromOutside() {
        FinishReview review = FinishReview.of(Collections.singletonList(
                set("a", SetStatus.PENDING, SetValues.EMPTY)));

        try {
            review.empty().add(new FinishReview.PendingSet("b", "x", 1));
            throw new AssertionError("the list should be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            assertEquals(1, review.empty().size());
        }
    }

    private static SetValues values(Weight weight, Integer reps) {
        return new SetValues(weight, reps, null, null, null);
    }

    private static SetUnderReview set(String id, SetStatus status, SetValues values) {
        return new SetUnderReview(id, "Supino reto com barra", 1, status, values,
                TrackingType.WEIGHT_REPS, SideMode.COMBINED);
    }
}
