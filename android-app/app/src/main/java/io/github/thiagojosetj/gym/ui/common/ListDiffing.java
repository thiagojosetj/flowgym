package io.github.thiagojosetj.gym.ui.common;

import androidx.recyclerview.widget.AsyncDifferConfig;
import androidx.recyclerview.widget.DiffUtil;

import java.util.concurrent.Executor;

/**
 * Builds the config the list adapters use, so the thread that computes the diff is one the app owns
 * instead of a private pool inside the library.
 *
 * <p>Why it matters: {@code ListAdapter.submitList} diffs on a background thread and posts the result
 * back to the main thread. In production that is exactly right. In a test it is a race nothing can
 * see: Espresso checks the screen while the diff is still running elsewhere, so an assertion fails
 * once in a while for no reason the failure message explains. Handing over {@link
 * io.github.thiagojosetj.gym.core.AppExecutors}' executor means tests, which already use a
 * synchronous one, get a list that is on screen before the next line of the test runs.
 */
public final class ListDiffing {

    private ListDiffing() {
    }

    public static <T> AsyncDifferConfig<T> config(DiffUtil.ItemCallback<T> callback, Executor executor) {
        return new AsyncDifferConfig.Builder<>(callback)
                .setBackgroundThreadExecutor(executor)
                .build();
    }
}
