package io.github.thiagojosetj.gym.data.local.row;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.thiagojosetj.gym.domain.session.SessionStatus;

/**
 * The session without its sets: status, time and rest state, with the pause arithmetic already done
 * by SQL.
 *
 * <p>The pause aggregates are computed here rather than read from {@code total_paused_ms}, which is
 * only a cache: a crash between pausing and resuming would leave it stale, and the screen must never
 * show a stale duration. Nothing that reads this row can forget to load the pauses either - which was
 * a real bug: a header built with an empty pause list always claimed the session was running.
 */
public class SessionHeaderRow {

    @NonNull
    public String id = "";

    @Nullable
    public String templateId;

    @NonNull
    public String name = "";

    @Nullable
    public String notes;

    @NonNull
    public SessionStatus status = SessionStatus.ACTIVE;

    public long startedAt;

    @Nullable
    public Long endedAt;

    /** Sum of the pauses that already ended. */
    public long closedPausedMs;

    /** Start of the pause in progress, or null when the session is running. */
    @Nullable
    public Long openPauseStartedAt;

    @Nullable
    public String restSetLogId;

    @Nullable
    public Long restEndsAt;

    @Nullable
    public Long restRemainingMsWhenPaused;
}
