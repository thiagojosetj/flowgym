package io.github.thiagojosetj.gym.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import io.github.thiagojosetj.gym.AppContainer;
import io.github.thiagojosetj.gym.GymApplication;
import io.github.thiagojosetj.gym.data.repository.ActiveSessionRepository;
import io.github.thiagojosetj.gym.data.repository.SettingsRepository;
import io.github.thiagojosetj.gym.domain.session.SessionHeader;

/**
 * Keeps the workout visible and its rest alert alive while the app is in the background (ACT-07).
 *
 * <p>It is a <em>projection</em>, never a precondition: the session lives in the database, the
 * service only renders it. If it cannot start, or notifications are denied, the workout still logs
 * every set - which is why the start is wrapped in a try/catch and why nothing here holds state.
 *
 * <p>WorkManager would be the wrong tool: this is not deferrable work that may run later, it is a
 * user-visible activity happening right now that must survive the app going to the background.
 * A rest of 90 s also cannot wait for WorkManager's 15-minute minimum, and exact alarms are
 * restricted to alarm-clock and calendar apps.
 */
public class ActiveWorkoutService extends Service {

    public static final String ACTION_PAUSE = "io.github.thiagojosetj.gym.action.PAUSE";
    public static final String ACTION_RESUME = "io.github.thiagojosetj.gym.action.RESUME";

    private static final String TAG = "ActiveWorkoutService";

    private ActiveSessionRepository sessions;
    private SettingsRepository settings;
    private LiveData<SessionHeader> header;
    private Observer<SessionHeader> observer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable
    private Runnable pendingAlert;
    /** Set whose rest alert already fired, so a re-render cannot fire it twice. */
    @Nullable
    private String alertedSetLogId;
    private boolean foregroundStarted;

    /**
     * Starts (or refreshes) the service. Call only from the foreground: Android forbids starting a
     * foreground service from the background, and a workout always starts with the user looking.
     */
    public static void start(Context context) {
        Intent intent = new Intent(context.getApplicationContext(), ActiveWorkoutService.class);
        try {
            ContextCompat.startForegroundService(context.getApplicationContext(), intent);
        } catch (RuntimeException e) {
            // e.g. ForegroundServiceStartNotAllowedException: the session keeps working regardless.
            Log.w(TAG, "Could not start the workout service", e);
        }
    }

    public static void stop(Context context) {
        try {
            context.getApplicationContext().stopService(
                    new Intent(context.getApplicationContext(), ActiveWorkoutService.class));
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not stop the workout service", e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AppContainer app = ((GymApplication) getApplication()).container();
        sessions = app.activeSessions;
        settings = app.settings;
        WorkoutNotifications.createChannel(this);
        header = sessions.observeActiveHeader();
        observer = this::render;
        header.observeForever(observer);
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        // Must promote immediately, before any database read: the platform gives a few seconds.
        promote(WorkoutNotifications.placeholder(this));

        String action = intent == null ? null : intent.getAction();
        if (ACTION_PAUSE.equals(action) || ACTION_RESUME.equals(action)) {
            SessionHeader current = header.getValue();
            if (current != null) {
                if (ACTION_PAUSE.equals(action)) {
                    sessions.pause(current.id(), () -> {
                    }, error -> Log.w(TAG, "Pause from the notification failed", error));
                } else {
                    sessions.resume(current.id(), () -> {
                    }, error -> Log.w(TAG, "Resume from the notification failed", error));
                }
            }
        }
        // START_STICKY plus the null-intent branch below: after being killed the service comes back
        // and asks the database whether there is still a workout.
        render(header.getValue());
        return START_STICKY;
    }

    /** Draws the current state, or stops when there is no workout to show. */
    private void render(@Nullable SessionHeader session) {
        if (session == null) {
            stopForegroundAndSelf();
            return;
        }
        promote(WorkoutNotifications.of(this, session, now()));
        scheduleRestAlert(session);
    }

    /**
     * One delayed alert, derived from the stored end instant. Re-derived on every render, so a
     * pause, an adjustment or a skip simply reschedules it.
     */
    private void scheduleRestAlert(SessionHeader session) {
        if (pendingAlert != null) {
            handler.removeCallbacks(pendingAlert);
            pendingAlert = null;
        }
        String restSetId = session.restSetLogId();
        if (restSetId == null) {
            alertedSetLogId = null; // the rest was cleared: the next one may alert again
            return;
        }
        if (restSetId.equals(alertedSetLogId) || session.isPaused()) {
            return;
        }
        long remaining = session.restRemainingMs(now());
        pendingAlert = () -> {
            pendingAlert = null;
            alertedSetLogId = restSetId;
            settings.loadSettings(values -> RestAlert.fire(this, values),
                    error -> Log.w(TAG, "Could not read the alert settings", error));
        };
        handler.postDelayed(pendingAlert, Math.max(0L, remaining));
    }

    private void promote(android.app.Notification notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(this, WorkoutNotifications.NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH);
            } else {
                ServiceCompat.startForeground(this, WorkoutNotifications.NOTIFICATION_ID, notification, 0);
            }
            foregroundStarted = true;
        } catch (RuntimeException e) {
            // A denied POST_NOTIFICATIONS or a type prerequisite the device refuses: log and carry on.
            Log.w(TAG, "Could not show the workout notification", e);
        }
    }

    private void stopForegroundAndSelf() {
        if (foregroundStarted) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
            foregroundStarted = false;
        }
        stopSelf();
    }

    private long now() {
        return ((GymApplication) getApplication()).container().clock.millis();
    }

    @Override
    public void onDestroy() {
        if (pendingAlert != null) {
            handler.removeCallbacks(pendingAlert);
            pendingAlert = null;
        }
        if (header != null && observer != null) {
            header.removeObserver(observer);
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // started service only
    }
}
