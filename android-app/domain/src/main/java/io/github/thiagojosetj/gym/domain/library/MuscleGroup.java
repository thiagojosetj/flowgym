package io.github.thiagojosetj.gym.domain.library;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A top-level muscle group with its subgroups, as shown by the library filter chips. */
public record MuscleGroup(MuscleNode group, List<MuscleNode> subgroups) {

    public MuscleGroup {
        subgroups = Collections.unmodifiableList(new ArrayList<>(subgroups));
    }

    /**
     * Builds the two-level hierarchy from a flat list (any order). Groups and subgroups are sorted
     * by {@code sortOrder}. Nodes deeper than two levels, or whose parent is missing, are ignored:
     * the UI only shows group → subgroup today (ADR-0013).
     */
    public static List<MuscleGroup> fromNodes(List<MuscleNode> nodes) {
        List<MuscleNode> sorted = new ArrayList<>(nodes);
        sorted.sort(Comparator.comparingInt(MuscleNode::sortOrder));

        Map<String, List<MuscleNode>> childrenByGroup = new LinkedHashMap<>();
        for (MuscleNode node : sorted) {
            if (node.isGroup()) {
                childrenByGroup.put(node.id(), new ArrayList<>());
            }
        }
        for (MuscleNode node : sorted) {
            if (!node.isGroup() && childrenByGroup.containsKey(node.parentId())) {
                childrenByGroup.get(node.parentId()).add(node);
            }
        }
        List<MuscleGroup> groups = new ArrayList<>(childrenByGroup.size());
        for (MuscleNode node : sorted) {
            if (node.isGroup()) {
                groups.add(new MuscleGroup(node, childrenByGroup.get(node.id())));
            }
        }
        return Collections.unmodifiableList(groups);
    }
}
