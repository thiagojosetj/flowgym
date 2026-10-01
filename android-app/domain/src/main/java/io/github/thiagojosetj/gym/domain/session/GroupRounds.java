package io.github.thiagojosetj.gym.domain.session;

import java.util.List;

/**
 * When the rest of a grouped round starts (PRODUCT_SPEC section 6.3).
 *
 * <p>The spec says the rest comes "after the last exercise of the round". Read literally that would
 * mean the group's last exercise by position - but ACT-01 says the planned order is not binding, so
 * someone who finishes A2 before A1 would never get a rest at all. The rule here is therefore the
 * one the sentence means rather than the one it says: a round is over when <b>no exercise of the
 * group still owes a set at that round</b>. The last exercise finished is the one that starts the
 * rest, whichever it happens to be.
 *
 * <p>Groups do not have to be even. An exercise with fewer sets than the round index simply has
 * nothing left to owe, so a 4x3 superset against a 2x3 one still rests after every round.
 *
 * <p>Drops are not rounds. A set's segments are nested inside it (ADR-0037), so they never shift a
 * round index and a drop-set in a superset still counts as one round.
 */
public final class GroupRounds {

    private GroupRounds() {
    }

    /**
     * The round a set belongs to: its ordinal among its exercise's sets, 0-based.
     *
     * @return -1 when the set does not belong to this exercise
     */
    public static int roundOf(SessionExercise exercise, String setId) {
        if (exercise == null || setId == null) {
            return -1;
        }
        List<LoggedSet> sets = exercise.sets();
        for (int i = 0; i < sets.size(); i++) {
            if (setId.equals(sets.get(i).id())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * True when every exercise of the group has settled its set for this round, so the round's rest
     * can start.
     *
     * <p>"Settled" means not {@link SetStatus#PENDING}: performed and deliberately skipped both end
     * the round, because neither leaves anything still to do. An exercise with no set at this index
     * does not hold the round open.
     *
     * @return false when the group is empty or no exercise has a set at this round, because there
     *         is then no round to have finished
     */
    public static boolean isRoundComplete(List<SessionExercise> group, int round) {
        if (group == null || round < 0) {
            return false;
        }
        boolean anySetAtThisRound = false;
        for (SessionExercise exercise : group) {
            List<LoggedSet> sets = exercise.sets();
            if (round >= sets.size()) {
                continue; // nothing owed: this exercise is shorter than the others
            }
            anySetAtThisRound = true;
            if (sets.get(round).status() == SetStatus.PENDING) {
                return false;
            }
        }
        return anySetAtThisRound;
    }
}
