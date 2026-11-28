package io.github.thiagojosetj.gym.domain.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.thiagojosetj.gym.domain.model.Laterality;
import io.github.thiagojosetj.gym.domain.model.LoadBasis;
import io.github.thiagojosetj.gym.domain.model.SideMode;
import io.github.thiagojosetj.gym.domain.model.TrackingType;

/** The groups ride beside the exercises, so ungrouped code never has to know they exist. */
public class ActiveSessionGroupsTest {

    private static final SessionGroup SUPERSET =
            new SessionGroup("g1", "A", "tech-ss", "SS", 75, 0);

    @Test
    public void aSessionBuiltWithoutGroupsHasEveryExerciseStandingAlone() {
        ActiveSession session = new ActiveSession(header(),
                Arrays.asList(exercise("se-1"), exercise("se-2")));

        assertNull(session.groupOf("se-1"));
        assertTrue(session.groupByExerciseId().isEmpty());
        assertTrue(session.exercisesOfGroup("g1").isEmpty());
    }

    @Test
    public void anExerciseKnowsItsGroupAndTheGroupKnowsItsExercisesInSessionOrder() {
        Map<String, SessionGroup> groups = new HashMap<>();
        groups.put("se-1", SUPERSET);
        groups.put("se-3", SUPERSET);
        ActiveSession session = new ActiveSession(header(),
                Arrays.asList(exercise("se-1"), exercise("se-2"), exercise("se-3")), groups);

        assertEquals(SUPERSET, session.groupOf("se-1"));
        assertNull(session.groupOf("se-2"));
        List<SessionExercise> members = session.exercisesOfGroup("g1");
        assertEquals(2, members.size());
        assertEquals("se-1", members.get(0).id());
        assertEquals("se-3", members.get(1).id());
    }

    @Test
    public void theMembersAreWhatTheRoundRuleTakes() {
        // The round rule needs the group's exercises and nothing else. An ungrouped exercise whose
        // set is still pending must not hold the round of a group open.
        Map<String, SessionGroup> groups = new HashMap<>();
        groups.put("se-1", SUPERSET);
        groups.put("se-2", SUPERSET);
        ActiveSession session = new ActiveSession(header(), Arrays.asList(
                exercise("se-1", SetStatus.COMPLETED),
                exercise("se-2", SetStatus.COMPLETED),
                exercise("se-3", SetStatus.PENDING)), groups);

        assertTrue(GroupRounds.isRoundComplete(session.exercisesOfGroup("g1"), 0));
    }

    @Test
    public void theGroupsAreACopyTheCallerCannotChangeAfterwards() {
        Map<String, SessionGroup> groups = new HashMap<>();
        groups.put("se-1", SUPERSET);
        ActiveSession session = new ActiveSession(header(),
                Collections.singletonList(exercise("se-1")), groups);

        groups.clear();

        assertEquals(SUPERSET, session.groupOf("se-1"));
        assertThrows(UnsupportedOperationException.class,
                () -> session.groupByExerciseId().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> session.exercisesOfGroup("g1").clear());
    }

    @Test
    public void twoSessionsWithTheSameGroupsAreEqual() {
        // The screen decides what to redraw by comparing sessions: a map that compared by identity
        // would redraw on every emission.
        Map<String, SessionGroup> first = new HashMap<>();
        first.put("se-1", SUPERSET);
        Map<String, SessionGroup> second = new HashMap<>();
        second.put("se-1", new SessionGroup("g1", "A", "tech-ss", "SS", 75, 0));
        List<SessionExercise> exercises = Collections.singletonList(exercise("se-1"));

        assertEquals(new ActiveSession(header(), exercises, first),
                new ActiveSession(header(), exercises, second));
    }

    // ------------------------------------------------------------------ helpers

    private static SessionHeader header() {
        return new SessionHeader("sess-1", "tpl-1", "Push A", null, SessionStatus.ACTIVE,
                new SessionClock(1_000_000L, null, 0L, null), null, null, null);
    }

    private static SessionExercise exercise(String id, SetStatus... statuses) {
        LoggedSet[] sets = new LoggedSet[statuses.length];
        for (int i = 0; i < statuses.length; i++) {
            sets[i] = new LoggedSet(id + "-set-" + i, i, i + 1, null, null, true, null, null, null,
                    0, SetValues.EMPTY, statuses[i],
                    statuses[i] == SetStatus.COMPLETED ? 1L : null, null, null,
                    Collections.emptyList());
        }
        return new SessionExercise(id, "ex-" + id, 0, "Exercicio " + id, TrackingType.WEIGHT_REPS,
                LoadBasis.TOTAL, 1, Laterality.BILATERAL, SideMode.COMBINED, 90, null, null, null,
                Arrays.asList(sets));
    }
}
