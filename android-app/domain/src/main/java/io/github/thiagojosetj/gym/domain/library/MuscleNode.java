package io.github.thiagojosetj.gym.domain.library;

import java.util.Objects;

/**
 * A node of the muscle hierarchy.
 *
 * @param parentId null for a top-level group ("Costas"); the group id for a subgroup
 */
public record MuscleNode(String id, String code, String name, String parentId, int sortOrder) {

    public MuscleNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
    }

    public boolean isGroup() {
        return parentId == null;
    }
}
