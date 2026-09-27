package io.github.thiagojosetj.gym.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import io.github.thiagojosetj.gym.R;
import io.github.thiagojosetj.gym.domain.session.SessionHeader;
import io.github.thiagojosetj.gym.ui.MainActivity;

/**
 * The ongoing notification of a workout (ACT-07).
 *
 * <p>The countdown and the workout clock are drawn by the platform's own chronometer: the base is a
 * timestamp, so nothing in this process runs every second and the numbers stay right while the phone
 * sleeps. A paused session shows static text instead - a chronometer that must not move is a lie.
 */
final class WorkoutNotifications {

    static final String CHANNEL_ID = "workout_in_progress";
    static final int NOTIFICATION_ID = 1001;

    private WorkoutNotifications() {
    }

    static void createChannel(Context context) {
        NotificationManager manager = ContextCompat.getSystemService(context, NotificationManager.class);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.session_notification_channel),
                NotificationManager.IMPORTANCE_LOW); // ongoing status, never a buzz per update
        channel.setDescription(context.getString(R.string.session_notification_channel_description));
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    /** A notification with no data yet, for the immediate startForeground call. */
    static Notification placeholder(Context context) {
        return base(context)
                .setContentTitle(context.getString(R.string.session_title))
                .build();
    }

    static Notification of(Context context, SessionHeader session, long now) {
        NotificationCompat.Builder builder = base(context)
                .setContentTitle(session.name())
                .addAction(0, context.getString(R.string.session_notification_open), openApp(context));

        long restRemaining = session.restRemainingMs(now);
        if (session.isPaused()) {
            builder.setContentText(context.getString(R.string.session_paused_label))
                    .addAction(0, context.getString(R.string.session_resume),
                            serviceAction(context, ActiveWorkoutService.ACTION_RESUME));
        } else if (restRemaining > 0) {
            // Counts down to the instant the rest ends, drawn by the platform.
            builder.setContentText(context.getString(R.string.session_rest_title))
                    .setWhen(now + restRemaining)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .addAction(0, context.getString(R.string.session_pause),
                            serviceAction(context, ActiveWorkoutService.ACTION_PAUSE));
        } else {
            // Base in the past by the effective time, so the chronometer shows time spent training.
            builder.setContentText(context.getString(R.string.session_elapsed_label))
                    .setWhen(now - session.clock().effectiveMs(now))
                    .setUsesChronometer(true)
                    .addAction(0, context.getString(R.string.session_pause),
                            serviceAction(context, ActiveWorkoutService.ACTION_PAUSE));
        }
        return builder.build();
    }

    private static NotificationCompat.Builder base(Context context) {
        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_fitness)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_WORKOUT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(openApp(context));
    }

    private static PendingIntent openApp(Context context) {
        Intent intent = new Intent(context, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Nullable
    private static PendingIntent serviceAction(Context context, String action) {
        Intent intent = new Intent(context, ActiveWorkoutService.class).setAction(action);
        return PendingIntent.getService(context, action.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
