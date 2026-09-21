package io.github.thiagojosetj.gym.data.local.entity;

/**
 * Sync state of an aggregate root (docs/SYNC.md §4). Persisted by name.
 * Everything starts {@link #PENDING}; nothing is uploaded until accounts exist (Phase 9).
 */
public enum SyncStatus {
    /** Local change not yet accepted by the server. */
    PENDING,
    /** Local copy equals the server version in {@code server_version}. */
    SYNCED
}
