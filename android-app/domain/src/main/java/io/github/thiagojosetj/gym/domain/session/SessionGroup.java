package io.github.thiagojosetj.gym.domain.session;

/**
 * The group an exercise was in, as the session recorded it (docs/DATABASE.md section 4). Editing or
 * deleting the template's group afterwards must not change what this session says happened, so the
 * badge is the stored one and not a lookup.
 */
public record SessionGroup(
        String id,
        String label,
        String techniqueId,
        String techniqueCode,
        int restAfterRoundSeconds,
        int position) {
}
