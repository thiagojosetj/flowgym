package io.github.thiagojosetj.gym.ui.session;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

import java.time.Clock;

/**
 * Emits the current instant once a second, and only while the screen is looking (ARCHITECTURE
 * section 8).
 *
 * <p>This is how the workout clock is drawn without a counter: the value is always "now", and
 * whoever renders it subtracts the persisted timestamps. Nothing accumulates, so nothing drifts, and
 * when the screen comes back after ten minutes the first tick is already correct.
 *
 * <p>It is deliberately NOT part of the list state: an adapter observing a per-second value would
 * resubmit every row once a second.
 */
public final class TickerLiveData extends LiveData<Long> {

    private static final long INTERVAL_MS = 1000L;

    private final Clock clock;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            setValue(clock.millis());
            schedule();
        }
    };

    public TickerLiveData(Clock clock) {
        this.clock = clock;
        setValue(clock.millis());
    }

    @Override
    protected void onActive() {
        setValue(clock.millis());
        schedule();
    }

    @Override
    protected void onInactive() {
        handler.removeCallbacks(tick);
    }

    /** Aligned to the next whole second, so the digits change when the clock does. */
    private void schedule() {
        long delay = INTERVAL_MS - (clock.millis() % INTERVAL_MS);
        handler.postDelayed(tick, delay <= 0 ? INTERVAL_MS : delay);
    }
}
