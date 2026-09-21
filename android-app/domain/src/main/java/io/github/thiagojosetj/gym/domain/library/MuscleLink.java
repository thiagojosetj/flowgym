package io.github.thiagojosetj.gym.domain.library;

/**
 * A muscle worked by an exercise.
 *
 * @param groupName parent group, or null when the muscle itself is a group
 */
public record MuscleLink(String muscleId, String name, String groupName) {

    /** "Costas · Latíssimo do dorso", or just the name for a group. */
    public String qualifiedName() {
        return groupName == null ? name : groupName + " · " + name;
    }
}
