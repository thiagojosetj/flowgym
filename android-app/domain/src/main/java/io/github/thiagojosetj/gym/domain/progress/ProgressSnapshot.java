package io.github.thiagojosetj.gym.domain.progress;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One read of the progress screen: what the shown period came to, and how far the training goes
 * in either direction (PRODUCT_SPEC PRG-04).
 *
 * <p>The two travel together on purpose. The arrows that move between periods are bounded by the
 * first and last day there is anything to see, and an arrow enabled by one read while the numbers
 * beside it came from another is the kind of disagreement this project has already paid for
 * (ARCHITECTURE section 5.2). One read, one answer.
 *
 * @param sessions        the period's sessions one by one, OLDEST FIRST, for the chart of
 *                        PRG-01. The same read as the totals above them, so the chart and the
 *                        figures can never disagree
 * @param firstTrainedDay the oldest finished session's day, or null when nothing was ever finished
 * @param lastTrainedDay  the newest one's. Bounds the arrows by the DATA rather than by today: a
 *                        phone whose clock ran ahead wrote a day in the future, and stopping at
 *                        today would hide that session instead of showing where it was recorded
 */
public record ProgressSnapshot(PeriodStatistics statistics, List<PeriodSession> sessions,
                               LocalDate firstTrainedDay, LocalDate lastTrainedDay) {

    public static final ProgressSnapshot EMPTY =
            new ProgressSnapshot(PeriodStatistics.EMPTY, Collections.emptyList(), null, null);

    public ProgressSnapshot {
        sessions = sessions == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(sessions));
    }

    /** True when nothing was ever finished - not merely "nothing in this period". */
    public boolean hasNoHistory() {
        return firstTrainedDay == null;
    }
}
