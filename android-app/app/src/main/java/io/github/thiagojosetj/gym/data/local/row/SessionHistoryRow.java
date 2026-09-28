package io.github.thiagojosetj.gym.data.local.row;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * One finished session as the history list needs it (PRODUCT_SPEC HIS-03): the identity, the time
 * and two counts, with no set data at all.
 *
 * <p>The counts are done by SQL because counting rows is not a judgement call. Load volume is not
 * here for the opposite reason: it is a judgement call - per-implement loads, per-side repetitions,
 * body weight and timed sets all change the answer (PRODUCT_SPEC section 9) - and those rules live
 * in {@code SessionVolume}, in one place. Duplicating them in SQL would give the list and the
 * session's own screen two chances to disagree.
 *
 * <p>The pause total is summed from {@code session_pause} rather than read from
 * {@code total_paused_ms}, which is only a cache, for the same reason {@link SessionHeaderRow}
 * does it.
 */
public class SessionHistoryRow {

    @NonNull
    public String id = "";

    @Nullable
    public String templateId;

    @NonNull
    public String name = "";

    public long startedAt;

    @Nullable
    public Long endedAt;

    /** ISO date in the zone the workout was performed in, which is what the calendar groups by. */
    @NonNull
    public String localDate = "";

    /** That zone, so the time of day can be shown as it was lived and not as the phone reads it now. */
    @NonNull
    public String timeZone = "UTC";

    /** The 1-5 rating, or null when the person gave none. Null is not zero. */
    @Nullable
    public Integer rating;

    public long closedPausedMs;

    public int exerciseCount;

    /** Sets confirmed as done; warm-ups included, matching what the finish summary counted. */
    public int performedSetCount;
}
