package io.github.thiagojosetj.gym.domain.library;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;

import org.junit.Test;

public class ExerciseFilterTest {

    @Test
    public void defaultsToNoRestrictionsAndPrimaryRole() {
        ExerciseFilter filter = ExerciseFilter.none();
        assertEquals("", filter.normalizedQuery());
        assertNull(filter.effectiveMuscleId());
        assertEquals(MuscleRoleScope.PRIMARY, filter.roleScope());
        assertFalse(filter.hasStructuredFilters());
    }

    @Test
    public void subgroupIsMoreSpecificThanGroup() {
        ExerciseFilter filter = ExerciseFilter.none().withMuscleGroup("back").withMuscleSubgroup("back.lats");
        assertEquals("back.lats", filter.effectiveMuscleId());
    }

    @Test
    public void changingGroupClearsSubgroup() {
        ExerciseFilter filter = ExerciseFilter.none().withMuscleGroup("back").withMuscleSubgroup("back.lats");
        assertEquals("back.lats", filter.withMuscleGroup("back").muscleSubgroupId());
        assertNull(filter.withMuscleGroup("chest").muscleSubgroupId());
        assertNull(filter.withMuscleGroup(null).muscleSubgroupId());
    }

    @Test
    public void subgroupWithoutGroupIsInvalid() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExerciseFilter("", null, "back.lats", MuscleRoleScope.ANY, null));
    }

    @Test
    public void equipmentSetIsCopiedAndComparedByContent() {
        HashSet<String> source = new HashSet<>(Arrays.asList("cable", "machine"));
        ExerciseFilter a = ExerciseFilter.none().withEquipment(source);
        source.add("barbell");
        ExerciseFilter b = ExerciseFilter.none().withEquipment(new HashSet<>(Arrays.asList("machine", "cable")));
        assertEquals(a, b);
        assertTrue(a.hasStructuredFilters());
    }

    @Test
    public void queryIsNormalizedForMatching() {
        assertEquals("elevacao", ExerciseFilter.none().withQuery("  ELEVAÇÃO ").normalizedQuery());
    }

    @Test
    public void clearStructuredFiltersKeepsQuery() {
        ExerciseFilter filter = ExerciseFilter.none().withQuery("remada").withMuscleGroup("back")
                .withRoleScope(MuscleRoleScope.ANY).clearStructuredFilters();
        assertEquals("remada", filter.query());
        assertFalse(filter.hasStructuredFilters());
        assertEquals(MuscleRoleScope.PRIMARY, filter.roleScope());
    }
}
