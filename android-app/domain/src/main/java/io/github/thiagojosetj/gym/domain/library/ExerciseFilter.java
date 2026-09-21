package io.github.thiagojosetj.gym.domain.library;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import io.github.thiagojosetj.gym.domain.util.TextNormalizer;

/**
 * Immutable library filter (PRODUCT_SPEC LIB-02/LIB-03). Every field is optional and they combine
 * with AND: e.g. "Costas" + "Latíssimo" + "Polia".
 *
 * @param query            free text as typed by the user (normalized on use)
 * @param muscleGroupId    selected top-level group, or null
 * @param muscleSubgroupId selected subgroup (must belong to the group), or null
 * @param roleScope        which muscle role the muscle filter matches
 * @param equipmentIds     exercises using ANY of these equipment ids; empty = no restriction
 */
public record ExerciseFilter(
        String query,
        String muscleGroupId,
        String muscleSubgroupId,
        MuscleRoleScope roleScope,
        Set<String> equipmentIds) {

    public ExerciseFilter {
        query = query == null ? "" : query;
        roleScope = roleScope == null ? MuscleRoleScope.PRIMARY : roleScope;
        equipmentIds = equipmentIds == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new TreeSet<>(equipmentIds));
        if (muscleSubgroupId != null && muscleGroupId == null) {
            throw new IllegalArgumentException("A subgroup requires its group");
        }
    }

    public static ExerciseFilter none() {
        return new ExerciseFilter("", null, null, MuscleRoleScope.PRIMARY, null);
    }

    /** Accent/case-insensitive form of the query, ready for matching {@code search_text}. */
    public String normalizedQuery() {
        return TextNormalizer.normalize(query);
    }

    /** The most specific muscle selected (subgroup wins over group), or null. */
    public String effectiveMuscleId() {
        return muscleSubgroupId != null ? muscleSubgroupId : muscleGroupId;
    }

    public boolean hasStructuredFilters() {
        return muscleGroupId != null || !equipmentIds.isEmpty();
    }

    public ExerciseFilter withQuery(String newQuery) {
        return new ExerciseFilter(newQuery, muscleGroupId, muscleSubgroupId, roleScope, equipmentIds);
    }

    /** Selecting a different group (or clearing it) always clears the subgroup. */
    public ExerciseFilter withMuscleGroup(String groupId) {
        String subgroup = groupId != null && groupId.equals(muscleGroupId) ? muscleSubgroupId : null;
        return new ExerciseFilter(query, groupId, subgroup, roleScope, equipmentIds);
    }

    public ExerciseFilter withMuscleSubgroup(String subgroupId) {
        return new ExerciseFilter(query, muscleGroupId, subgroupId, roleScope, equipmentIds);
    }

    public ExerciseFilter withRoleScope(MuscleRoleScope scope) {
        return new ExerciseFilter(query, muscleGroupId, muscleSubgroupId, scope, equipmentIds);
    }

    public ExerciseFilter withEquipment(Set<String> ids) {
        return new ExerciseFilter(query, muscleGroupId, muscleSubgroupId, roleScope, ids);
    }

    /** Clears structured filters but keeps the typed query. */
    public ExerciseFilter clearStructuredFilters() {
        return new ExerciseFilter(query, null, null, MuscleRoleScope.PRIMARY, null);
    }
}
