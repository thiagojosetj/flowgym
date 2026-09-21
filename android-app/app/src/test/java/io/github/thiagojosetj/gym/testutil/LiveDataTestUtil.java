package io.github.thiagojosetj.gym.testutil;

import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import java.util.concurrent.TimeoutException;

/** Reads the current value of a LiveData in a Robolectric test, idling the main looper. */
public final class LiveDataTestUtil {

    private LiveDataTestUtil() {
    }

    public static <T> T getOrAwaitValue(LiveData<T> liveData) throws TimeoutException {
        Object[] holder = new Object[1];
        boolean[] received = new boolean[1];
        Observer<T> observer = value -> {
            holder[0] = value;
            received[0] = true;
        };
        liveData.observeForever(observer);
        try {
            long deadline = System.currentTimeMillis() + 2_000;
            while (!received[0] && System.currentTimeMillis() < deadline) {
                shadowOf(Looper.getMainLooper()).idle();
                if (!received[0]) {
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new TimeoutException("Interrupted");
                    }
                }
            }
            if (!received[0]) {
                throw new TimeoutException("LiveData value was never set");
            }
            @SuppressWarnings("unchecked")
            T value = (T) holder[0];
            return value;
        } finally {
            liveData.removeObserver(observer);
        }
    }
}
