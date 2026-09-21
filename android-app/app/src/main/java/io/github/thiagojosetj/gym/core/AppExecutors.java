package io.github.thiagojosetj.gym.core;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Threads used by the app (ARCHITECTURE §5.2).
 *
 * <p>{@link #diskIO()} is a single thread: every database write runs there, in submission order.
 * That removes a whole class of race conditions (e.g. a double tap on "save") and guarantees that
 * start-up work (creating the local user, seeding the catalog) finishes before any later write.
 * Observable reads do not use it: Room runs LiveData queries on its own executor.
 */
public final class AppExecutors {

    private final Executor diskIO;
    private final Executor mainThread;

    public AppExecutors(Executor diskIO, Executor mainThread) {
        this.diskIO = diskIO;
        this.mainThread = mainThread;
    }

    /** Production executors: one named background thread for disk + the Android main thread. */
    public static AppExecutors create() {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        return new AppExecutors(
                Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "disk-io")),
                mainHandler::post);
    }

    public Executor diskIO() {
        return diskIO;
    }

    public Executor mainThread() {
        return mainThread;
    }

    /**
     * Runs {@code work} on the disk thread and delivers the outcome on the main thread.
     * Exactly one of the callbacks is called.
     */
    public <T> void runOnDisk(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        diskIO.execute(() -> {
            try {
                T result = work.call();
                mainThread.execute(() -> onSuccess.accept(result));
            } catch (Exception e) {
                mainThread.execute(() -> onError.accept(e));
            }
        });
    }
}
