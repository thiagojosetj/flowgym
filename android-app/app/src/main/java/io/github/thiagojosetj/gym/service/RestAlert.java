package io.github.thiagojosetj.gym.service;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import io.github.thiagojosetj.gym.domain.settings.AppSettings;

/**
 * The sound and the vibration when a rest runs out, both optional and both read from the user's
 * settings at the moment they fire (they are editable while training).
 *
 * <p>Deliberately a plain sound instead of a notification alert: the notification is ongoing and
 * silent, and re-alerting it on every update would buzz at the user throughout the workout.
 */
final class RestAlert {

    private static final String TAG = "RestAlert";
    private static final long VIBRATION_MS = 400L;

    private RestAlert() {
    }

    static void fire(Context context, AppSettings settings) {
        if (settings.restSoundEnabled()) {
            playSound(context);
        }
        if (settings.restVibrationEnabled()) {
            vibrate(context);
        }
    }

    private static void playSound(Context context) {
        try {
            Ringtone ringtone = RingtoneManager.getRingtone(context,
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
            if (ringtone == null) {
                return;
            }
            ringtone.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            ringtone.play();
        } catch (RuntimeException e) {
            // A missing or blocked ringtone must not take the workout down with it.
            Log.w(TAG, "Could not play the rest sound", e);
        }
    }

    private static void vibrate(Context context) {
        Vibrator vibrator = vibrator(context);
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        try {
            vibrator.vibrate(VibrationEffect.createOneShot(VIBRATION_MS,
                    VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not vibrate", e);
        }
    }

    private static Vibrator vibrator(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager manager = ContextCompat.getSystemService(context, VibratorManager.class);
            return manager == null ? null : manager.getDefaultVibrator();
        }
        return ContextCompat.getSystemService(context, Vibrator.class);
    }
}
